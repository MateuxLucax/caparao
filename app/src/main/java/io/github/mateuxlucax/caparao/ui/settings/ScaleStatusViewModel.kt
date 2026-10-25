package io.github.mateuxlucax.caparao.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateuxlucax.caparao.data.scale.ScaleSource
import io.github.mateuxlucax.caparao.model.ScaleState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ScaleStatusUiState(val scale: ScaleState = ScaleState.Idle, val log: List<String> = emptyList())

/** The Settings tab's scale section: the connection, its log, and reconnecting. */
class ScaleStatusViewModel(private val scale: ScaleSource) : ViewModel() {
    val uiState: StateFlow<ScaleStatusUiState> = combine(scale.state, scale.log, ::ScaleStatusUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScaleStatusUiState(scale.state.value, scale.log.value))

    /** Drops the connection and starts over, e.g. after resetting the Dot's pairing. */
    fun reconnect() {
        scale.stop()
        scale.start()
    }
}
