package io.github.mateuxlucax.caparao.ui.brews

import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class BrewsViewModelTest {
    private fun brew(id: String, mode: BrewMode, startedAt: Long) =
        Brew(id, mode, startedAt, 28_000, 18.0, 36.0, 4_000, 1.3, 2.4, interrupted = false)

    @Test
    fun groupsByModeInModeOrderNewestFirst() {
        val brews = listOf(
            brew("e1", BrewMode.ESPRESSO, 1),
            brew("f1", BrewMode.FILTER, 2),
            brew("e2", BrewMode.ESPRESSO, 3),
        )
        val sections = BrewsViewModel.group(brews)
        assertEquals(listOf(BrewMode.FILTER, BrewMode.ESPRESSO), sections.map { it.first })
        assertEquals(listOf("e2", "e1"), sections[1].second.map { it.id })
    }

    @Test
    fun modesWithoutBrewsAreLeftOut() {
        assertEquals(listOf(BrewMode.ESPRESSO), BrewsViewModel.group(listOf(brew("e1", BrewMode.ESPRESSO, 1))).map { it.first })
        assertEquals(emptyList<Pair<BrewMode, List<Brew>>>(), BrewsViewModel.group(emptyList()))
    }
}
