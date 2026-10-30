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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class BrewDetailUiState(
    val brew: Brew? = null,
    val samples: List<BrewSample> = emptyList(),
    val loading: Boolean = true,
    /** The brew of the same recipe right before this one, for "Compare with last" (docs/adr/0022-compare-and-trends.md). */
    val previousId: String? = null,
)

/** One saved brew with its curve. */
class BrewDetailViewModel(private val id: String, private val repository: BrewRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(BrewDetailUiState())
    val uiState: StateFlow<BrewDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val brew = repository.get(id)
            val previous = brew?.let { Comparison.previous(it, repository.brews.first()) }
            _uiState.value = BrewDetailUiState(brew, repository.samples(id), loading = false, previousId = previous?.id)
        }
    }

    /** Deletes the brew and its curve, then calls [onDeleted]. */
    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(id)
            onDeleted()
        }
    }

    companion object {
        val BREW_ID = object : CreationExtras.Key<String> {}
    }
}
