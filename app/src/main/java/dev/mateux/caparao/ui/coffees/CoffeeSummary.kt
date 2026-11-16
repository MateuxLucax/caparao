package dev.mateux.caparao.ui.coffees

import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewTaste
import kotlin.math.roundToInt

/** How often one of the bag's tasting notes was found, out of [CoffeeSummary.tasted] brews. */
data class NoteFound(val note: String, val found: Int)

/** Whole days left at the bag's average daily dose over its recent brews, or null without enough recent use. */
fun estimatedDaysLeft(leftGrams: Double, brews: List<Brew>, now: Long): Int? {
    if (leftGrams <= 0.0) return null
    val recent = brews.filter { now - it.startedAt in 0..(14 * DAY_MS) }
    if (recent.size < 3) return null
    val firstUse = recent.minOf { it.startedAt }
    val days = ((now - firstUse) / DAY_MS).coerceIn(1, 14)
    val gramsPerDay = recent.sumOf { it.doseGrams } / days
    if (gramsPerDay <= 0.0) return null
    val remaining = leftGrams / gramsPerDay
    return if (remaining < 1.0) 0 else remaining.roundToInt().coerceAtLeast(1)
}

private const val DAY_MS = 86_400_000L

/** How a coffee has been turning out, from the brews linked to its bag (#469). */
data class CoffeeSummary(
    /** Brews with a taste answer: a taste, or at least one note found. */
    val tasted: Int,
    /** The taste most brews had; null below [MIN_FOR_TENDENCY] brews with a taste, or when two tastes tie. */
    val tendency: BrewTaste?,
    /** The bag's notes, most found first; empty while no brew has a taste answer. */
    val notesFound: List<NoteFound>,
) {
    companion object {
        const val MIN_FOR_TENDENCY = 3

        fun of(tastingNotes: List<String>, brews: List<Brew>): CoffeeSummary {
            val tastes = brews.mapNotNull { it.taste }
            val counts = tastes.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
            val tendency = counts.firstOrNull()
                ?.takeIf { tastes.size >= MIN_FOR_TENDENCY && it.value > (counts.getOrNull(1)?.value ?: 0) }
                ?.key
            val tasted = brews.filter { it.taste != null || it.tastingNotesFound.isNotEmpty() }
            val found = if (tasted.isEmpty()) {
                emptyList()
            } else {
                // sortedByDescending is stable: notes found equally often keep the profile's order.
                tastingNotes
                    .map { note -> NoteFound(note, tasted.count { brew -> brew.tastingNotesFound.any { it.equals(note, ignoreCase = true) } }) }
                    .sortedByDescending { it.found }
            }
            return CoffeeSummary(tasted.size, tendency, found)
        }
    }
}
