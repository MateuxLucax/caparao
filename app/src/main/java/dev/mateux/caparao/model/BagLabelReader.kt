package dev.mateux.caparao.model

import java.text.Normalizer
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class LabelLine(val text: String, val height: Float)

data class BagLabelReading(
    val weightGrams: Double? = null,
    val roastedAt: Long? = null,
    val tastingNotes: List<String> = emptyList(),
    val roaster: String? = null,
    val candidates: List<String> = emptyList(),
)

/**
 * Reads only label details supported by the supplied text and known values. Fixtures from real Brazilian bags live in
 * `BagLabelFixturesTest` (#555); the rules below follow what those labels print.
 */
object BagLabelReader {
    fun read(
        lines: List<LabelLine>,
        knownRoasters: List<String>,
        knownNotes: List<String>,
        now: LocalDate = LocalDate.now(ZoneOffset.UTC),
    ): BagLabelReading {
        val roastedAt = lines.indices.firstNotNullOfOrNull { roastDate(lines, it, now) }?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val weight = lines.mapNotNull { line -> lineWeight(line.text) }.maxOrNull()
        val (roaster, roasterLines) = roaster(lines, knownRoasters)
        val notePatterns = knownNotes.filter { it.isNotBlank() }.map { it to notePattern(it) }
        val notes = linkedSetOf<String>()
        lines.forEach { line ->
            val normalizedLine = normalize(line.text)
            notePatterns.mapNotNull { (note, pattern) -> pattern.find(normalizedLine)?.let { it.range.first to note } }
                .sortedBy { it.first }.forEach { notes += it.second }
        }
        val candidates = lines.withIndex().asSequence().mapNotNull { (index, line) ->
            if (index in roasterLines || roaster != null && compact(line.text).contains(compact(roaster))) return@mapNotNull null
            val text = candidateText(line.text) ?: return@mapNotNull null
            if (notePatterns.any { (_, pattern) -> pattern.containsMatchIn(normalize(text)) }) null else line.copy(text = text)
        }.sortedByDescending { it.height }.map { titleCase(it.text) }.distinctBy(::normalize).take(8).toList()
        return BagLabelReading(weight, roastedAt, notes.take(6), roaster, candidates)
    }

    /**
     * The date on line [index] when it is the roast date: the line names it ("Torrado em", "Data de torra", "Fab:",
     * "F:"), or the line above is only that name. "Fab"/"F" is the packing date that Brazilian roasters print as the
     * roast date. Validity ("Val:", "V:", "Validade"), dates in the future and dates nobody named are skipped.
     */
    private fun roastDate(lines: List<LabelLine>, index: Int, now: LocalDate): LocalDate? {
        val date = DATE.findAll(lines[index].text).mapNotNull { parseDate(it.value) }.firstOrNull { !it.isAfter(now) } ?: return null
        val own = normalize(lines[index].text)
        if (INVALID_DATE.containsMatchIn(own)) return null
        if (ROAST_DATE.containsMatchIn(own)) return date
        val above = lines.getOrNull(index - 1)?.text ?: return null
        return date.takeIf { isDateLabel(above, ROAST_DATE_LABEL) }
    }

    /**
     * The weight a line states, or null. Size choices printed on every bag ("○ 250g ○ 1kg", which ML Kit reads as
     * "o 250g o lkg", sometimes one size per line) and company IDs or phone numbers ending in a misread "g" are skipped.
     */
    private fun lineWeight(raw: String): Double? {
        val text = OCR_ONE_KILO.replace(raw.trim(), "1")
        val normalized = normalize(text)
        if (SIZE_OPTION.containsMatchIn(text) || CONTACT.containsMatchIn(normalized) || ADDRESS.containsMatchIn(normalized)) return null
        return weights(text).distinct().singleOrNull()
    }

    private fun weights(text: String): List<Double> = WEIGHT.findAll(text).mapNotNull { match ->
        val number = match.groupValues[1].let { if (THOUSANDS.matches(it)) it.replace(".", "") else it.replace(',', '.') }
        val kilos = match.groupValues[2].lowercase(Locale.ROOT).first() in "kq"
        number.toDoubleOrNull()?.let { if (kilos) it * 1000 else it }
    }.toList()

