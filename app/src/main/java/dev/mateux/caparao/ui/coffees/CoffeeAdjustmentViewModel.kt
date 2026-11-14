package dev.mateux.caparao.ui.coffees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import dev.mateux.caparao.data.local.BagRecipeAdjustmentRepository
import dev.mateux.caparao.data.local.CoffeeBagRepository
import dev.mateux.caparao.data.local.RecipeRepository
import dev.mateux.caparao.model.BagRecipeAdjustment
import dev.mateux.caparao.model.BagRecipeAdjustmentHistory
import dev.mateux.caparao.model.BrewLimits
import dev.mateux.caparao.model.Recipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class CoffeeAdjustmentState(
    /** Null while the bag and the recipe load, and when either is gone. */
    val recipe: Recipe? = null,
    val bagName: String = "",
    /** The values this coffee brews with: the adjustment's, or the recipe's where it has none. */
    val grind: Int? = null,
    val temp: Int? = null,
    val dose: Double = 0.0,
    val tuned: Boolean = false,
    /** Something differs from the recipe, so there is an adjustment to put back. */
    val adjusted: Boolean = false,
    /** The values differ from the ones saved. */
    val dirty: Boolean = false,
    /** Saved: the screen closes. */
    val closed: Boolean = false,
)

/** Edits what one coffee changes in one recipe: grind, water temperature and dose (#462). */
class CoffeeAdjustmentViewModel(
    private val target: Target,
    bags: CoffeeBagRepository,
    recipes: RecipeRepository,
    private val adjustments: BagRecipeAdjustmentRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    data class Target(val bagId: String, val recipeId: String)

    private var saved: CoffeeAdjustmentState? = null
    private val _state = MutableStateFlow(CoffeeAdjustmentState())
    val state: StateFlow<CoffeeAdjustmentState> = _state.asStateFlow()

    /** Newest first. */
    val history: StateFlow<List<BagRecipeAdjustmentHistory>> =
        adjustments.history(target.bagId, target.recipeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val recipe = recipes.get(target.recipeId)
            val bag = bags.observe(target.bagId).first()
            if (recipe == null || bag == null) {
                _state.update { it.copy(closed = true) }
                return@launch
            }
            val adjustment = adjustments.get(target.bagId, target.recipeId)
            val loaded = CoffeeAdjustmentState(
                recipe = recipe,
                bagName = bag.name,
                grind = adjustment?.grind ?: recipe.grind?.trim()?.toIntOrNull(),
                temp = adjustment?.waterTempCelsius ?: recipe.waterTempCelsius,
                dose = adjustment?.doseGrams ?: recipe.doseGrams,
                tuned = adjustment?.tuned ?: false,
            ).checked(null)
            saved = loaded
            _state.value = loaded
        }
    }

    /** One grinder number finer or coarser; a recipe without a number starts at 1. */
    fun stepGrind(by: Int) = change { it.copy(grind = ((it.grind ?: 0) + by).coerceAtLeast(1)) }

    fun stepTemp(by: Int) = change { state ->
        state.temp?.let { state.copy(temp = (it + by).coerceIn(BrewLimits.TEMPERATURE_CELSIUS)) } ?: state
    }

    /** Half a gram at a time. */
    fun stepDose(by: Double) = change { it.copy(dose = (((it.dose + by) * 2).roundToInt() / 2.0).coerceIn(BrewLimits.DOSE_GRAMS)) }

    /** Back to the recipe's own values; nothing is stored until Save. */
    fun reset() = change { state ->
        val recipe = state.recipe ?: return@change state
        state.copy(grind = recipe.grind?.trim()?.toIntOrNull(), temp = recipe.waterTempCelsius, dose = recipe.doseGrams)
    }

    private fun change(edit: (CoffeeAdjustmentState) -> CoffeeAdjustmentState) = _state.update { edit(it).checked(saved) }

    private fun CoffeeAdjustmentState.checked(saved: CoffeeAdjustmentState?): CoffeeAdjustmentState {
        val recipe = recipe ?: return this
        val adjusted = grind != recipe.grind?.trim()?.toIntOrNull() || temp != recipe.waterTempCelsius || dose != recipe.doseGrams
        return copy(adjusted = adjusted, dirty = saved != null && (grind != saved.grind || temp != saved.temp || dose != saved.dose))
    }

    /** Stores only what differs from the recipe; a value changed by hand is no longer the tuned one. */
    fun save() {
        val state = _state.value
        val recipe = state.recipe ?: return
        val adjustment = BagRecipeAdjustment(
            bagId = target.bagId,
            recipeId = target.recipeId,
            grind = state.grind.takeIf { it != recipe.grind?.trim()?.toIntOrNull() },
            waterTempCelsius = state.temp.takeIf { it != recipe.waterTempCelsius },
            doseGrams = state.dose.takeIf { it != recipe.doseGrams },
            tuned = state.tuned && !state.dirty,
        )
        viewModelScope.launch {
            adjustments.save(adjustment, clock())
            _state.update { it.copy(closed = true) }
        }
    }

    companion object {
        val TARGET = object : CreationExtras.Key<Target> {}
    }
}
