package io.github.mateuxlucax.caparao.ui.brews

import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewSample
import io.github.mateuxlucax.caparao.ui.FakeBrewDao
import io.github.mateuxlucax.caparao.ui.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrewDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repository = BrewRepository(FakeBrewDao())
    private val brew = Brew("b1", BrewMode.ESPRESSO, 1_000, 28_000, 18.0, 36.0, 4_000, 1.3, 2.0, interrupted = false)
    private val curve = listOf(BrewSample(0, 0.0, 0.0), BrewSample(100, 0.4, 4.0))

    @Test
    fun loadsTheBrewAndItsCurve() = runTest {
        repository.save(brew, curve)
        val state = BrewDetailViewModel("b1", repository).uiState.value
        assertFalse(state.loading)
        assertEquals(brew, state.brew)
        assertEquals(curve, state.samples)
    }

    @Test
    fun aMissingBrewStopsLoadingWithNothing() {
        val state = BrewDetailViewModel("gone", repository).uiState.value
        assertFalse(state.loading)
        assertNull(state.brew)
        assertTrue(state.samples.isEmpty())
    }

    @Test
    fun deleteRemovesTheBrewBeforeCallingBack() = runTest {
        repository.save(brew, curve)
        var goneWhenCalled: Boolean? = null
        BrewDetailViewModel("b1", repository).delete { goneWhenCalled = true }
        assertEquals(true, goneWhenCalled)
        assertNull(repository.get("b1"))
        assertTrue(repository.samples("b1").isEmpty())
    }
}
