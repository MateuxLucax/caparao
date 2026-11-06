package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.data.local.BuiltInRecipes
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Test

class FrenchPressRecipeTest {
    @Test
    fun anOlderFrenchPressRecipeGetsAFourMinuteSteepForBrewing() {
        val oldRecipe = BuiltInRecipes.frenchPress(0).copy(steps = BuiltInRecipes.frenchPress(0).steps.dropLast(1))

        val brewed = oldRecipe.withDefaultFrenchPressSteep()

        assertEquals(RecipeStep(StepKind.PRESS, null, 240_000), brewed.steps.last())
        assertEquals(3, oldRecipe.steps.size)
    }

    @Test
    fun anExplicitFrenchPressSteepIsKept() {
        val recipe = BuiltInRecipes.frenchPress(0)

        assertEquals(recipe, recipe.withDefaultFrenchPressSteep())
    }
}
