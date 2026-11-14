package dev.mateux.caparao.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import dev.mateux.caparao.data.local.RecipeRepository
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.Recipe
import dev.mateux.caparao.model.StepKind
import dev.mateux.caparao.ui.components.RecipeForm
import dev.mateux.caparao.ui.components.StepForm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

data class RecipeEditorState(
    /** Null while an existing recipe loads. */
    val form: RecipeForm?,
    val isNew: Boolean,
    /** Shown only after a save was tried, so a new form doesn't open all red. */
    val errors: Set<RecipeForm.Field> = emptySet(),
    /** Saved or deleted: the screen closes. */
    val closed: Boolean = false,
    /** The form differs from the one the editor opened with: leaving asks first (#181). */
    val dirty: Boolean = false,
)

/** Creates or edits one recipe ([target]); steps are kept in the order typed. */
class RecipeEditorViewModel(
    private val target: Target,
    private val repository: RecipeRepository,
    private val locale: Locale = Locale.getDefault(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    /** A recipe to edit, or a new one in a mode. */
    @Serializable
    sealed interface Target {
        @Serializable data class Edit(val id: String) : Target
        @Serializable data class New(val mode: BrewMode) : Target
    }

    private var existing: Recipe? = null

    /** The form as it opened, to tell an untouched editor from one with changes to lose. */
    private var initial: RecipeForm? = (target as? Target.New)?.let { RecipeForm.new(it.mode) }

    private val _state = MutableStateFlow(
        RecipeEditorState(form = initial, isNew = target is Target.New),
    )
    val state: StateFlow<RecipeEditorState> = _state.asStateFlow()

    init {
        if (target is Target.Edit) {
            viewModelScope.launch {
                val recipe = repository.get(target.id)
                existing = recipe
                val form = recipe?.let { RecipeForm.of(it, locale) }
                initial = form
                _state.update { if (form == null) it.copy(closed = true) else it.copy(form = form, dirty = false) }
            }
        }
    }

    fun edit(change: (RecipeForm) -> RecipeForm) = _state.update { state ->
        val form = state.form?.let(change) ?: return@update state
        // Once shown, errors follow the edits, so they clear as the fields are fixed.
        state.copy(form = form, errors = if (state.errors.isEmpty()) emptySet() else form.validate(), dirty = form != initial)
    }

    /** Adds a step after the last one: a pour, starting 30 s after it, at the flow the last pour asked for. */
    fun addStep() = edit { form ->
        val last = form.steps.lastOrNull()?.let { RecipeForm.clock(it.start) } ?: -30_000
        val flow = form.steps.lastOrNull { it.kind.pours }?.flowTarget.orEmpty()
        form.copy(steps = form.steps + StepForm(StepKind.POUR, start = RecipeForm.clockText(last + 30_000), flowTarget = flow))
    }

    /**
     * Moves a step to another place in the list. The start times stay where they are, so the list is still in time
     * order: the step takes the start of the place it lands on.
     */
    fun moveStep(from: Int, to: Int) = edit { form ->
        if (from !in form.steps.indices || to !in form.steps.indices || from == to) return@edit form
        val moved = form.steps.toMutableList().apply { add(to, removeAt(from)) }
        form.copy(steps = moved.mapIndexed { i, step -> step.copy(start = form.steps[i].start) })
    }

    fun removeStep(index: Int) = edit { it.copy(steps = it.steps.filterIndexed { i, _ -> i != index }) }

    fun updateStep(index: Int, step: StepForm) = edit { it.copy(steps = it.steps.mapIndexed { i, s -> if (i == index) step else s }) }

    fun save() {
        val form = _state.value.form ?: return
        val errors = form.validate()
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        val now = clock()
        val recipe = form.toRecipe(existing?.id ?: newId(), existing?.createdAt ?: now, now)
        viewModelScope.launch {
            repository.save(recipe)
            _state.update { it.copy(closed = true) }
        }
    }

    /** Brews that followed it keep its name. */
    fun delete() {
        val recipe = existing ?: return
        viewModelScope.launch {
            repository.delete(recipe.id)
            _state.update { it.copy(closed = true) }
        }
    }

    companion object {
        val TARGET = object : CreationExtras.Key<Target> {}
    }
}
