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

/** Reads only label details supported by the supplied text and known values. */
object BagLabelReader {
    fun read(
        lines: List<LabelLine>,
        knownRoasters: List<String>,
        knownNotes: List<String>,
        now: LocalDate = LocalDate.now(ZoneOffset.UTC),
    ): BagLabelReading {
        val datedLines = lines.mapIndexed { index, line ->
            val nearby = listOfNotNull(lines.getOrNull(index - 1)?.text, line.text, lines.getOrNull(index + 1)?.text).joinToString(" ")
            val invalid = INVALID_DATE.containsMatchIn(normalize(nearby))
            val roast = ROAST_DATE.containsMatchIn(normalize(nearby))
            Triple(line, if (invalid) false else roast, DATE.findAll(line.text).mapNotNull { parseDate(it.value) }.filter { !it.isAfter(now) }.firstOrNull())
        }
        val roastedAt = datedLines.firstNotNullOfOrNull { (_, tagged, date) -> date?.takeIf { tagged }?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli() }
        val weight = lines.flatMap { WEIGHT.findAll(it.text).map { match -> match.groupValues[1].replace(',', '.').toDoubleOrNull()?.let { amount -> if (match.groupValues[2].startsWith("k", true)) amount * 1000 else amount } } }
            .filterNotNull().maxOrNull()
        val roaster = lines.asSequence().flatMap { line -> knownRoasters.asSequence().filter { known -> known.isNotBlank() && normalize(line.text).contains(normalize(known)) }.map { line to it } }
            .map { it.second }.firstOrNull()?.let(::titleCase)
        val notes = linkedSetOf<String>()
        lines.forEach { line ->
            val normalizedLine = normalize(line.text)
            knownNotes.mapNotNull { note ->
                if (note.isBlank()) null else Regex("(?<![\\p{L}\\p{N}])${Regex.escape(normalize(note))}(?![\\p{L}\\p{N}])")
                    .find(normalizedLine)?.let { it.range.first to note }
            }.sortedBy { it.first }.forEach { notes += it.second }
        }
        val roastFieldLines = lines.indices.filter { index ->
            val nearby = listOfNotNull(lines.getOrNull(index - 1)?.text, lines[index].text, lines.getOrNull(index + 1)?.text).joinToString(" ")
            ROAST_DATE.containsMatchIn(normalize(nearby)) && DATE.containsMatchIn(nearby)
        }.toSet()
        val invalidDateLines = lines.indices.filter { index ->
            val nearby = listOfNotNull(lines.getOrNull(index - 1)?.text, lines[index].text, lines.getOrNull(index + 1)?.text).joinToString(" ")
            INVALID_DATE.containsMatchIn(normalize(nearby)) && DATE.containsMatchIn(nearby)
        }.toSet()
        val candidates = lines.withIndex().asSequence().filter { (index, line) ->
            val text = line.text.trim()
            index !in roastFieldLines && index !in invalidDateLines &&
                !WEIGHT.containsMatchIn(text) && !ONLY_NUMBERS.matches(text) &&
                !ADDRESS.containsMatchIn(text) && !BARCODE.matches(text) && !isKnownRoaster(text, knownRoasters) && !matchesNote(text, knownNotes)
        }.map { it.value }.sortedByDescending { it.height }.map { titleCase(it.text.trim()) }.distinctBy(::normalize).take(8).toList()
        return BagLabelReading(weight, roastedAt, notes.take(6), roaster, candidates)
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

    private fun isKnownRoaster(text: String, roasters: List<String>) = roasters.any { it.isNotBlank() && normalize(text).contains(normalize(it)) }

    private fun matchesNote(text: String, knownNotes: List<String>) = knownNotes.any { note ->
        note.isNotBlank() && Regex("(?<![\\p{L}\\p{N}])${Regex.escape(normalize(note))}(?![\\p{L}\\p{N}])").containsMatchIn(normalize(text))
    }

    private fun normalize(value: String) = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")

    private val ROAST_DATE = Regex("\\b(torra|torrado em|roast|roasted on)\\b")
    private val INVALID_DATE = Regex("\\b(validade|val\\.?|best before|fabricacao)\\b")
    private val DATE = Regex("\\b(?:\\d{1,2}[/.\\-]\\d{1,2}[/.\\-]\\d{4}|\\d{4}-\\d{1,2}-\\d{1,2})\\b")
    private val DATE_FORMATS = listOf(DateTimeFormatter.ofPattern("d/M/uuuu"), DateTimeFormatter.ofPattern("d.M.uuuu"), DateTimeFormatter.ofPattern("d-M-uuuu"), DateTimeFormatter.ISO_LOCAL_DATE)
    private val WEIGHT = Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*(kg|g)\\b")
    private val ONLY_NUMBERS = Regex("[\\d\\s.,/-]+")
    private val BARCODE = Regex("\\d{8,}")
    private val ADDRESS = Regex("(?i)\\b(rua|avenida|av\\.?|street|road|cep|zip)\\b")
}
