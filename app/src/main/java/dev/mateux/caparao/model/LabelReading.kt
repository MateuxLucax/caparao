package dev.mateux.caparao.model

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

/** One recognized line; height is used only to order candidates. */
data class LabelLine(val text: String, val height: Int)

data class LabelReading(
    val weightGrams: Int? = null,
    val roastDate: LocalDate? = null,
    val roaster: String? = null,
    val tastingNotes: List<String> = emptyList(),
    val candidates: List<String> = emptyList(),
)

private fun key(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()

private fun title(text: String): String = text.lowercase(Locale.ROOT)
    .replace(Regex("(^|[^\\p{L}])(\\p{L})")) { it.groupValues[1] + it.groupValues[2].uppercase(Locale.ROOT) }

/** Extracts only fields supported by explicit label text or previously known values. */
fun readLabel(lines: List<LabelLine>, roasters: List<String>, notes: List<String>, today: LocalDate): LabelReading {
    val weight = Regex("(?<![\\d.,])(\\d+(?:[.,]\\d+)?)\\s*(kg|g)\\b", RegexOption.IGNORE_CASE)
    val date = Regex("\\b(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})\\b")
    val roast = Regex("\\b(torra|torrado em|torrada em|roast|roasted on)\\b")
    val excluded = Regex("\\b(validade|val\\.?|best before|fabricacao)\\b")
    val weights = lines.flatMap { line -> weight.findAll(line.text).mapNotNull { match ->
        val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
        val grams = number * if (match.groupValues[2].equals("kg", true)) 1000 else 1
        grams.takeIf { it > 0 && it <= Int.MAX_VALUE && it % 1.0 == 0.0 }?.toInt()
    }.toList() }
    val roastDate = lines.indices.firstNotNullOfOrNull { i ->
        val context = key(lines[i].text)
        if (excluded.containsMatchIn(context)) return@firstNotNullOfOrNull null
        val source = if (roast.containsMatchIn(context)) lines[i].text else if (i > 0 && roast.containsMatchIn(key(lines[i - 1].text)) && !excluded.containsMatchIn(key(lines[i - 1].text))) lines[i].text else return@firstNotNullOfOrNull null
        date.findAll(source).firstNotNullOfOrNull { m ->
            runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()?.takeUnless { it > today }
        }
    }
    val foundRoaster = lines.firstOrNull { line -> roasters.any { key(it) == key(line.text) && key(it).isNotEmpty() } }?.text?.trim()?.let(::title)
    val foundNotes = mutableListOf<String>()
    for (line in lines) {
        val normalized = key(line.text)
        val matches = notes.mapNotNull { note ->
            val n = key(note)
            if (n.isEmpty()) null else Regex("(?<![\\p{L}\\p{N}])${Regex.escape(n)}(?![\\p{L}\\p{N}])").find(normalized)?.let { it.range.first to note }
        }.sortedBy { it.first }
        for ((_, note) in matches) if (foundNotes.none { key(it) == key(note) } && foundNotes.size < 6) foundNotes.add(note)
    }
    val candidates = lines.withIndex().filter { (_, line) ->
        val s = line.text.trim()
        val k = key(s)
        s.any { it.isLetter() } && !weight.matches(s) && !excluded.containsMatchIn(k) &&
            !roast.containsMatchIn(k) && !Regex("\\b(rua|avenida|av\\.|street|road|cep|cnpj|www|http)\\b").containsMatchIn(k) &&
            roasters.none { key(it) == k } && notes.none { key(it) == k }
    }.sortedWith(compareByDescending<IndexedValue<LabelLine>> { it.value.height }.thenBy { it.index })
        .map { title(it.value.text.trim()) }.distinctBy(::key).take(8)
    return LabelReading(weights.maxOrNull(), roastDate, foundRoaster, foundNotes, candidates)
}

/** Keeps values already entered and appends new notes and candidates without duplicates. */
fun mergeLabelReadings(current: LabelReading, incoming: LabelReading): LabelReading = LabelReading(
    weightGrams = current.weightGrams ?: incoming.weightGrams,
    roastDate = current.roastDate ?: incoming.roastDate,
    roaster = current.roaster?.takeIf { it.isNotBlank() } ?: incoming.roaster,
    tastingNotes = (current.tastingNotes + incoming.tastingNotes).distinctBy(::key).take(6),
    candidates = (current.candidates + incoming.candidates).distinctBy(::key),
)
