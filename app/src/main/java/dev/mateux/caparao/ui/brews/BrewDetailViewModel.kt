package dev.mateux.caparao.ui.brews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import dev.mateux.caparao.data.local.BrewRepository
import dev.mateux.caparao.data.local.CoffeeBagRepository
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.CoffeeBag
import dev.mateux.caparao.ui.components.NotesForm
import dev.mateux.caparao.model.BrewSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrewDetailUiState(
    val brew: Brew? = null,
    val samples: List<BrewSample> = emptyList(),
    val loading: Boolean = true,
    /** Set while the notes are being edited. */
    val notes: NotesForm? = null,
    /** The open coffee bags to pick the coffee from while the notes are edited (#291). */
    val bags: List<CoffeeBag> = emptyList(),
)

/** One saved brew with its curve. */
class BrewDetailViewModel(
    private val id: String,
    private val repository: BrewRepository,
    /** Called after the notes were saved, to send the brew again (UploadScheduler.brewSaved). */
    private val onEdited: (String) -> Unit = {},
    /** The bags the notes pick the coffee from; null in tests that never edit notes. */
    private val coffeeBags: CoffeeBagRepository? = null,
    /** Called after the brew was deleted, to cancel its upload and local rating reminder. */
    private val onBrewDeleted: suspend (String) -> Unit = {},
) : ViewModel() {
    private val _uiState = MutableStateFlow(BrewDetailUiState())
    val uiState: StateFlow<BrewDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val samples = repository.samples(id)
            repository.observe(id)
                .collect { brew ->
                    _uiState.update { it.copy(brew = brew, samples = samples, loading = false) }
                }
        }
    }

    fun editNotes() {
        val brew = _uiState.value.brew ?: return
        viewModelScope.launch {
            val bags = coffeeBags?.bags?.first()?.filterNot(CoffeeBag::finished).orEmpty()
            _uiState.update { it.copy(notes = NotesForm.of(brew), bags = bags) }
        }
    }

    fun changeNotes(form: NotesForm) = _uiState.update { if (it.notes == null) it else it.copy(notes = form) }

    fun cancelNotes() = _uiState.update { it.copy(notes = null) }

    /** Saves the coffee and recipe notes; the brew is unsynced until the server has them (#105). */
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
            onBrewDeleted(id)
            onDeleted()
        }
    }

    companion object {
        val BREW_ID = object : CreationExtras.Key<String> {}
    }
}
