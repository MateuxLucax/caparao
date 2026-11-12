package dev.mateux.caparao.brew

import dev.mateux.caparao.model.BrewLimits
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.Recipe
import dev.mateux.caparao.model.RecipeStep
import dev.mateux.caparao.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewPlanTest {
    private val espresso = recipe("espresso", BrewMode.ESPRESSO)
    private val filter = recipe("filter", BrewMode.FILTER)

    @Test
    fun preselectsFilterFreeAndOtherModesBySavedChoiceOrFirstRecipe() {
        val recipes = listOf(espresso, filter)

        assertNull(BrewPlan.preselected(BrewMode.FILTER, recipes, espresso.id, 20.0, 16.0, BrewLimits.DEFAULT_STEEP_MS).recipe)
        assertEquals(espresso, BrewPlan.preselected(BrewMode.ESPRESSO, recipes, espresso.id, 20.0, 16.0, BrewLimits.DEFAULT_STEEP_MS).recipe)
        assertEquals(espresso, BrewPlan.preselected(BrewMode.ESPRESSO, recipes, null, 20.0, 16.0, BrewLimits.DEFAULT_STEEP_MS).recipe)
        assertNull(BrewPlan.preselected(BrewMode.ESPRESSO, recipes, "deleted", 20.0, 16.0, BrewLimits.DEFAULT_STEEP_MS).recipe)
    }

    @Test
    fun repeatedWaterChangesScaleFromTheSavedRecipe() {
        val small = filter.copy(
            doseGrams = 7.5,
            targetRatio = 4.0,
            steps = listOf(RecipeStep(StepKind.BLOOM, 10.0, 0), RecipeStep(StepKind.POUR, 20.0, 1_000), RecipeStep(StepKind.POUR, 30.0, 2_000)),
        )
        val plan = BrewPlan.create(BrewMode.FILTER, small, 7.5, 16.0, BrewLimits.DEFAULT_STEEP_MS)
            .withWater(15.0)!!
            .withWater(12.0)!!

        assertEquals(listOf(4.0, 8.0, 12.0), plan.scaledRecipe!!.steps.map { it.targetWeightGrams })
        assertEquals(3.0, plan.doseGrams, 0.0)
    }

    @Test
    fun savedRecipeEditsApplyBeforeBrewingButNotAfterOrAfterThisBrewIsEdited() {
        val changed = filter.copy(doseGrams = 25.0, targetRatio = 18.0)
        val before = BrewPlan.create(BrewMode.FILTER, filter, 20.0, 16.0, BrewLimits.DEFAULT_STEEP_MS)
            .withSavedRecipe(changed, beforeBrew = true)
        val after = before.withSavedRecipe(changed.copy(doseGrams = 30.0), beforeBrew = false)
        val scaled = before.withWater(450.0)!!
        val whileEdited = scaled.withSavedRecipe(changed.copy(doseGrams = 30.0), beforeBrew = true)

        assertEquals(25.0, before.doseGrams, 0.0)
        assertSame(before, after)
        assertSame(scaled, whileEdited)
        assertTrue(scaled.editedForThisBrew)
    }

    @Test
    fun sessionUsesThePlanForFreeFrenchPressWaterAndSteep() {
        val plan = BrewPlan.create(BrewMode.FRENCH_PRESS, null, 20.0, 15.0, 60_000L)
        val session = BrewSession(plan)

        assertTrue(session is FrenchPressSession)
        assertEquals(listOf(300.0, 60_000.0), session.guideSteps.map { it.targetWeightGrams ?: it.targetAtMs.toDouble() })
    }

    private fun recipe(id: String, mode: BrewMode) = Recipe(
        id = id,
        name = id,
        mode = mode,
        method = id,
        doseGrams = 20.0,
        targetRatio = 2.0,
        steps = if (mode == BrewMode.ESPRESSO) emptyList() else listOf(RecipeStep(StepKind.POUR, 40.0, 0)),
        createdAt = 0,
        updatedAt = 0,
    )
}
