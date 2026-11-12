package dev.mateux.caparao.ui.home

import dev.mateux.caparao.model.ScaleBattery
import dev.mateux.caparao.model.ScaleError
import dev.mateux.caparao.model.ScaleReading
import dev.mateux.caparao.model.ScaleState
import dev.mateux.caparao.ui.FakeScaleSource
import dev.mateux.caparao.ui.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val scale = FakeScaleSource()
    // Lazy: viewModelScope must be created after the rule sets the main dispatcher.
    private val viewModel by lazy { HomeViewModel(scale) }

    @Test
    fun followsTheScale() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val reading = ScaleReading(18.2, 1.5, 7, overload = false)
        scale.state.value = ScaleState.Connected(ScaleBattery(percent = 80, bars = 3))
        scale.reading.value = reading
        val state = viewModel.uiState.first { it.reading != null }
        assertEquals(ScaleState.Connected(ScaleBattery(80, 3)), state.scale)
        assertEquals(reading, state.reading)
    }

    @Test
    fun timerTogglesBetweenStartAndPauseAndResetStopsIt() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        scale.state.value = ScaleState.Connected(null)
        viewModel.toggleTimer()
        assertTrue(viewModel.uiState.value.timerRunning)
        viewModel.toggleTimer()
        assertFalse(viewModel.uiState.value.timerRunning)
        viewModel.toggleTimer()
        viewModel.resetTimer()
        assertFalse(viewModel.uiState.value.timerRunning)
        viewModel.tare()
        assertEquals(listOf("timer start", "timer pause", "timer start", "timer reset", "tare"), scale.commands)
    }

    @Test
    fun aLostConnectionStopsShowingTheTimerAsRunning() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        scale.state.value = ScaleState.Connected(null)
        viewModel.toggleTimer()
        scale.state.value = ScaleState.Failed(ScaleError.CONNECTION_LOST, retrying = true)
        assertFalse(viewModel.uiState.value.timerRunning)
    }

    @Test
    fun retryRestartsTheConnection() {
        viewModel.retry()
        assertEquals(listOf("stop", "start"), scale.commands)
    }
}
