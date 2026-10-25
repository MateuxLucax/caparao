package io.github.mateuxlucax.caparao.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateuxlucax.caparao.data.scale.ScaleSource
import io.github.mateuxlucax.caparao.data.scale.TimerAction
import io.github.mateuxlucax.caparao.model.ScaleReading
import io.github.mateuxlucax.caparao.model.ScaleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val scale: ScaleState = ScaleState.Idle,
    val reading: ScaleReading? = null,
    /** Whether the scale's timer was started from here; the Dot does not report it. */
    val timerRunning: Boolean = false,
)

/** The live scale readout: connection, weight, flow, battery, and the tare and timer controls. */
class HomeViewModel(private val scale: ScaleSource) : ViewModel() {
    private val timerRunning = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(scale.state, scale.reading, timerRunning) { state, reading, running ->
        HomeUiState(state, reading, running && state is ScaleState.Connected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(scale.state.value, scale.reading.value))

    fun tare() = scale.tare()

    fun toggleTimer() {
        val running = !timerRunning.value
        scale.timer(if (running) TimerAction.START else TimerAction.PAUSE)
        timerRunning.value = running
    }

    fun resetTimer() {
        scale.timer(TimerAction.RESET)
        timerRunning.value = false
    }

    /** After the user fixed what stopped the connection (Bluetooth, permission). */
    fun retry() {
        scale.stop()
        scale.start()
    }
}
