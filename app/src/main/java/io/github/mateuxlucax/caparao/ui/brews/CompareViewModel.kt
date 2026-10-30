package io.github.mateuxlucax.caparao.ui.brews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One curve of the overlay. */
data class ComparedBrew(val brew: Brew, val samples: List<BrewSample>)

data class CompareUiState(
    /** In the order they were picked; a brew deleted meanwhile is left out. */
    val brews: List<ComparedBrew> = emptyList(),
    val loading: Boolean = true,
)

/** Two to four brews of one mode, their weight curves overlaid (docs/adr/0022-compare-and-trends.md). */
class CompareViewModel(private val ids: List<String>, private val repository: BrewRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(CompareUiState())
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val brews = ids.mapNotNull { id -> repository.get(id)?.let { ComparedBrew(it, repository.samples(id)) } }
            _uiState.value = CompareUiState(brews, loading = false)
        }
    }

    companion object {
        val BREW_IDS = object : CreationExtras.Key<List<String>> {}
    }
}