    /** The first known roaster named on one line, or across two lines when a logo breaks ("moka" / "clube"), or in an address. */
    private fun roaster(lines: List<LabelLine>, knownRoasters: List<String>): Pair<String?, Set<Int>> {
        val known = knownRoasters.filter { compact(it).length >= 3 }
        lines.indices.forEach { index ->
            listOf(setOf(index), setOf(index, index + 1)).filter { span -> span.all { it < lines.size } }.forEach { span ->
                val text = compact(span.sorted().joinToString(" ") { lines[it].text })
                known.firstOrNull { text.contains(compact(it)) }?.let { return titleCase(it) to span }
            }
        }
        return null to emptySet()
    }

    /** The line as a candidate for the user to assign, or null for dates, lot codes, sizes, field names and contact details. */
    private fun candidateText(raw: String): String? {
        val text = raw.trim()
        val normalized = normalize(text)
        if (text.isEmpty() || DATE.containsMatchIn(text) || ONLY_NUMBERS.matches(text) || DIGIT_RUN.containsMatchIn(text)) return null
        if (ADDRESS.containsMatchIn(normalized) || CONTACT.containsMatchIn(normalized) || LOT.containsMatchIn(normalized)) return null
        if (isDateLabel(text, ROAST_DATE_LABEL) || isDateLabel(text, INVALID_DATE_LABEL) || isFieldLabel(text)) return null
        if (isProse(text) || !text.first().isLetterOrDigit() || CODE.containsMatchIn(text)) return null
        if (!WEIGHT.containsMatchIn(text)) return text
        // "CAFE FAZENDA JATOBA 250G": keep the name in front of a trailing weight.
        return TRAILING_WEIGHT.matchEntire(text)?.groupValues?.get(1)?.trim()?.takeIf { it.split(Regex("\\s+")).size >= 2 && !FIELD_LABEL.matches(normalize(it)) }
    }

    /**
     * A field name printed on every bag, also as ML Kit misreads it ("Variedad", "Perhlsensorial", "Proceso"). Short
     * names must match exactly, so a variety such as "Arara" next to "Aroma" stays a candidate.
     */
    private fun isFieldLabel(text: String): Boolean {
        val normalized = normalize(text).trimEnd(':', ' ', '.')
        if (FIELD_LABEL.matches(normalized)) return true
        val letters = normalized.replace(Regex("[^\\p{L}]"), "")
        val allowed = when { letters.length >= 12 -> 2; letters.length >= 6 -> 1; else -> return false }
        return FIELD_LABELS.any { label ->
            // A label cut short by the photo ("perfl sens") is compared with the same length of the label.
            (listOf(label.length) + (letters.length - 1..letters.length + 1)).filter { it in 1..label.length }
                .any { editDistance(letters, label.take(it)) <= allowed }
        }
    }

