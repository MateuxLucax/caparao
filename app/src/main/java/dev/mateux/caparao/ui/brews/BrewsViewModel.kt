package dev.mateux.caparao.ui.brews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mateux.caparao.data.local.BrewRepository
import dev.mateux.caparao.data.local.SyncStatus
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.CoffeeBag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A saved brew with the name of its coffee: the linked bag's, or what was typed after the brew. */
data class BrewLine(val brew: Brew, val coffee: String?)

/** An open bag in the filter row. */
data class CoffeeFilter(val bagId: String, val name: String)

data class BrewsUiState(
    /** Brews not rated yet, newest first, after the coffee filter. */
    val toRate: List<BrewLine> = emptyList(),
    /** The other brews, newest first, after the coffee filter. */
    val rated: List<BrewLine> = emptyList(),
    /** The open bags, for the filter row. */
    val coffees: List<CoffeeFilter> = emptyList(),
    /** The bag the list is filtered by; null shows every brew. */
    val coffee: String? = null,
    /** No brew saved at all, whatever the filter. */
    val empty: Boolean = true,
    val loading: Boolean = true,
    /** Server, last upload and last error, for the line above the list. */
    val sync: SyncStatus = SyncStatus(serverConfigured = false, lastSyncMs = 0, lastError = null),
)

/** Saved brews in one list by date, the ones to rate first (#466), with the upload status. */
class BrewsViewModel(
    repository: BrewRepository,
    /** CoffeeBagRepository.bags. */
    bags: Flow<List<CoffeeBag>>,
    /** SettingsStore.syncStatus. */
    syncStatus: Flow<SyncStatus>,
    /** Enqueues every unsynced brew again (UploadScheduler.resendUnsynced). */
    private val resendUnsynced: suspend () -> Unit,
) : ViewModel() {
    private val coffee = MutableStateFlow<String?>(null)

    val uiState: StateFlow<BrewsUiState> = combine(repository.brews, bags, coffee, syncStatus) { brews, bags, coffee, sync ->
        lines(brews, bags, coffee).copy(loading = false, sync = sync)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrewsUiState())

    /** Filters the list by an open bag; null shows every brew again. */
    fun filter(bagId: String?) {
        coffee.value = bagId
    }

    /** "Retry" after an upload error, or after the local network was allowed. */
    fun retry() {
        viewModelScope.launch { resendUnsynced() }
    }

    companion object {
        /** A filter on a bag that was finished or deleted since falls back to every brew. */
        fun lines(brews: List<Brew>, bags: List<CoffeeBag>, coffee: String?): BrewsUiState {
            val names = bags.associate { it.id to it.name }
            val filters = bags.filterNot { it.finished }.map { CoffeeFilter(it.id, it.name) }
            val selected = coffee?.takeIf { id -> filters.any { it.bagId == id } }
            val shown = brews
                .filter { selected == null || it.bagId == selected }
                .sortedByDescending { it.startedAt }
                .map { BrewLine(it, names[it.bagId] ?: it.coffee?.takeIf(String::isNotBlank)) }
            val (toRate, rated) = shown.partition { it.brew.ratedAt == null }
            return BrewsUiState(toRate, rated, filters, selected, empty = brews.isEmpty())
        }
    }
}
