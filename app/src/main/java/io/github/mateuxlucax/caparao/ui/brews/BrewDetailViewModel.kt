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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrewDetailUiState(
    val brew: Brew? = null,
    val samples: List<BrewSample> = emptyList(),
    val loading: Boolean = true,
    /** Set while the notes are being edited. */
    val notes: NotesForm? = null,
    val coffees: List<String> = emptyList(),
)

/** One saved brew with its curve. */
class BrewDetailViewModel(
    private val id: String,
    private val repository: BrewRepository,
    /** Called after the notes were saved, to send the brew again (UploadScheduler.brewSaved). */
    private val onEdited: (String) -> Unit = {},
) : ViewModel() {
    private val _uiState = MutableStateFlow(BrewDetailUiState())
    val uiState: StateFlow<BrewDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = BrewDetailUiState(repository.get(id), repository.samples(id), loading = false)
        }
    }

    fun editNotes() {
        val brew = _uiState.value.brew ?: return
        viewModelScope.launch {
            val coffees = repository.recentCoffees()
            _uiState.update { it.copy(notes = NotesForm.of(brew, coffees.firstOrNull()), coffees = coffees) }
        }
    }

    fun changeNotes(form: NotesForm) = _uiState.update { if (it.notes == null) it else it.copy(notes = form) }

    fun cancelNotes() = _uiState.update { it.copy(notes = null) }

    /** Saves the notes; the brew is unsynced until the server has them (docs/adr/0021-brew-notes.md). */
    fun saveNotes() {
        val state = _uiState.value
        val brew = state.brew ?: return
        val form = state.notes?.takeIf { it.valid } ?: return
        val updated = form.applyTo(brew)
        viewModelScope.launch {
            if (updated != brew) {
                repository.updateNotes(updated)
                onEdited(id)
            }
            _uiState.update { it.copy(brew = repository.get(id), notes = null) }
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
