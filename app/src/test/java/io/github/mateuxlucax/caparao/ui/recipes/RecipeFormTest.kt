package io.github.mateuxlucax.caparao.ui.recipes

import io.github.mateuxlucax.caparao.data.local.BuiltInRecipes
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind
import io.github.mateuxlucax.caparao.ui.recipes.RecipeForm.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class RecipeFormTest {
    private val filter = RecipeForm(
        mode = BrewMode.FILTER, name = "V60", method = "V60", dose = "15", ratio = "16",
        steps = listOf(StepForm(StepKind.BLOOM, "45", "0:00"), StepForm(StepKind.WAIT, "", "0:30"), StepForm(StepKind.POUR, "240", "0:45")),
    )

    @Test
    fun aCompleteFilterFormBecomesARecipe() {
        assertEquals(emptySet<Field>(), filter.validate())
        val recipe = filter.toRecipe("id", createdAt = 1, now = 2)
        assertEquals(
            listOf(RecipeStep(StepKind.BLOOM, 45.0, 0), RecipeStep(StepKind.WAIT, null, 30_000), RecipeStep(StepKind.POUR, 240.0, 45_000)),
            recipe.steps,
        )
        assertEquals(240.0, recipe.targetYieldGrams, 0.0)
        assertNull(recipe.grind)
        assertEquals(1L, recipe.createdAt)
        assertEquals(2L, recipe.updatedAt)
    }

    @Test
    fun filterTimeWindowIsOptionalButMustBeACompleteOrderedPair() {
        assertEquals(emptySet<Field>(), filter.validate())
        assertEquals(setOf(Field.WINDOW), filter.copy(minSeconds = "120").validate())
        assertEquals(setOf(Field.WINDOW), filter.copy(maxSeconds = "150").validate())
        assertEquals(setOf(Field.WINDOW), filter.copy(minSeconds = "bad", maxSeconds = "150").validate())
        assertEquals(setOf(Field.WINDOW), filter.copy(minSeconds = "151", maxSeconds = "150").validate())

        val withRange = filter.copy(minSeconds = "120", maxSeconds = "150")
        assertEquals(emptySet<Field>(), withRange.validate())
        val recipe = withRange.toRecipe("filter", 0, 1)
        assertEquals(120_000L, recipe.targetMinMs)
        assertEquals(150_000L, recipe.targetMaxMs)
        assertEquals(withRange, RecipeForm.of(recipe, Locale.US))
    }

    @Test
    fun optionalFlowTargetIsParsedValidatedAndKeptByTheForm() {
        val targeted = filter.copy(steps = listOf(StepForm(StepKind.BLOOM, "45", "0:00", "5,5")))
        assertEquals(emptySet<Field>(), targeted.validate())
        val recipe = targeted.toRecipe("id", createdAt = 1, now = 2)
        assertEquals(5.5, recipe.steps.single().targetFlowGramsPerSecond!!, 0.0)
        assertEquals(targeted, RecipeForm.of(recipe, Locale.forLanguageTag("pt-BR")))
        assertEquals(setOf(Field.STEPS), targeted.copy(steps = listOf(targeted.steps.single().copy(flowTarget = "0"))).validate())
        assertEquals(setOf(Field.STEPS), targeted.copy(steps = listOf(targeted.steps.single().copy(flowTarget = "fast"))).validate())
    }

    @Test
    fun requiredFieldsAndRanges() {
        assertEquals(
            setOf(Field.NAME, Field.METHOD, Field.DOSE, Field.RATIO, Field.TEMP),
            filter.copy(name = " ", method = "", dose = "0", ratio = "40", temp = "120").validate(),
        )
        assertEquals(emptySet<Field>(), filter.copy(dose = "15,5", ratio = "16.7", temp = "94").validate())
    }

    @Test
    fun stepsMustMoveForwardInTimeAndWeight() {
        fun with(vararg steps: StepForm) = filter.copy(steps = steps.toList()).validate()
        assertEquals(setOf(Field.STEPS), with())
        assertEquals(setOf(Field.STEPS), with(StepForm(StepKind.POUR, "100", "1:00"), StepForm(StepKind.POUR, "200", "0:30")))
        assertEquals(setOf(Field.STEPS), with(StepForm(StepKind.POUR, "100", "0:00"), StepForm(StepKind.POUR, "90", "0:30")))
        assertEquals(setOf(Field.STEPS), with(StepForm(StepKind.POUR, "", "0:00")))
        assertEquals(setOf(Field.STEPS), with(StepForm(StepKind.POUR, "100", "0:75")))
        // A wait needs no weight; plain seconds work too.
        assertEquals(emptySet<Field>(), with(StepForm(StepKind.POUR, "100", "0"), StepForm(StepKind.SWIRL, "", "45"), StepForm(StepKind.POUR, "200", "1:00")))
    }

    @Test
    fun espressoChecksTheWindowAndDropsSteps() {
        val espresso = RecipeForm.new(BrewMode.ESPRESSO).copy(name = "E", dose = "18", ratio = "2", minSeconds = "28", maxSeconds = "34")
        assertEquals(emptySet<Field>(), espresso.validate())
        assertEquals(28_000L, espresso.toRecipe("e", 0, 0).targetMinMs)
        assertEquals(setOf(Field.WINDOW), espresso.copy(minSeconds = "40").validate())
        assertEquals(emptySet<Field>(), espresso.copy(minSeconds = "", maxSeconds = "").validate())
    }

    @Test
    fun frenchPressCanFinishWithATerminalPressAtFourMinutes() {
        val frenchPress = RecipeForm.new(BrewMode.FRENCH_PRESS).copy(
            name = "French press",
            dose = "20",
            ratio = "15",
            steps = listOf(
                StepForm(StepKind.BLOOM, "100", "0:00"),
                StepForm(StepKind.WAIT, start = "0:00"),
                StepForm(StepKind.POUR, "300", "1:00"),
                StepForm(StepKind.PRESS, start = "4:00"),
            ),
        )

        assertEquals(emptySet<Field>(), frenchPress.validate())
        assertEquals(
            listOf(
                RecipeStep(StepKind.BLOOM, 100.0, 0),
                RecipeStep(StepKind.WAIT, null, 0),
                RecipeStep(StepKind.POUR, 300.0, 60_000),
                RecipeStep(StepKind.PRESS, null, 240_000),
            ),
            frenchPress.toRecipe("press", 0, 0).steps,
        )
        assertEquals("French press", RecipeForm.new(BrewMode.FRENCH_PRESS).method)
    }

    @Test
    fun frenchPressRequiresSteepAsItsLastStep() {
        val noSteep = RecipeForm.new(BrewMode.FRENCH_PRESS).copy(
            name = "French press",
            dose = "20",
            ratio = "15",
            steps = listOf(StepForm(StepKind.BLOOM, "100", "0:00"), StepForm(StepKind.POUR, "300", "1:00")),
        )
        assertEquals(setOf(Field.STEPS), noSteep.validate())

        val newForm = RecipeForm.new(BrewMode.FRENCH_PRESS).copy(name = "French press", dose = "20", ratio = "15")
        assertEquals(StepForm(StepKind.PRESS, start = "4:00"), newForm.steps.last())
    }

    @Test
    fun anOlderFrenchPressRecipeGetsTheDefaultSteepWhenOpened() {
        val oldRecipe = BuiltInRecipes.frenchPress(0).copy(steps = BuiltInRecipes.frenchPress(0).steps.dropLast(1))

        val form = RecipeForm.of(oldRecipe, Locale.US)

        assertEquals(StepForm(StepKind.PRESS, start = "4:00"), form.steps.last())
        assertEquals(3, oldRecipe.steps.size)
    }

    @Test
    fun pressStepMustBeTerminalAndFrenchPressOnly() {
        val press = StepForm(StepKind.PRESS, start = "4:00")
        val laterPour = StepForm(StepKind.POUR, "300", "4:30")
        assertEquals(setOf(Field.STEPS), filter.copy(steps = listOf(filter.steps.first(), press)).validate())
        assertEquals(
            setOf(Field.STEPS),
            RecipeForm.new(BrewMode.FRENCH_PRESS).copy(
                name = "Press",
                dose = "20",
                ratio = "15",
                steps = listOf(StepForm(StepKind.BLOOM, "100", "0:00"), press, laterPour),
            ).validate(),
        )
    }

    @Test
    fun aRecipeRoundTripsThroughTheForm() {
        for (recipe in BuiltInRecipes.all(5)) {
            val form = RecipeForm.of(recipe, Locale.US)
            assertEquals(emptySet<Field>(), form.validate())
            assertEquals(recipe, form.toRecipe(recipe.id, 5, 5))
        }
        assertEquals("16,7", RecipeForm.of(BuiltInRecipes.all(0)[1], Locale.forLanguageTag("pt-BR")).ratio)
    }
}
