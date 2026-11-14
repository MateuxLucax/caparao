package dev.mateux.caparao.brew

import dev.mateux.caparao.model.BagRecipeAdjustment
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.BrewTaste
import dev.mateux.caparao.model.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BrewAdjustmentSuggestionTest {
    private val recipe = Recipe(
        id = "recipe",
        name = "V60",
        mode = BrewMode.FILTER,
        method = "V60",
        doseGrams = 15.0,
        targetRatio = 16.0,
        grind = 10,
        waterTempCelsius = 90,
        targetMinMs = 100_000,
        targetMaxMs = 120_000,
        createdAt = 0,
        updatedAt = 0,
    )
    private val adjustment = BagRecipeAdjustment("bag", "recipe", grind = 10, waterTempCelsius = 90)

    @Test
    fun suggestsEachTableRow() {
        data class Case(
            val name: String,
            val durationMs: Long,
            val taste: BrewTaste,
            val expected: BrewAdjustmentSuggestion,
            val recipe: Recipe = this@BrewAdjustmentSuggestionTest.recipe,
        )

        val cases = listOf(
            Case("under target and sour", 99_000, BrewTaste.SOUR, suggested(adjustment.copy(grind = 9))),
            Case("under target and balanced", 99_000, BrewTaste.BALANCED, suggested(adjustment.copy(grind = 9))),
            Case("over target and bitter", 121_000, BrewTaste.BITTER, suggested(adjustment.copy(grind = 11))),
            Case("over target and balanced", 121_000, BrewTaste.BALANCED, suggested(adjustment.copy(grind = 11))),
            Case("on target and sour", 110_000, BrewTaste.SOUR, suggested(adjustment.copy(waterTempCelsius = 91))),
            Case("on target and bitter", 110_000, BrewTaste.BITTER, suggested(adjustment.copy(waterTempCelsius = 89))),
            Case("on target and balanced", 110_000, BrewTaste.BALANCED, BrewAdjustmentSuggestion.Tuned),
            Case("at lower target boundary", 100_000, BrewTaste.SOUR, suggested(adjustment.copy(waterTempCelsius = 91))),
            Case("at upper target boundary", 120_000, BrewTaste.BITTER, suggested(adjustment.copy(waterTempCelsius = 89))),
            Case(
                "espresso uses its target window",
                99_000,
                BrewTaste.SOUR,
                suggested(adjustment.copy(grind = 9)),
                recipe.copy(mode = BrewMode.ESPRESSO),
            ),
        )

        cases.forEach { case ->
            assertEquals(
                case.name,
                case.expected,
                suggestBrewAdjustment(
                    case.recipe,
                    brew(case.durationMs, case.taste, mode = case.recipe.mode),
                    adjustment,
                    lowestGrindNumber = 1,
                ),
            )
        }
    }

    @Test
    fun returnsNoneForEveryUnsupportedOrOpposingCase() {
        data class Case(
            val name: String,
            val brew: Brew,
            val recipe: Recipe = this@BrewAdjustmentSuggestionTest.recipe,
            val adjustment: BagRecipeAdjustment = this@BrewAdjustmentSuggestionTest.adjustment,
            val lowestGrindNumber: Int = 1,
            val expected: BrewAdjustmentSuggestion = BrewAdjustmentSuggestion.NothingToSuggest,
        )

        val cases = listOf(
            Case(
                "fast and bitter",
                brew(99_000, BrewTaste.BITTER),
                expected = BrewAdjustmentSuggestion.OppositeSignals,
            ),
            Case(
                "slow and sour",
                brew(121_000, BrewTaste.SOUR),
                expected = BrewAdjustmentSuggestion.OppositeSignals,
            ),
            Case("missing lower time target", brew(110_000, BrewTaste.SOUR), recipe.copy(targetMinMs = null)),
            Case("missing upper time target", brew(110_000, BrewTaste.SOUR), recipe.copy(targetMaxMs = null)),
            Case("reversed time window", brew(110_000, BrewTaste.SOUR), recipe.copy(targetMinMs = 121_000)),
            Case("unrated brew", brew = brew(110_000, null)),
            Case("interrupted brew", brew = brew(110_000, BrewTaste.SOUR, interrupted = true)),
            Case(
                "temperature suggestion without recipe temperature",
                brew = brew(110_000, BrewTaste.SOUR),
                recipe = recipe.copy(waterTempCelsius = null),
            ),
            Case(
                "cooler suggestion without recipe temperature",
                brew = brew(110_000, BrewTaste.BITTER),
                recipe = recipe.copy(waterTempCelsius = null),
            ),
            Case(
                "temperature suggestion above maximum",
                brew = brew(110_000, BrewTaste.SOUR),
                adjustment = adjustment.copy(waterTempCelsius = 100),
            ),
            Case(
                "temperature suggestion below minimum",
                brew = brew(110_000, BrewTaste.BITTER),
                adjustment = adjustment.copy(waterTempCelsius = 1),
            ),
            Case(
                "finer grind suggestion at grinder minimum",
                brew = brew(99_000, BrewTaste.SOUR),
                adjustment = adjustment.copy(grind = 1),
            ),
            Case(
                "grind below grinder minimum with minimum above one",
                brew = brew(99_000, BrewTaste.SOUR),
                adjustment = adjustment.copy(grind = 3),
                lowestGrindNumber = 5,
            ),
            Case(
                "grind suggestion without a numeric grind",
                brew = brew(99_000, BrewTaste.SOUR),
                recipe = recipe.copy(grind = null),
                adjustment = adjustment.copy(grind = null),
            ),
        )

        cases.forEach { case ->
            assertEquals(
                case.name,
                case.expected,
                suggestBrewAdjustment(
                    case.recipe,
                    case.brew,
                    case.adjustment,
                    lowestGrindNumber = case.lowestGrindNumber,
                ),
            )
        }
    }

    @Test
    fun usesRecipeValuesWhenAdjustmentDoesNotOverrideThem() {
        val noOverrides = BagRecipeAdjustment("bag", "recipe")

        assertEquals(
            suggested(noOverrides.copy(grind = 9)),
            suggestBrewAdjustment(recipe, brew(99_000, BrewTaste.SOUR), noOverrides, lowestGrindNumber = 1),
        )
        assertEquals(
            suggested(noOverrides.copy(waterTempCelsius = 91)),
            suggestBrewAdjustment(recipe, brew(110_000, BrewTaste.SOUR), noOverrides, lowestGrindNumber = 1),
        )
    }

    @Test
    fun rejectsAdjustmentForAnotherRecipe() {
        assertThrows(IllegalArgumentException::class.java) {
            suggestBrewAdjustment(recipe, brew(110_000, BrewTaste.SOUR), adjustment.copy(recipeId = "other"), 1)
        }
    }

    @Test
    fun anchorsSuggestionsOnWhatTheBrewRecorded() {
        // The stored adjustment says 9, 85 °C and 18 g, but the brew followed the recipe (10, 90 °C, 15 g).
        val stored = BagRecipeAdjustment("bag", "recipe", grind = 9, waterTempCelsius = 85, doseGrams = 18.0)
        val followed = brew(99_000, BrewTaste.SOUR, grind = "10", waterTempCelsius = 90, doseGrams = 15.0)

        // The next grind moves from the recorded 10, and the unused values are not carried over.
        assertEquals(
            suggested(BagRecipeAdjustment("bag", "recipe", grind = 9)),
            suggestBrewAdjustment(recipe, followed, stored, lowestGrindNumber = 1),
        )
        // On target and sour, the water moves from the recorded 90, not from the stored 85.
        assertEquals(
            suggested(BagRecipeAdjustment("bag", "recipe", waterTempCelsius = 91)),
            suggestBrewAdjustment(
                recipe,
                brew(110_000, BrewTaste.SOUR, grind = "10", waterTempCelsius = 90, doseGrams = 15.0),
                stored,
                lowestGrindNumber = 1,
            ),
        )
    }

    @Test
    fun doesNotMarkAnAdjustmentTheBrewDidNotUseAsTuned() {
        val stored = BagRecipeAdjustment("bag", "recipe", grind = 9, waterTempCelsius = 91, doseGrams = 18.0)
        val followed = brew(110_000, BrewTaste.BALANCED, grind = "10", waterTempCelsius = 90, doseGrams = 15.0)
        assertEquals(BrewAdjustmentSuggestion.NothingToSuggest, suggestBrewAdjustment(recipe, followed, stored, 1))
    }

    @Test
    fun marksTheAdjustmentTunedWhenTheBrewUsedIt() {
        val stored = BagRecipeAdjustment("bag", "recipe", grind = 9, waterTempCelsius = 91, doseGrams = 18.0)
        val brewed = brew(110_000, BrewTaste.BALANCED, grind = "9", waterTempCelsius = 91, doseGrams = 18.2)
        assertEquals(BrewAdjustmentSuggestion.Tuned, suggestBrewAdjustment(recipe, brewed, stored, 1))
        // A suggestion moves from the recorded values and keeps the used adjustment as its base.
        assertEquals(
            suggested(stored.copy(grind = 8)),
            suggestBrewAdjustment(
                recipe,
                brew(99_000, BrewTaste.SOUR, grind = "9", waterTempCelsius = 91, doseGrams = 18.2),
                stored,
                lowestGrindNumber = 1,
            ),
        )
    }

    @Test
    fun skipsSuggestionsWhenTheRecipeNoLongerExplainsTheBrew() {
        // The brew recorded 10 and 90 °C; the recipe has since been edited to 12 and 85 °C.
        val edited = recipe.copy(grind = 12, waterTempCelsius = 85, targetMinMs = 120_000, targetMaxMs = 150_000)
        val old = brew(99_000, BrewTaste.SOUR, grind = "10", waterTempCelsius = 90)
        assertEquals(
            BrewAdjustmentSuggestion.NothingToSuggest,
            suggestBrewAdjustment(edited, old, BagRecipeAdjustment("bag", "recipe"), 1),
        )
        // A changed temperature alone is enough to stop judging the old brew.
        val oldWithoutGrind = brew(99_000, BrewTaste.SOUR, waterTempCelsius = 90)
        assertEquals(
            BrewAdjustmentSuggestion.NothingToSuggest,
            suggestBrewAdjustment(edited.copy(grind = null), oldWithoutGrind, BagRecipeAdjustment("bag", "recipe"), 1),
        )
    }

    private fun suggested(adjustment: BagRecipeAdjustment) = BrewAdjustmentSuggestion.Suggested(adjustment)

    private fun brew(
        durationMs: Long,
        taste: BrewTaste?,
        interrupted: Boolean = false,
        mode: BrewMode = BrewMode.FILTER,
        grind: String? = null,
        waterTempCelsius: Int? = null,
        doseGrams: Double = 15.0,
    ) = Brew(
        id = "brew",
        mode = mode,
        startedAt = 0,
        durationMs = durationMs,
        doseGrams = doseGrams,
        yieldGrams = 240.0,
        timeToFirstDropMs = null,
        flowAvgGramsPerSecond = 2.0,
        flowPeakGramsPerSecond = 5.0,
        interrupted = interrupted,
        taste = taste,
        grind = grind,
        waterTempCelsius = waterTempCelsius,
    )
}
