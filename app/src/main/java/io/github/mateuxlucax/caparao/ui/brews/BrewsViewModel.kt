package io.github.mateuxlucax.caparao.ui.brews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.SyncStatus
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BrewsUiState(
    /** Modes with at least one brew, in [BrewMode] order, each newest first. */
    val sections: List<Pair<BrewMode, List<Brew>>> = emptyList(),
    val loading: Boolean = true,
    /** Server, last upload and last error, for the line above the list. */
    val sync: SyncStatus = SyncStatus(serverConfigured = false, lastSyncMs = 0, lastError = null),
)

/** Saved brews grouped by mode (Brews wireframe), with the upload status. */
class BrewsViewModel(
    repository: BrewRepository,
    /** SettingsStore.syncStatus. */
    syncStatus: Flow<SyncStatus>,
    /** Enqueues every unsynced brew again (UploadScheduler.resendUnsynced). */
    private val resendUnsynced: suspend () -> Unit,
) : ViewModel() {
    val uiState: StateFlow<BrewsUiState> = combine(repository.brews, syncStatus) { brews, sync ->
        BrewsUiState(group(brews), loading = false, sync = sync)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrewsUiState())

    /** "Retry" after an upload error, or after the local network was allowed. */
    fun retry() {
        viewModelScope.launch { resendUnsynced() }
    }

    companion object {
        fun group(brews: List<Brew>): List<Pair<BrewMode, List<Brew>>> =
            BrewMode.entries.mapNotNull { mode ->
                brews.filter { it.mode == mode }.sortedByDescending { it.startedAt }.takeIf { it.isNotEmpty() }?.let { mode to it }
            }
    }
}
