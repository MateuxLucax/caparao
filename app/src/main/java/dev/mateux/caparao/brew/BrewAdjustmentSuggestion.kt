package dev.mateux.caparao.brew

import dev.mateux.caparao.model.BagRecipeAdjustment
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewTaste
import dev.mateux.caparao.model.Recipe

/**
 * Suggests the next grind or water temperature for a finished, rated brew. Returns the adjusted values to save, or
 * null when the time and taste do not point to a safe change. An on-target balanced brew returns the adjustment with
 * [BagRecipeAdjustment.tuned] set instead of changing a recipe value.
 *
 * The lower grinder number is assumed to be finer (#454). [lowestGrindNumber] is supplied by the caller because
 * grinders use different number ranges.
 */
fun suggestBrewAdjustment(
    recipe: Recipe,
    brew: Brew,
    adjustment: BagRecipeAdjustment,
    lowestGrindNumber: Int,
): BagRecipeAdjustment? {
    if (brew.interrupted) return null
    val min = recipe.targetMinMs ?: return null
    val max = recipe.targetMaxMs ?: return null
    if (min > max) return null
    val taste = brew.taste ?: return null
    val current = adjustment.copy(tuned = false)

    return when {
        brew.durationMs < min && (taste == BrewTaste.SOUR || taste == BrewTaste.BALANCED) -> {
            val grind = adjustment.grind ?: recipe.grind?.toIntOrNull() ?: return null
            val finer = (grind - 1).coerceAtLeast(lowestGrindNumber)
            if (finer == grind) null else current.copy(grind = finer)
        }
        brew.durationMs > max && (taste == BrewTaste.BITTER || taste == BrewTaste.BALANCED) -> {
            val grind = adjustment.grind ?: recipe.grind?.toIntOrNull() ?: return null
            current.copy(grind = grind + 1)
        }
        brew.durationMs in min..max && taste == BrewTaste.SOUR -> {
            val temperature = recipe.waterTempCelsius ?: return null
            val currentTemperature = adjustment.waterTempCelsius ?: temperature
            val hotter = (currentTemperature + 1).coerceAtMost(MAX_WATER_TEMPERATURE_CELSIUS)
            if (hotter == currentTemperature) null else current.copy(waterTempCelsius = hotter)
        }
        brew.durationMs in min..max && taste == BrewTaste.BITTER -> {
            val temperature = recipe.waterTempCelsius ?: return null
            val currentTemperature = adjustment.waterTempCelsius ?: temperature
            val cooler = (currentTemperature - 1).coerceAtLeast(MIN_WATER_TEMPERATURE_CELSIUS)
            if (cooler == currentTemperature) null else current.copy(waterTempCelsius = cooler)
        }
        brew.durationMs in min..max && taste == BrewTaste.BALANCED -> adjustment.copy(tuned = true)
        else -> null
    }
}

private const val MIN_WATER_TEMPERATURE_CELSIUS = 1
private const val MAX_WATER_TEMPERATURE_CELSIUS = 100
