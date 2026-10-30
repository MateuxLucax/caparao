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
    fun aRecipeRoundTripsThroughTheForm() {
        for (recipe in BuiltInRecipes.all(5)) {
            val form = RecipeForm.of(recipe, Locale.US)
            assertEquals(emptySet<Field>(), form.validate())
            assertEquals(recipe, form.toRecipe(recipe.id, 5, 5))
        }
        assertEquals("16,7", RecipeForm.of(BuiltInRecipes.all(0)[1], Locale.forLanguageTag("pt-BR")).ratio)
    }
}
