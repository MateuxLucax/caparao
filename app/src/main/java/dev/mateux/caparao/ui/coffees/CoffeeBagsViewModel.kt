package dev.mateux.caparao.ui.coffees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import dev.mateux.caparao.data.local.CoffeeBagRepository
import dev.mateux.caparao.data.local.BagRecipeAdjustmentRepository
import dev.mateux.caparao.model.CoffeeBag
import dev.mateux.caparao.ui.components.RecipeForm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** A bag as typed in the form; [id] is null for a new one. */
@Serializable
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
    val tastingNotes: List<String> = emptyList(),
    val sourceBagId: String? = null,
    val finishSource: Boolean = true,
    val copiedAdjustmentCount: Int = 0,
) {
    val valid: Boolean get() = name.isNotBlank() && RecipeForm.number(weight) != null

    /**
     * Adds [value] as a tasting note when there is room: trimmed, case-insensitive duplicates ignored,
     * six notes at most. Returns this form unchanged when the note is not added.
     */
    fun withNote(value: String): BagForm {
        val note = value.trim()
        if (note.isEmpty() || tastingNotes.size >= 6 || tastingNotes.any { it.equals(note, true) }) return this
        return copy(tastingNotes = tastingNotes + note)
    }

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
        tastingNotes = tastingNotes.take(6).map(String::trim).filter(String::isNotEmpty),
    )

    companion object {
        fun of(bag: CoffeeBag) = BagForm(
            bag.id, bag.name, bag.roaster.orEmpty(), bag.origin.orEmpty(), bag.roastedAt,
            RecipeForm.plain(bag.weightGrams, java.util.Locale.ROOT), bag.notes.orEmpty(), bag.finished, bag.createdAt, bag.tastingNotes,
        )
    }
}

/** The full-screen editor draft is saved with its navigation entry across process death. */
class CoffeeEditorViewModel(private val savedState: SavedStateHandle, target: BagForm = BagForm()) : ViewModel() {
    private val _form = MutableStateFlow(savedState.get<String>("form")?.let { Json.decodeFromString<BagForm>(it) } ?: target)
    val form: StateFlow<BagForm> = _form.asStateFlow()
    private var opened = _form.value
    val dirty: Boolean get() = _form.value != opened
    fun change(form: BagForm) { _form.value = form; savedState["form"] = Json.encodeToString(form) }
    fun reset(form: BagForm) { opened = form; change(form) }
    companion object { val TARGET = object : CreationExtras.Key<BagForm> {} }
}

/** The coffee bags list and persistence operations. */
class CoffeeBagsViewModel(
    private val repository: CoffeeBagRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val adjustments: BagRecipeAdjustmentRepository? = null,
) : ViewModel() {
    val bags: StateFlow<List<CoffeeBag>?> = repository.bags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(form: BagForm) {
        if (!form.valid) return
        viewModelScope.launch {
            val bag = form.toModel(clock(), newId)
            if (form.sourceBagId == null) repository.save(bag)
            else repository.openAnotherBag(bag, form.sourceBagId, form.finishSource, clock())
        }
    }

    fun now(): Long = clock()
}

internal fun coffeeFieldSuggestions(
    bags: List<CoffeeBag>, query: String, excludingId: String?, field: (CoffeeBag) -> String?,
): List<String> {
    val match = query.coffeeKey()
    if (match.isEmpty()) return emptyList()
    return bags.asSequence().filter { it.id != excludingId }.sortedByDescending(CoffeeBag::createdAt)
        .mapNotNull(field).map(String::trim).filter { it.isNotEmpty() && it.coffeeKey() != match && it.coffeeKey().contains(match) }
        .distinctBy(String::coffeeKey).take(3).toList()
}

internal fun coffeeNoteSuggestions(
    bags: List<CoffeeBag>, defaults: List<String>, selected: List<String>, excludingId: String?,
): List<String> {
    val excluded = selected.mapTo(mutableSetOf(), String::coffeeKey)
    return (bags.asSequence().filter { it.id != excludingId }.sortedByDescending(CoffeeBag::createdAt)
        .flatMap { it.tastingNotes.asSequence() } + defaults.asSequence())
        .map(String::trim).filter(String::isNotEmpty).distinctBy(String::coffeeKey)
        .filterNot { it.coffeeKey() in excluded }.toList()
}

private fun String.coffeeKey() = Normalizer.normalize(this.trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)
