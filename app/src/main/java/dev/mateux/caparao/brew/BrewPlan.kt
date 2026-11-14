package dev.mateux.caparao.brew

import dev.mateux.caparao.model.BagRecipeAdjustment
import dev.mateux.caparao.model.BrewLimits
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.Recipe
import dev.mateux.caparao.model.RecipeStep
import dev.mateux.caparao.model.StepKind

/** The recipe snapshot and amounts for one brew, before its session starts. */
class BrewPlan private constructor(
    val mode: BrewMode,
    /** The stable selected recipe, before scaling water for this brew. */
    val recipe: Recipe?,
    /** This brew's scaled copy, or null until its water is changed. */
    val scaledRecipe: Recipe?,
    val doseGrams: Double,
    val targetRatio: Double,
    val steepMs: Long,
    private val freeDoseGrams: Double,
    private val freeTargetRatio: Double,
    val editedForThisBrew: Boolean,
) {
    val brewRecipe: Recipe? get() = scaledRecipe ?: recipe

    fun withRecipe(recipe: Recipe?, freeDose: Double, freeRatio: Double, freeSteep: Long) =
        create(mode, recipe, freeDose, freeRatio, freeSteep)

    /** A recipe edit applies before brewing unless water has already been changed for this brew. */
    fun withSavedRecipe(recipe: Recipe, beforeBrew: Boolean): BrewPlan {
        if (!beforeBrew || editedForThisBrew || this.recipe?.id != recipe.id) return this
        val current = recipe.withDefaultFrenchPressSteep()
        return BrewPlan(mode, current, null, current.doseGrams, current.targetRatio, steepMs, freeDoseGrams, freeTargetRatio, false)
    }

    /** Scales from the saved recipe each time, so rounding never compounds. */
    fun withWater(waterGrams: Double): BrewPlan? {
        val base = recipe?.takeIf { mode == BrewMode.FILTER || mode == BrewMode.FRENCH_PRESS } ?: return null
        if (waterGrams !in BrewLimits.WATER_GRAMS || waterGrams / base.targetRatio !in BrewLimits.DOSE_GRAMS) return null
        val scaled = scaleRecipeToWater(base, waterGrams)
        return BrewPlan(mode, base, scaled, scaled.doseGrams, scaled.targetRatio, steepMs, freeDoseGrams, freeTargetRatio, true)
    }

    /**
     * This brew with a coffee's adjustment for the chosen recipe (#463): its grind and water temperature replace the
     * recipe's, and its dose scales the water at the recipe's ratio unless the water was already changed for this brew.
     * The selected [recipe] stays as saved, so the adjustment can be dropped again. French press is left as it is.
     */
    fun withAdjustment(adjustment: BagRecipeAdjustment?): BrewPlan {
        val base = recipe?.takeIf { mode != BrewMode.FRENCH_PRESS } ?: return this
        if (adjustment == null || adjustment.recipeId != base.id) return this
        val current = brewRecipe ?: base
        val tuned = current.copy(
            grind = adjustment.grind?.toString() ?: current.grind,
            waterTempCelsius = adjustment.waterTempCelsius ?: current.waterTempCelsius,
        )
        val dose = adjustment.doseGrams?.takeIf { !editedForThisBrew && it in BrewLimits.DOSE_GRAMS }
        val adjusted = when {
            dose == null -> tuned
            mode == BrewMode.ESPRESSO -> tuned.copy(doseGrams = dose)
            else -> scaleRecipeToWater(tuned, dose * tuned.targetRatio)
        }
        return BrewPlan(mode, base, adjusted, dose ?: doseGrams, targetRatio, steepMs, freeDoseGrams, freeTargetRatio, editedForThisBrew)
    }

    fun withDose(grams: Double) = BrewPlan(
        mode, recipe, scaledRecipe, grams, targetRatio, steepMs,
        if (recipe == null) grams else freeDoseGrams, freeTargetRatio, editedForThisBrew,
    )

    fun withRatio(ratio: Double): BrewPlan {
        val clamped = ratio.coerceIn(BrewLimits.RATIO.start, BrewLimits.RATIO.endInclusive)
        return BrewPlan(mode, recipe, scaledRecipe, doseGrams, clamped, steepMs, freeDoseGrams,
            if (recipe == null) clamped else freeTargetRatio, editedForThisBrew)
    }

    fun withSteep(ms: Long): BrewPlan = if (mode == BrewMode.FRENCH_PRESS && recipe == null && ms in BrewLimits.STEEP_MS) {
        BrewPlan(mode, null, null, doseGrams, targetRatio, ms, freeDoseGrams, freeTargetRatio, false)
    } else {
        this
    }

    fun createSession(filterConfig: FilterConfig = FilterConfig()): BrewSession = createBrewSession(this, filterConfig)

    companion object {
        /** Keeps filter free by default; espresso and French press follow their saved choice or first recipe. */
        fun preselected(
            mode: BrewMode,
            recipes: List<Recipe>,
            lastRecipeId: String?,
            freeDose: Double,
            freeRatio: Double,
            freeSteep: Long,
        ): BrewPlan {
            val chosen = if (mode == BrewMode.FILTER) null else
                if (lastRecipeId == null) recipes.firstOrNull() else recipes.firstOrNull { it.id == lastRecipeId }
            return create(mode, chosen, freeDose, freeRatio, freeSteep)
        }

        fun create(
            mode: BrewMode,
            recipe: Recipe?,
            freeDose: Double,
            freeRatio: Double,
            freeSteep: Long,
        ): BrewPlan {
            require(recipe == null || recipe.mode == mode)
            val selected = recipe?.withDefaultFrenchPressSteep()
            return BrewPlan(mode, selected, null, selected?.doseGrams ?: freeDose,
                selected?.targetRatio ?: freeRatio, freeSteep, freeDose, freeRatio, false)
        }
    }
}

private fun Recipe.withDefaultFrenchPressSteep(): Recipe =
    if (mode == BrewMode.FRENCH_PRESS && steps.lastOrNull()?.kind != StepKind.PRESS) {
        copy(steps = steps + RecipeStep(StepKind.PRESS, targetWeightGrams = null, targetAtMs = BrewLimits.DEFAULT_STEEP_MS))
    } else {
        this
    }
