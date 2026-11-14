package dev.mateux.caparao.ui.brews

import dev.mateux.caparao.brew.BrewAdjustmentSuggestion
import dev.mateux.caparao.brew.suggestBrewAdjustment
import dev.mateux.caparao.data.local.BagRecipeAdjustmentRepository
import dev.mateux.caparao.data.local.RecipeRepository
import dev.mateux.caparao.model.BagRecipeAdjustment
import dev.mateux.caparao.model.BagRecipeAdjustmentReason
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.Recipe

/** What follows a saved rating (#468). */
enum class RatingNext {
    /** Nothing to say: the screen closes. */
    CLOSE,

    /** A change to propose, or opposite signals to show: the Suggestion screen opens. */
    SUGGEST,

    /** On target and balanced: the adjustment was marked tuned; a short confirmation, no screen. */
    TUNED,
}

/** Works out what a rated brew suggests for its coffee and recipe (#467, #468). */
class BrewSuggestions(
    private val recipes: RecipeRepository,
    private val adjustments: BagRecipeAdjustmentRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** [current] is the stored adjustment, or an empty one that follows the recipe. */
    data class Found(val recipe: Recipe, val current: BagRecipeAdjustment, val suggestion: BrewAdjustmentSuggestion)

    /** Null without a bag, or without a recipe that still exists. */
    suspend fun forBrew(brew: Brew): Found? {
        val bagId = brew.bagId ?: return null
        val recipe = brew.recipeId?.let { recipes.get(it) } ?: return null
        val current = adjustments.get(bagId, recipe.id) ?: BagRecipeAdjustment(bagId, recipe.id)
        return Found(recipe, current, suggestBrewAdjustment(recipe, brew, current, LOWEST_GRIND))
    }

    /** Marks the adjustment tuned when the brew landed on target and balanced; otherwise only says what to show. */
    suspend fun afterRating(brew: Brew): RatingNext {
        val found = forBrew(brew) ?: return RatingNext.CLOSE
        return when (found.suggestion) {
            is BrewAdjustmentSuggestion.Suggested, BrewAdjustmentSuggestion.OppositeSignals -> RatingNext.SUGGEST
            BrewAdjustmentSuggestion.Tuned -> {
                if (!found.current.tuned) {
                    adjustments.save(found.current.copy(tuned = true), clock(), BagRecipeAdjustmentReason.SUGGESTION, brew.id)
                }
                RatingNext.TUNED
            }
            BrewAdjustmentSuggestion.NothingToSuggest -> RatingNext.CLOSE
        }
    }

    companion object {
        /** The finest grinder number the adjustment screen allows (#462). */
        const val LOWEST_GRIND = 1
    }
}
