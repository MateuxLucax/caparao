package dev.mateux.caparao.ui.coffees

import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewTaste

/** How often one of the bag's tasting notes was found, out of [CoffeeSummary.tasted] brews. */
data class NoteFound(val note: String, val found: Int)

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
