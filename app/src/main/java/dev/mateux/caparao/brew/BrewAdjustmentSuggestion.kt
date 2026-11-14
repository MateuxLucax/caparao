package dev.mateux.caparao.brew

import dev.mateux.caparao.model.BagRecipeAdjustment
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewTaste
import dev.mateux.caparao.model.Recipe

sealed interface BrewAdjustmentSuggestion {
    data class Suggested(val adjustment: BagRecipeAdjustment) : BrewAdjustmentSuggestion
    object Tuned : BrewAdjustmentSuggestion
    object OppositeSignals : BrewAdjustmentSuggestion
    object NothingToSuggest : BrewAdjustmentSuggestion
}

/**
 * Suggests the next grind or water temperature for a finished, rated brew. Opposing time and taste signals are kept
 * distinct from cases where there is not enough information to suggest a change. An on-target balanced brew is tuned.
 *
 * The lower grinder number is assumed to be finer (#454). [lowestGrindNumber] is supplied by the caller because
 * grinders use different number ranges.
 */
fun suggestBrewAdjustment(
    recipe: Recipe,
    brew: Brew,
    adjustment: BagRecipeAdjustment,
    lowestGrindNumber: Int,
): BrewAdjustmentSuggestion {
    require(adjustment.recipeId == recipe.id) { "Adjustment belongs to a different recipe" }

    if (brew.interrupted) return BrewAdjustmentSuggestion.NothingToSuggest
    val min = recipe.targetMinMs ?: return BrewAdjustmentSuggestion.NothingToSuggest
    val max = recipe.targetMaxMs ?: return BrewAdjustmentSuggestion.NothingToSuggest
    if (min > max) return BrewAdjustmentSuggestion.NothingToSuggest
    val taste = brew.taste ?: return BrewAdjustmentSuggestion.NothingToSuggest
    val current = adjustment.copy(tuned = false)

    return when {
        brew.durationMs < min && (taste == BrewTaste.SOUR || taste == BrewTaste.BALANCED) -> {
            val grind = adjustment.grind ?: recipe.grind?.toIntOrNull()
                ?: return BrewAdjustmentSuggestion.NothingToSuggest
            if (grind <= lowestGrindNumber) return BrewAdjustmentSuggestion.NothingToSuggest
            BrewAdjustmentSuggestion.Suggested(current.copy(grind = grind - 1))
        }
        brew.durationMs > max && (taste == BrewTaste.BITTER || taste == BrewTaste.BALANCED) -> {
            val grind = adjustment.grind ?: recipe.grind?.toIntOrNull()
                ?: return BrewAdjustmentSuggestion.NothingToSuggest
            BrewAdjustmentSuggestion.Suggested(current.copy(grind = grind + 1))
        }
        brew.durationMs < min && taste == BrewTaste.BITTER -> BrewAdjustmentSuggestion.OppositeSignals
        brew.durationMs > max && taste == BrewTaste.SOUR -> BrewAdjustmentSuggestion.OppositeSignals
        brew.durationMs in min..max && taste == BrewTaste.SOUR -> {
            val temperature = recipe.waterTempCelsius ?: return BrewAdjustmentSuggestion.NothingToSuggest
            val currentTemperature = adjustment.waterTempCelsius ?: temperature
            val hotter = (currentTemperature + 1).coerceAtMost(MAX_WATER_TEMPERATURE_CELSIUS)
            if (hotter == currentTemperature) BrewAdjustmentSuggestion.NothingToSuggest
            else BrewAdjustmentSuggestion.Suggested(current.copy(waterTempCelsius = hotter))
        }
        brew.durationMs in min..max && taste == BrewTaste.BITTER -> {
            val temperature = recipe.waterTempCelsius ?: return BrewAdjustmentSuggestion.NothingToSuggest
            val currentTemperature = adjustment.waterTempCelsius ?: temperature
            val cooler = (currentTemperature - 1).coerceAtLeast(MIN_WATER_TEMPERATURE_CELSIUS)
            if (cooler == currentTemperature) BrewAdjustmentSuggestion.NothingToSuggest
            else BrewAdjustmentSuggestion.Suggested(current.copy(waterTempCelsius = cooler))
        }
        brew.durationMs in min..max && taste == BrewTaste.BALANCED -> BrewAdjustmentSuggestion.Tuned
        else -> BrewAdjustmentSuggestion.NothingToSuggest
    }
}

private const val MIN_WATER_TEMPERATURE_CELSIUS = 1
private const val MAX_WATER_TEMPERATURE_CELSIUS = 100
