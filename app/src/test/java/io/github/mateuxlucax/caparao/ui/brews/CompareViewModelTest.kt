package io.github.mateuxlucax.caparao.ui.brews

import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewSample
import io.github.mateuxlucax.caparao.ui.FakeBrewDao
import io.github.mateuxlucax.caparao.ui.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class CompareViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repository = BrewRepository(FakeBrewDao())
    private fun brew(id: String, at: Long) =
        Brew(id, BrewMode.ESPRESSO, at, 28_000, 18.0, 36.0, 4_000, 1.3, 2.0, interrupted = false, recipeId = "r", recipeName = "Espresso")
    private val curve = listOf(BrewSample(0, 0.0, 0.0), BrewSample(100, 0.4, 4.0))

    @Test
    fun loadsTheBrewsInPickOrderAndSkipsDeletedOnes() = runTest {
        repository.save(brew("a", 1), curve)
        repository.save(brew("b", 2), curve)
        val state = CompareViewModel(listOf("b", "gone", "a"), repository).uiState.value
        assertFalse(state.loading)
        assertEquals(listOf("b", "a"), state.brews.map { it.brew.id })
        assertEquals(curve, state.brews[0].samples)
    }

    @Test
    fun detailOffersThePreviousBrewOfItsRecipe() = runTest {
        repository.save(brew("a", 1), curve)
        repository.save(brew("b", 2), curve)
        assertEquals("a", BrewDetailViewModel("b", repository).uiState.value.previousId)
        assertEquals(null, BrewDetailViewModel("a", repository).uiState.value.previousId)
    }

    @Test
    fun trendsFollowTheRecipe() = runTest {
        repository.save(brew("a", 1), curve)
        repository.save(brew("b", 2), curve)
        val viewModel = TrendsViewModel(TrendKey(BrewMode.ESPRESSO, "r"), repository)
        val state = viewModel.uiState.first { !it.loading }
        assertEquals(listOf("a", "b"), state.brews.map { it.id })
        assertEquals("Espresso", state.title)
    }
}
