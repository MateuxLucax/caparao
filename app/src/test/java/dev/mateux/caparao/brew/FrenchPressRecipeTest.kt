package dev.mateux.caparao.brew

import dev.mateux.caparao.data.local.BuiltInRecipes
import dev.mateux.caparao.model.BrewLimits
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.RecipeStep
import dev.mateux.caparao.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Test

class FrenchPressRecipeTest {
    @Test
    fun anOlderFrenchPressRecipeGetsAFourMinuteSteepForBrewing() {
        val oldRecipe = BuiltInRecipes.frenchPress(0).copy(steps = BuiltInRecipes.frenchPress(0).steps.dropLast(1))

        val brewed = BrewPlan.create(BrewMode.FRENCH_PRESS, oldRecipe, 20.0, 15.0, BrewLimits.DEFAULT_STEEP_MS).brewRecipe!!

        assertEquals(RecipeStep(StepKind.PRESS, null, BrewLimits.DEFAULT_STEEP_MS), brewed.steps.last())
        assertEquals(3, oldRecipe.steps.size)
    }

    @Test
    fun anExplicitFrenchPressSteepIsKept() {
        val recipe = BuiltInRecipes.frenchPress(0)

        assertEquals(recipe, BrewPlan.create(BrewMode.FRENCH_PRESS, recipe, 20.0, 15.0, BrewLimits.DEFAULT_STEEP_MS).brewRecipe)
    }
}
