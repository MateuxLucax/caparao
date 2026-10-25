package io.github.mateuxlucax.caparao.ui.settings

import io.github.mateuxlucax.caparao.model.ScaleError
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.ui.FakeScaleSource
import io.github.mateuxlucax.caparao.ui.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ScaleStatusViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val scale = FakeScaleSource()

    // Lazy: viewModelScope must be created after the rule sets the main dispatcher.
    private val viewModel by lazy { ScaleStatusViewModel(scale) }

    @Test
    fun showsTheConnectionAndItsLog() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        scale.state.value = ScaleState.Failed(ScaleError.PAIRED_ELSEWHERE, retrying = true)
        scale.log.value = listOf("open: pairing refused")
        assertEquals(ScaleStatusUiState(ScaleState.Failed(ScaleError.PAIRED_ELSEWHERE, true), listOf("open: pairing refused")), viewModel.uiState.value)
    }

    @Test
    fun reconnectStartsOver() {
        viewModel.reconnect()
        assertEquals(listOf("stop", "start"), scale.commands)
    }
}
