package io.github.mateuxlucax.caparao.ui.brews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.model.Brew
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class TrendsUiState(
    val key: TrendKey,
    /** The recipe's name from its newest brew; null for brews without a recipe. */
    val title: String? = null,
    /** Oldest first. */
    val brews: List<Brew> = emptyList(),
    val loading: Boolean = true,
)

/** Ratio and total time over the last brews of a recipe, computed from Room with no aggregation tables (ADR 0022). */
class TrendsViewModel(private val key: TrendKey, repository: BrewRepository) : ViewModel() {
    val uiState: StateFlow<TrendsUiState> = repository.brews.map { all ->
        val brews = Comparison.trend(key, all)
        TrendsUiState(key, brews.lastOrNull()?.recipeName, brews, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrendsUiState(key))

    companion object {
        val KEY = object : CreationExtras.Key<TrendKey> {}
    }
}
