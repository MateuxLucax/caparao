package io.github.mateuxlucax.caparao.ui.brews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BrewsUiState(
    /** Modes with at least one brew, in [BrewMode] order, each newest first. */
    val sections: List<Pair<BrewMode, List<Brew>>> = emptyList(),
    val loading: Boolean = true,
)

/** Saved brews grouped by mode (Brews wireframe). */
class BrewsViewModel(repository: BrewRepository) : ViewModel() {
    val uiState: StateFlow<BrewsUiState> = repository.brews
        .map { brews -> BrewsUiState(group(brews), loading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrewsUiState())

    companion object {
        fun group(brews: List<Brew>): List<Pair<BrewMode, List<Brew>>> =
            BrewMode.entries.mapNotNull { mode ->
                brews.filter { it.mode == mode }.sortedByDescending { it.startedAt }.takeIf { it.isNotEmpty() }?.let { mode to it }
            }
    }
}
