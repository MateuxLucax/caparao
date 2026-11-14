package dev.mateux.caparao.ui.brews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import dev.mateux.caparao.data.local.BrewRepository
import dev.mateux.caparao.data.local.CoffeeBagRepository
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewTaste
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrewRatingUiState(
    val brew: Brew? = null,
    val bagTastingNotes: List<String> = emptyList(),
    val taste: BrewTaste? = null,
    val tastingNotesFound: Set<String> = emptySet(),
    val rating: Int? = null,
    val notes: String = "",
    val loading: Boolean = true,
    val saving: Boolean = false,
)

/** Loads and saves the delayed rating for one saved brew (#464). */
class BrewRatingViewModel(
    private val id: String,
    private val repository: BrewRepository,
    private val coffeeBags: CoffeeBagRepository,
    /** Called after the changed rating has been persisted, so it can be uploaded again. */
    private val onSaved: (String) -> Unit = {},
) : ViewModel() {
    private val _uiState = MutableStateFlow(BrewRatingUiState())
    val uiState: StateFlow<BrewRatingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val brew = repository.get(id)
            val bag = brew?.bagId?.let { coffeeBags.get(it) }
            val availableNotes = bag?.tastingNotes.orEmpty()
            _uiState.value = BrewRatingUiState(
                brew = brew,
                bagTastingNotes = availableNotes,
                taste = brew?.taste,
                tastingNotesFound = availableNotes.filter { it in brew?.tastingNotesFound.orEmpty() }.toSet(),
                rating = brew?.rating,
                notes = brew?.notes.orEmpty(),
                loading = false,
            )
        }
    }

    fun setTaste(taste: BrewTaste) = _uiState.update {
        it.copy(taste = if (it.taste == taste) null else taste)
    }

    fun toggleTastingNote(note: String) = _uiState.update {
        if (note !in it.bagTastingNotes) it else it.copy(
            tastingNotesFound = if (note in it.tastingNotesFound) it.tastingNotesFound - note else it.tastingNotesFound + note,
        )
    }

    fun setRating(rating: Int?) = _uiState.update { it.copy(rating = rating?.takeIf { value -> value in 1..5 }) }

    fun setNotes(notes: String) = _uiState.update { it.copy(notes = notes.take(1000)) }

    /** Saves the chosen taste and notes, schedules the updated brew for upload, and closes the screen. */
    fun save(onClose: () -> Unit) {
        val state = _uiState.value
        val brew = state.brew ?: return
        if (state.loading || state.saving) return
        val updated = brew.copy(
            rating = state.rating,
            notes = state.notes.trim().ifEmpty { null },
            taste = state.taste,
            tastingNotesFound = (
                brew.tastingNotesFound.filterNot { it in state.bagTastingNotes } +
                    state.bagTastingNotes.filter { it in state.tastingNotesFound }
                ).distinct(),
        )
        if (updated == brew) {
            onClose()
            return
        }
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.saveRating(updated)
            onSaved(id)
            onClose()
        }
    }

    companion object {
        val BREW_ID = object : CreationExtras.Key<String> {}
    }
}
