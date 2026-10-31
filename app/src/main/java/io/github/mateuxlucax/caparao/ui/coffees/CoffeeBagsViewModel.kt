package io.github.mateuxlucax.caparao.ui.coffees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateuxlucax.caparao.data.local.CoffeeBagRepository
import io.github.mateuxlucax.caparao.model.CoffeeBag
import io.github.mateuxlucax.caparao.ui.recipes.RecipeForm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** A bag as typed in the form; [id] is null for a new one. */
data class BagForm(
    val id: String? = null,
    val name: String = "",
    val roaster: String = "",
    val origin: String = "",
    val roastedAt: Long? = null,
    val weight: String = "",
    val notes: String = "",
    val finished: Boolean = false,
    val createdAt: Long? = null,
) {
    val valid: Boolean get() = name.isNotBlank() && RecipeForm.number(weight) != null

    fun toModel(now: Long, newId: () -> String) = CoffeeBag(
        id = id ?: newId(),
        name = name.trim(),
        roaster = roaster.trim().ifEmpty { null },
        origin = origin.trim().ifEmpty { null },
        roastedAt = roastedAt,
        weightGrams = requireNotNull(RecipeForm.number(weight)),
        notes = notes.trim().ifEmpty { null },
        finished = finished,
        createdAt = createdAt ?: now,
    )

    companion object {
        fun of(bag: CoffeeBag) = BagForm(
            bag.id, bag.name, bag.roaster.orEmpty(), bag.origin.orEmpty(), bag.roastedAt,
            RecipeForm.plain(bag.weightGrams, java.util.Locale.ROOT), bag.notes.orEmpty(), bag.finished, bag.createdAt,
        )
    }
}

/** The coffee bags in Settings: the list and the bag being edited, if any. */
class CoffeeBagsViewModel(
    private val repository: CoffeeBagRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    val bags: StateFlow<List<CoffeeBag>?> = repository.bags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _editing = MutableStateFlow<BagForm?>(null)
    val editing: StateFlow<BagForm?> = _editing.asStateFlow()

    fun new() {
        _editing.value = BagForm()
    }

    fun edit(bag: CoffeeBag) {
        _editing.value = BagForm.of(bag)
    }

    fun change(form: BagForm) = _editing.update { if (it == null) null else form }

    fun cancel() {
        _editing.value = null
    }

    fun save() {
        val form = _editing.value?.takeIf { it.valid } ?: return
        _editing.value = null
        viewModelScope.launch { repository.save(form.toModel(clock(), newId)) }
    }

    fun delete() {
        val id = _editing.value?.id ?: return
        _editing.value = null
        viewModelScope.launch { repository.delete(id) }
    }

    fun now(): Long = clock()
}
