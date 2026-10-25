package io.github.mateuxlucax.caparao.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateuxlucax.caparao.data.scale.ScaleSource
import io.github.mateuxlucax.caparao.model.ScaleReading
import io.github.mateuxlucax.caparao.model.ScaleState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class OnboardingUiState(
    val scale: ScaleState = ScaleState.Idle,
    val reading: ScaleReading? = null,
    val log: List<String> = emptyList(),
)

/** First launch: the scale step shows the live connection; finishing marks onboarding done. The step itself is UI state. */
class OnboardingViewModel(private val scale: ScaleSource, private val markDone: () -> Unit) : ViewModel() {
    val uiState: StateFlow<OnboardingUiState> = combine(scale.state, scale.reading, scale.log, ::OnboardingUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState(scale.state.value, scale.reading.value))

    fun retry() {
        scale.stop()
        scale.start()
    }

    fun finish() = markDone()
}
