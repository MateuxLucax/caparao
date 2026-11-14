package dev.mateux.caparao.brew

import dev.mateux.caparao.model.BagRecipeAdjustment
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewTaste
import dev.mateux.caparao.model.Recipe
import kotlin.math.abs

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
 *
 * The brew's own grind, water temperature and dose are what it actually brewed, whether they came from the stored
 * adjustment or from the recipe after "Use the recipe" (#463, #468 review). The next value moves from those, and an
 * adjustment the brew did not use is never marked tuned. The target window has no brew-time snapshot: it is read from
 * the live recipe, so a window edited after the brew re-judges the same time. When the recorded grind or temperature
 * no longer agree with either the recipe or the adjustment as they are today, the recipe evidently changed since the
 * brew and no suggestion is made.
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

    val brewedGrind = brew.grind?.toIntOrNull()
    val brewedTemperature = brew.waterTempCelsius
    val recipeGrind = recipe.grind

    // The recorded values must be explainable by the recipe or the adjustment as they are today; otherwise the recipe
    // changed after the brew and its time cannot be judged against the live window.
    val grindAgrees = brewedGrind == null || adjustment.grind == brewedGrind || recipeGrind == brewedGrind
    val temperatureAgrees = brewedTemperature == null || adjustment.waterTempCelsius == brewedTemperature ||
        recipe.waterTempCelsius == brewedTemperature
    if (!grindAgrees || !temperatureAgrees) return BrewAdjustmentSuggestion.NothingToSuggest

    // The stored adjustment was used only when everything it sets is what the brew recorded; "Use the recipe" leaves
    // its values out, so they were never brewed and the adjustment must not be marked tuned. The weighed dose is
    // compared within half a step.
    val usedAdjustment = (adjustment.grind == null || brewedGrind == null || adjustment.grind == brewedGrind) &&
        (adjustment.waterTempCelsius == null || brewedTemperature == null || adjustment.waterTempCelsius == brewedTemperature) &&
        (adjustment.doseGrams == null || abs(adjustment.doseGrams - brew.doseGrams) < 0.5)

    // The next brew starts from what this one brewed; an adjustment the brew did not use contributes nothing, and
    // values that equal the recipe's are left to the recipe.
    val base = if (usedAdjustment) {
        current
    } else {
        BagRecipeAdjustment(
            adjustment.bagId,
            recipe.id,
            grind = brewedGrind?.takeIf { it != recipeGrind },
            waterTempCelsius = brewedTemperature?.takeIf { it != recipe.waterTempCelsius },
            doseGrams = brew.doseGrams.takeIf { it != recipe.doseGrams },
        )
    }
    val grindNow = brewedGrind ?: adjustment.grind ?: recipeGrind

    return when {
        brew.durationMs < min && (taste == BrewTaste.SOUR || taste == BrewTaste.BALANCED) -> {
            val grind = grindNow ?: return BrewAdjustmentSuggestion.NothingToSuggest
            if (grind <= lowestGrindNumber) return BrewAdjustmentSuggestion.NothingToSuggest
            BrewAdjustmentSuggestion.Suggested(base.copy(grind = grind - 1))
        }
        brew.durationMs > max && (taste == BrewTaste.BITTER || taste == BrewTaste.BALANCED) -> {
            val grind = grindNow ?: return BrewAdjustmentSuggestion.NothingToSuggest
            BrewAdjustmentSuggestion.Suggested(base.copy(grind = grind + 1))
        }
        brew.durationMs < min && taste == BrewTaste.BITTER -> BrewAdjustmentSuggestion.OppositeSignals
        brew.durationMs > max && taste == BrewTaste.SOUR -> BrewAdjustmentSuggestion.OppositeSignals
        brew.durationMs in min..max && taste == BrewTaste.SOUR -> {
            recipe.waterTempCelsius ?: return BrewAdjustmentSuggestion.NothingToSuggest
            val temperature = brewedTemperature ?: adjustment.waterTempCelsius ?: recipe.waterTempCelsius
            val hotter = (temperature + 1).coerceAtMost(MAX_WATER_TEMPERATURE_CELSIUS)
            if (hotter == temperature) BrewAdjustmentSuggestion.NothingToSuggest
            else BrewAdjustmentSuggestion.Suggested(base.copy(waterTempCelsius = hotter))
        }
        brew.durationMs in min..max && taste == BrewTaste.BITTER -> {
            recipe.waterTempCelsius ?: return BrewAdjustmentSuggestion.NothingToSuggest
            val temperature = brewedTemperature ?: adjustment.waterTempCelsius ?: recipe.waterTempCelsius
            val cooler = (temperature - 1).coerceAtLeast(MIN_WATER_TEMPERATURE_CELSIUS)
            if (cooler == temperature) BrewAdjustmentSuggestion.NothingToSuggest
            else BrewAdjustmentSuggestion.Suggested(base.copy(waterTempCelsius = cooler))
        }
        brew.durationMs in min..max && taste == BrewTaste.BALANCED ->
            if (usedAdjustment) BrewAdjustmentSuggestion.Tuned else BrewAdjustmentSuggestion.NothingToSuggest
        else -> BrewAdjustmentSuggestion.NothingToSuggest
    }
}

private const val MIN_WATER_TEMPERATURE_CELSIUS = 1
private const val MAX_WATER_TEMPERATURE_CELSIUS = 100
