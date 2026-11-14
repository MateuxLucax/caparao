package dev.mateux.caparao.ui.brews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import dev.mateux.caparao.brew.BrewAdjustmentSuggestion
import dev.mateux.caparao.data.local.BagRecipeAdjustmentRepository
import dev.mateux.caparao.data.local.BrewRepository
import dev.mateux.caparao.data.local.CoffeeBagRepository
import dev.mateux.caparao.model.BagRecipeAdjustment
import dev.mateux.caparao.model.BagRecipeAdjustmentReason
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewLimits
import dev.mateux.caparao.model.Recipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The value a suggestion moves. */
enum class SuggestedField { GRIND, TEMPERATURE }

data class BrewSuggestionState(
    val loading: Boolean = true,
    /** Saved, or there is nothing to show: the screen closes. */
    val closed: Boolean = false,
    /** A save is in flight; further steps and saves are ignored until it lands (#468 review). */
    val saving: Boolean = false,
    val brew: Brew? = null,
    val recipe: Recipe? = null,
    val bagName: String = "",
    /** How many tasting notes the bag has, for "2 of 3 notes found". */
    val bagNotes: Int = 0,
    /** Null for opposite signals: both facts are shown and no value is proposed. */
    val field: SuggestedField? = null,
    /** The value this brew actually brewed with. */
    val from: Int = 0,
    /** The proposed value; − and + move it before saving. */
    val to: Int = 0,
)

/** Shows what a rated brew suggests for its coffee and saves it as the coffee's adjustment (#468). */
class BrewSuggestionViewModel(
    private val brewId: String,
    brews: BrewRepository,
    bags: CoffeeBagRepository,
    suggestions: BrewSuggestions,
    private val adjustments: BagRecipeAdjustmentRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    /** What the saved adjustment is built on: the proposed adjustment, whose other fields are what this brew brewed. */
    private var base: BagRecipeAdjustment? = null
    private val _state = MutableStateFlow(BrewSuggestionState())
    val state: StateFlow<BrewSuggestionState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val brew = brews.get(brewId)
            val found = brew?.let { suggestions.forBrew(it) }
            val bag = brew?.bagId?.let { bags.get(it) }
            val suggestion = found?.suggestion
            if (brew == null || found == null || bag == null ||
                (suggestion !is BrewAdjustmentSuggestion.Suggested && suggestion != BrewAdjustmentSuggestion.OppositeSignals)
            ) {
                _state.update { it.copy(loading = false, closed = true) }
                return@launch
            }
            base = (suggestion as? BrewAdjustmentSuggestion.Suggested)?.adjustment
            val state = BrewSuggestionState(loading = false, brew = brew, recipe = found.recipe, bagName = bag.name, bagNotes = bag.tastingNotes.size)
            val proposed = base
            val brewedGrind = brew.grind?.toIntOrNull()
            val brewedTemperature = brew.waterTempCelsius
            _state.value = when {
                proposed == null -> state
                // Which field moved: the one that no longer equals what the brew recorded (the stored adjustment
                // stands in for brews saved before the values were recorded).
                proposed.grind != null && proposed.grind != (brewedGrind ?: found.current.grind) -> state.copy(
                    field = SuggestedField.GRIND,
                    from = brewedGrind ?: found.current.grind ?: found.recipe.grind?.toIntOrNull() ?: 0,
                    to = proposed.grind ?: 0,
                )
                proposed.waterTempCelsius != null && proposed.waterTempCelsius != (brewedTemperature ?: found.current.waterTempCelsius) -> state.copy(
                    field = SuggestedField.TEMPERATURE,
                    from = brewedTemperature ?: found.current.waterTempCelsius ?: found.recipe.waterTempCelsius ?: 0,
                    to = proposed.waterTempCelsius ?: 0,
                )
                else -> state
            }
        }
    }

    /** Moves the proposed value one step before saving. */
    fun step(by: Int) = _state.update {
        if (it.closed || it.saving) return@update it
        when (it.field) {
            SuggestedField.GRIND -> it.copy(to = (it.to + by).coerceAtLeast(BrewSuggestions.LOWEST_GRIND))
            SuggestedField.TEMPERATURE -> it.copy(to = (it.to + by).coerceIn(BrewLimits.TEMPERATURE_CELSIUS))
            null -> it
        }
    }

    /** "Save for this coffee": stores the value, or nothing where it equals the recipe's, with a history row for this brew. */
    fun save() {
        val state = _state.value
        val recipe = state.recipe ?: return
        val base = base ?: return
        if (state.closed || state.saving) return
        if (state.to == state.from) return
        val adjustment = when (state.field) {
            SuggestedField.GRIND -> base.copy(grind = state.to.takeIf { it != recipe.grind?.trim()?.toIntOrNull() })
            SuggestedField.TEMPERATURE -> base.copy(waterTempCelsius = state.to.takeIf { it != recipe.waterTempCelsius })
            null -> return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            adjustments.save(adjustment, clock(), BagRecipeAdjustmentReason.SUGGESTION, brewId)
            _state.update { it.copy(closed = true) }
        }
    }

    companion object {
        val BREW_ID = object : CreationExtras.Key<String> {}
    }
}