    /** Body copy, not a name: long lines, sentence ends, broken company names and lines cut at a joining word. */
    private fun isProse(text: String): Boolean {
        val words = normalize(text).split(Regex("[\\s]+")).filter { it.isNotEmpty() }
        return words.size > 4 || text.last() in ".,;:" || words.first() in JOINING_WORDS || words.last() in JOINING_WORDS ||
            "ltda" in words
    }

    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        a.forEachIndexed { i, ca ->
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            b.forEachIndexed { j, cb -> current[j + 1] = minOf(previous[j + 1] + 1, current[j] + 1, previous[j] + if (ca == cb) 0 else 1) }
            previous = current
        }
        return previous[b.length]
    }

    /** Matches a note and its plural or gender forms: "Cítrico" finds "notas cítricas", "Caramelo" finds "caramelo". */
    private fun notePattern(note: String): Regex {
        val words = normalize(note).trim().split(Regex("\\s+")).joinToString("\\s+") { word ->
            val stem = Regex("(os|as|o|a|s)$").replace(word, "")
            Regex.escape(stem.ifEmpty { word }) + if (stem.length < word.length) "(?:os|as|o|a|s|es)?" else "(?:s|es)?"
        }
        return Regex("(?<![\\p{L}\\p{N}])$words(?![\\p{L}\\p{N}])")
    }

    /** Keeps the first value, while accumulating unique notes and candidate lines from later photos. */
    fun merge(existing: BagLabelReading, next: BagLabelReading): BagLabelReading = BagLabelReading(
        weightGrams = existing.weightGrams ?: next.weightGrams,
        roastedAt = existing.roastedAt ?: next.roastedAt,
        tastingNotes = (existing.tastingNotes + next.tastingNotes).distinctBy(::normalize).take(6),
        roaster = existing.roaster ?: next.roaster,
        candidates = (existing.candidates + next.candidates).distinctBy(::normalize),
    )

    private fun parseDate(value: String): LocalDate? = DATE_FORMATS.firstNotNullOfOrNull { format ->
        try { LocalDate.parse(value, format) } catch (_: DateTimeParseException) { null }
    }

    private fun titleCase(value: String): String = value.lowercase(Locale.ROOT).replace(Regex("(^|[^\\p{L}])(\\p{L})")) { it.groupValues[1] + it.groupValues[2].uppercase(Locale.ROOT) }

    private fun normalize(value: String) = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")

    /** Lowercase letters, digits and "+" only, so "Moka Clube", "@mokaclube" and "moka / clube" compare equal. */
    private fun compact(value: String) = normalize(value).replace(Regex("[^\\p{L}\\p{N}+]"), "")

    private fun isDateLabel(text: String, label: Regex) = label.matches(normalize(text.trim()))

    private const val ROAST_WORDS = "torra|torrado em|data de torra|roast|roasted on|roasted|fab|fabricacao|fabricado em"
    private const val INVALID_WORDS = "validade|val|vence|vencimento|best before|expiry"
    private val ROAST_DATE = Regex("\\b($ROAST_WORDS)\\b|(^|[^\\p{L}])f\\s*[:.]")
    private val INVALID_DATE = Regex("\\b($INVALID_WORDS)\\b|(^|[^\\p{L}])v\\s*[:.]")
    private val ROAST_DATE_LABEL = Regex("($ROAST_WORDS|f)[: .]*")
    private val INVALID_DATE_LABEL = Regex("($INVALID_WORDS|v)[: .]*")
    private val DATE = Regex("\\b(?:\\d{1,2}[/.\\-]\\d{1,2}[/.\\-](?:\\d{4}|\\d{2})|\\d{4}-\\d{1,2}-\\d{1,2})\\b")
    private val DATE_FORMATS = listOf("d/M/uuuu", "d.M.uuuu", "d-M-uuuu", "d/M/uu", "d.M.uu", "d-M-uu").map(DateTimeFormatter::ofPattern) +
        DateTimeFormatter.ISO_LOCAL_DATE
    private const val UNIT = "kg|quilos?|g|gr|gramas?|gramos?"
    private val WEIGHT = Regex("(?i)(?<![\\d.,])(\\d+(?:[.,]\\d+)?)\\s*($UNIT)(?![\\p{L}\\p{N}])")
    private val TRAILING_WEIGHT = Regex("(?i)(.*\\p{L}.*?)[\\s\\-–|]+\\d+(?:[.,]\\d+)?\\s*(?:$UNIT)\\.?")
    private val THOUSANDS = Regex("\\d{1,3}\\.\\d{3}")
    private val ONLY_NUMBERS = Regex("[\\d\\s.,/-]+")
    private val DIGIT_RUN = Regex("\\d{8,}")
    /** A barcode or batch code with letters in it: "89R682Z1". */
    private val CODE = Regex("(?<!\\S)(?=[^\\s]*\\p{L})(?=(?:[^\\s\\d]*\\d){4})[^\\s]+")
    private val ADDRESS = Regex("\\b(rua|avenida|av\\.?|street|road|cep|zip)\\b|^r\\.\\s")
    private val CONTACT = Regex("@|\\bwww\\.|https?:|\\.com\\b|\\b(cnpj|sac|tel|telefone|whatsapp)\\b")
    private val LOT = Regex("^(lote|lt)\\b.*\\d|^l\\s*[:.]")
    private val FIELD_LABEL = Regex(
        "variedade|variety|perfil sensorial|processo|process|produtor|producer|regiao|region|origem|origin|altitude|" +
            "colheita|fragrancia|aroma|acidez|corpo|notas|notas sensoriais|tasting notes|torra|torrado por|" +
            "produzido e embalado por|siga|lote|peso liquido|peso liq|peso",
    )
    private val FIELD_LABELS = FIELD_LABEL.pattern.split('|').map { it.replace(" ", "") }.filter { it.length >= 6 }
    private val JOINING_WORDS = setOf("a", "o", "e", "de", "do", "da", "dos", "das", "com", "para", "no", "na", "em", "um", "uma", "ao", "of", "the", "and")
    /** A tick box in front of a size: "○ 250g", "o 250g", "• 1kg". */
    private val SIZE_OPTION = Regex("(?i)(^|\\s)[o○●•◯◦□☐]\\s*\\d+(?:[.,]\\d+)?\\s*(?:$UNIT)(?![\\p{L}\\p{N}])")
    /** ML Kit reads the "1" of "1kg" as "l" or "I". */
    private val OCR_ONE_KILO = Regex("(?<![\\p{L}\\p{N}])[lI](?=\\s*kg(?![\\p{L}]))")
}
