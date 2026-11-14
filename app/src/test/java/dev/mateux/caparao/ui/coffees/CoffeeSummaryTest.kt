package dev.mateux.caparao.ui.coffees

import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.BrewTaste
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoffeeSummaryTest {
    private var next = 0

    private fun brew(taste: BrewTaste? = null, vararg found: String) =
        Brew("b${next++}", BrewMode.FILTER, 0, 180_000, 20.0, 300.0, null, 1.6, 4.0, interrupted = false, taste = taste, tastingNotesFound = found.toList())

    private val notes = listOf("Caramel", "Orange", "Chocolate")

    @Test
    fun noTendencyBelowThreeBrewsWithATaste() {
        val two = listOf(brew(BrewTaste.BITTER), brew(BrewTaste.BITTER), brew())
        assertNull(CoffeeSummary.of(notes, two).tendency)
        assertEquals(BrewTaste.BITTER, CoffeeSummary.of(notes, two + brew(BrewTaste.SOUR)).tendency)
    }

    @Test
    fun aTieHasNoTendency() {
        val tied = listOf(brew(BrewTaste.BITTER), brew(BrewTaste.BITTER), brew(BrewTaste.SOUR), brew(BrewTaste.SOUR))
        assertNull(CoffeeSummary.of(notes, tied).tendency)
    }

    @Test
    fun countsEachNoteOverTheBrewsWithATasteAnswer() {
        val summary = CoffeeSummary.of(
            notes,
            listOf(
                brew(BrewTaste.BALANCED, "Caramel", "Chocolate"),
                brew(BrewTaste.BITTER, "caramel", "Chocolate"),
                brew(null, "Caramel", "Orange"),
                // Saved before rating existed, or not rated yet: not counted.
                brew(),
            ),
        )
        assertEquals(3, summary.tasted)
        assertEquals(listOf(NoteFound("Caramel", 3), NoteFound("Chocolate", 2), NoteFound("Orange", 1)), summary.notesFound)
    }

    @Test
    fun noNotesLineWithoutATasteAnswer() {
        val summary = CoffeeSummary.of(notes, listOf(brew(), brew()))
        assertEquals(0, summary.tasted)
        assertEquals(emptyList<NoteFound>(), summary.notesFound)
    }
}
