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

private fun title(text: String): String {
    return text.lowercase(Locale.ROOT)
        .replace(Regex("(^|[^\\p{L}])(\\p{L})")) { it.groupValues[1] + it.groupValues[2].uppercase(Locale.ROOT) }
}

private fun roasterTitle(text: String): String = title(text)
    .replace(Regex("(?i)(?<=\\s)(da|das|de|do|dos|e|and|of|the)(?=\\s)")) { it.value.lowercase(Locale.ROOT) }

private val labelDate = Regex("(?<!\\d)(\\d{1,4})[/.-](\\d{1,2})[/.-](\\d{1,4})(?!\\d)")

/** Parses common label dates. Ambiguous slash dates use day/month, as on Brazilian labels. */
private fun parseLabelDate(match: MatchResult): LocalDate? {
    val first = match.groupValues[1].toIntOrNull() ?: return null
    val second = match.groupValues[2].toIntOrNull() ?: return null
    val third = match.groupValues[3].toIntOrNull() ?: return null
    if (match.groupValues[1].length != 4 && match.groupValues[3].length != 4) return null
    val (year, month, day) = if (match.groupValues[1].length == 4) {
        Triple(first, second, third)
    } else if (match.groupValues[3].length == 4 && first <= 12 && second > 12) {
        Triple(third, first, second)
    } else {
        Triple(third, second, first)
    }
    return runCatching { LocalDate.of(year, month, day) }.getOrNull()
}

/** Extracts only fields supported by explicit label text or previously known values. */
fun readLabel(lines: List<LabelLine>, roasters: List<String>, notes: List<String>, today: LocalDate): LabelReading {
    val weight = Regex("(?<![\\d.,])(\\d+(?:[.,]\\d+)?)\\s*(kg|g)\\b", RegexOption.IGNORE_CASE)
    val roast = Regex("\\b(torra|torrado em|torrada em|roast|roasted on)\\b")
    val excluded = Regex("\\b(validade|val\\.?|best before|fabricacao)\\b")
    val address = Regex("\\b(rua|avenida|av\\.?|estrada|rodovia|praca|street|st\\.?|road|rd\\.?|avenue|ave\\.?|boulevard|blvd\\.?|cep|zip|cnpj)\\b|(?:www\\.|https?://)")
    val notePatterns = notes.mapNotNull { note ->
        val normalized = key(note)
        normalized.takeIf { it.isNotEmpty() }?.let { note to Regex("(?<![\\p{L}\\p{N}])${Regex.escape(it)}(?![\\p{L}\\p{N}])") }
    }
    val weights = lines.flatMap { line -> weight.findAll(line.text).mapNotNull { match ->
        val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
        val grams = number * if (match.groupValues[2].equals("kg", true)) 1000 else 1
        grams.takeIf { it > 0 && it <= Int.MAX_VALUE && it % 1.0 == 0.0 }?.toInt()
    }.toList() }
    val roastDate = lines.indices.firstNotNullOfOrNull { i ->
        labelDate.findAll(key(lines[i].text)).firstNotNullOfOrNull { dateMatch ->
            val nearbyLabels = (maxOf(0, i - 1)..minOf(lines.lastIndex, i + 1)).flatMap { labelLine ->
                val normalized = key(lines[labelLine].text)
                (roast.findAll(normalized).map { it.range to true } + excluded.findAll(normalized).map { it.range to false })
                    .map { (range, isRoast) ->
                        val lineDistance = kotlin.math.abs(labelLine - i)
                        val textDistance = if (lineDistance == 0) {
                            when {
                                range.last < dateMatch.range.first -> dateMatch.range.first - range.last
                                dateMatch.range.last < range.first -> range.first - dateMatch.range.last + 1
                                else -> 0
                            }
                        } else 0
                        Triple(lineDistance, textDistance, isRoast)
                    }
            }
            val nearest = nearbyLabels.minWithOrNull(compareBy<Triple<Int, Int, Boolean>> { it.first }
                .thenBy { it.second }.thenBy { if (it.third) 1 else 0 })
            if (nearest?.third != true) null else parseLabelDate(dateMatch)?.takeUnless { it > today }
        }
    }
    val foundRoaster = lines.firstOrNull { line -> roasters.any { key(it) == key(line.text) && key(it).isNotEmpty() } }?.text?.trim()?.let(::roasterTitle)
    val foundNotes = mutableListOf<String>()
    for (line in lines) {
        val normalized = key(line.text)
        val matches = notePatterns.mapNotNull { (note, pattern) -> pattern.find(normalized)?.let { it.range.first to note } }
            .sortedWith(compareBy<Pair<Int, String>> { it.first }.thenByDescending { key(it.second).length })
        for ((_, note) in matches) if (foundNotes.none { key(it) == key(note) } && foundNotes.size < 6) foundNotes.add(note)
    }
    val candidates = lines.withIndex().filter { (_, line) ->
        val s = line.text.trim()
        val k = key(s)
        s.any { it.isLetter() } && !weight.matches(s) && labelDate.find(k) == null &&
            notePatterns.none { (_, pattern) -> pattern.matches(k) } &&
            !address.containsMatchIn(k) && !roast.containsMatchIn(k) && !excluded.containsMatchIn(k) &&
            roasters.none { key(it) == k }
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
