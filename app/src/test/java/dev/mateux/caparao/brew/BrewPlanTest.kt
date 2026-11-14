package dev.mateux.caparao.brew

import dev.mateux.caparao.model.BagRecipeAdjustment
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
    fun anAdjustmentReplacesGrindAndTemperatureAndScalesTheWaterToItsDose() {
        val saved = filter.copy(
            doseGrams = 20.0, targetRatio = 15.0, grind = "24", waterTempCelsius = 94,
            steps = listOf(RecipeStep(StepKind.BLOOM, 40.0, 0), RecipeStep(StepKind.POUR, 300.0, 30_000)),
        )
        val plan = BrewPlan.create(BrewMode.FILTER, saved, 15.0, 16.0, BrewLimits.DEFAULT_STEEP_MS)
            .withAdjustment(BagRecipeAdjustment("bag", saved.id, grind = 23, waterTempCelsius = 93, doseGrams = 21.0))

        assertEquals(21.0, plan.doseGrams, 0.0)
        assertEquals("23", plan.brewRecipe!!.grind)
        assertEquals(93, plan.brewRecipe!!.waterTempCelsius)
        assertEquals(listOf(42.0, 315.0), plan.brewRecipe!!.steps.map { it.targetWeightGrams })
        // The selected recipe stays as saved, so the adjustment can be dropped again.
        assertEquals(saved, plan.recipe)
    }

    @Test
    fun anAdjustmentKeepsWhatItDoesNotChangeAndAWaterChosenForThisBrew() {
        val saved = filter.copy(doseGrams = 20.0, targetRatio = 15.0, grind = "24", waterTempCelsius = 94)
        val plan = BrewPlan.create(BrewMode.FILTER, saved, 15.0, 16.0, BrewLimits.DEFAULT_STEEP_MS)

        val grindOnly = plan.withAdjustment(BagRecipeAdjustment("bag", saved.id, grind = 23))
        assertEquals(20.0, grindOnly.doseGrams, 0.0)
        assertEquals(94, grindOnly.brewRecipe!!.waterTempCelsius)

        val edited = plan.withWater(240.0)!!.withAdjustment(BagRecipeAdjustment("bag", saved.id, grind = 23, doseGrams = 21.0))
        assertEquals(16.0, edited.doseGrams, 0.0)
        assertEquals("23", edited.brewRecipe!!.grind)
    }

    @Test
    fun anAdjustmentDoseThatWouldNeedWaterBeyondTheScaleIsLeftOut() {
        val saved = filter.copy(
            doseGrams = 20.0, targetRatio = 30.0, grind = "24", waterTempCelsius = 94,
            steps = listOf(RecipeStep(StepKind.BLOOM, 40.0, 0), RecipeStep(StepKind.POUR, 600.0, 30_000)),
        )
        val plan = BrewPlan.create(BrewMode.FILTER, saved, 15.0, 16.0, BrewLimits.DEFAULT_STEEP_MS)

        // 67 g × 30 is 2010 g of water, beyond the scale: the brew keeps the recipe's dose and targets, consistently.
        val kept = plan.withAdjustment(BagRecipeAdjustment("bag", saved.id, grind = 23, doseGrams = 67.0))
        assertEquals(20.0, kept.doseGrams, 0.0)
        assertEquals("23", kept.brewRecipe!!.grind)
        assertEquals(listOf(40.0, 600.0), kept.brewRecipe!!.steps.map { it.targetWeightGrams })

        // 66 g × 30 is 1980 g of water: within the scale, so the dose scales the water as usual.
        val applied = plan.withAdjustment(BagRecipeAdjustment("bag", saved.id, doseGrams = 66.0))
        assertEquals(66.0, applied.doseGrams, 0.0)
        assertEquals(listOf(132.0, 1980.0), applied.brewRecipe!!.steps.map { it.targetWeightGrams })
    }

    @Test
    fun anAdjustmentIsLeftOutWithoutARecipeForAnotherRecipeAndForFrenchPress() {
        val free = BrewPlan.create(BrewMode.FILTER, null, 15.0, 16.0, BrewLimits.DEFAULT_STEEP_MS)
        assertSame(free, free.withAdjustment(BagRecipeAdjustment("bag", "any", grind = 23)))

        val plan = BrewPlan.create(BrewMode.ESPRESSO, espresso, 18.0, 2.0, BrewLimits.DEFAULT_STEEP_MS)
        assertSame(plan, plan.withAdjustment(null))
        assertSame(plan, plan.withAdjustment(BagRecipeAdjustment("bag", "other", grind = 9)))
        val adjusted = plan.withAdjustment(BagRecipeAdjustment("bag", espresso.id, doseGrams = 18.5))
        assertEquals(18.5, adjusted.doseGrams, 0.0)
        assertEquals(18.5, adjusted.brewRecipe!!.doseGrams, 0.0)

        val press = recipe("press", BrewMode.FRENCH_PRESS)
        val pressPlan = BrewPlan.create(BrewMode.FRENCH_PRESS, press, 20.0, 15.0, BrewLimits.DEFAULT_STEEP_MS)
        assertSame(pressPlan, pressPlan.withAdjustment(BagRecipeAdjustment("bag", press.id, grind = 30)))
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
