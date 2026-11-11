package io.github.mateuxlucax.caparao.ui.brews
import io.github.mateuxlucax.caparao.ui.components.NotesForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.mateuxlucax.caparao.data.export.BrewExportFiles
import io.github.mateuxlucax.caparao.data.export.ExportedFile
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.CoffeeBagRepository
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.CoffeeBag
import io.github.mateuxlucax.caparao.model.BrewSample
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class BrewDetailUiState(
    val brew: Brew? = null,
    val samples: List<BrewSample> = emptyList(),
    val loading: Boolean = true,
    /** Set while the notes are being edited. */
    val notes: NotesForm? = null,
    /** The open coffee bags to pick the coffee from while the notes are edited (#291). */
    val bags: List<CoffeeBag> = emptyList(),
    /** The file handed to the share sheet, once. */
    val share: ExportedFile? = null,
    val shareFailed: Boolean = false,
)

/** One saved brew with its curve. */
class BrewDetailViewModel(
    private val id: String,
    private val repository: BrewRepository,
    /** Writes the JSON for "Share"; null in tests that never share. */
    private val export: BrewExportFiles? = null,
    /** Called after the notes were saved, to send the brew again (UploadScheduler.brewSaved). */
    private val onEdited: (String) -> Unit = {},
    /** The bags the notes pick the coffee from; null in tests that never edit notes. */
    private val coffeeBags: CoffeeBagRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BrewDetailUiState())
    val uiState: StateFlow<BrewDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val brew = repository.get(id)
            _uiState.value = BrewDetailUiState(brew, repository.samples(id), loading = false)
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

    /** Saves the notes; the brew is unsynced until the server has them (#105). */
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

    /** Builds the brew's JSON for the share sheet (#76). */
    fun share() {
        val state = _uiState.value
        val brew = state.brew ?: return
        val exporter = export ?: return
        _uiState.update { it.copy(share = null, shareFailed = false) }
        viewModelScope.launch {
            try {
                val file = exporter.one(brew, state.samples)
                _uiState.update { it.copy(share = file) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(shareFailed = true) }
            }
        }
    }

    /** The chooser opened; clear the one-shot file. */
    fun shared() = _uiState.update { it.copy(share = null) }

    companion object {
        val BREW_ID = object : CreationExtras.Key<String> {}
    }
}
