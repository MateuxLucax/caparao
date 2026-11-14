package dev.mateux.caparao.brew

import dev.mateux.caparao.model.BagRecipeAdjustment
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.BrewTaste
import dev.mateux.caparao.model.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrewAdjustmentSuggestionTest {
    private val recipe = Recipe(
        id = "recipe",
        name = "V60",
        mode = BrewMode.FILTER,
        method = "V60",
        doseGrams = 15.0,
        targetRatio = 16.0,
        grind = "10",
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
            val expected: BagRecipeAdjustment,
            val recipe: Recipe = this@BrewAdjustmentSuggestionTest.recipe,
        )

        val cases = listOf(
            Case("under target and sour", 99_000, BrewTaste.SOUR, adjustment.copy(grind = 9)),
            Case("under target and balanced", 99_000, BrewTaste.BALANCED, adjustment.copy(grind = 9)),
            Case("over target and bitter", 121_000, BrewTaste.BITTER, adjustment.copy(grind = 11)),
            Case("over target and balanced", 121_000, BrewTaste.BALANCED, adjustment.copy(grind = 11)),
            Case("on target and sour", 110_000, BrewTaste.SOUR, adjustment.copy(waterTempCelsius = 91)),
            Case("on target and bitter", 110_000, BrewTaste.BITTER, adjustment.copy(waterTempCelsius = 89)),
            Case("on target and balanced", 110_000, BrewTaste.BALANCED, adjustment.copy(tuned = true)),
            Case("at lower target boundary", 100_000, BrewTaste.SOUR, adjustment.copy(waterTempCelsius = 91)),
            Case("at upper target boundary", 120_000, BrewTaste.BITTER, adjustment.copy(waterTempCelsius = 89)),
            Case(
                "espresso uses its target window",
                99_000,
                BrewTaste.SOUR,
                adjustment.copy(grind = 9),
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
        )

        val cases = listOf(
            Case("fast and bitter", brew(99_000, BrewTaste.BITTER)),
            Case("slow and sour", brew(121_000, BrewTaste.SOUR)),
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
                "grind suggestion without a numeric grind",
                brew = brew(99_000, BrewTaste.SOUR),
                recipe = recipe.copy(grind = "medium-coarse"),
                adjustment = adjustment.copy(grind = null),
            ),
        )

        cases.forEach { case ->
            assertNull(
                case.name,
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
            noOverrides.copy(grind = 9),
            suggestBrewAdjustment(recipe, brew(99_000, BrewTaste.SOUR), noOverrides, lowestGrindNumber = 1),
        )
        assertEquals(
            noOverrides.copy(waterTempCelsius = 91),
            suggestBrewAdjustment(recipe, brew(110_000, BrewTaste.SOUR), noOverrides, lowestGrindNumber = 1),
        )
    }

    private fun brew(
        durationMs: Long,
        taste: BrewTaste?,
        interrupted: Boolean = false,
        mode: BrewMode = BrewMode.FILTER,
    ) = Brew(
        id = "brew",
        mode = mode,
        startedAt = 0,
        durationMs = durationMs,
        doseGrams = 15.0,
        yieldGrams = 240.0,
        timeToFirstDropMs = null,
        flowAvgGramsPerSecond = 2.0,
        flowPeakGramsPerSecond = 5.0,
        interrupted = interrupted,
        taste = taste,
    )
}
