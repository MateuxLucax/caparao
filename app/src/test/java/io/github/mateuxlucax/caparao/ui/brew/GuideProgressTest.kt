package io.github.mateuxlucax.caparao.ui.brew

import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Test

class GuideProgressTest {
    private val frenchPressSteps = listOf(
        RecipeStep(StepKind.BLOOM, 100.0, 0),
        RecipeStep(StepKind.WAIT, null, 60_000),
        RecipeStep(StepKind.POUR, 300.0, 60_000),
        RecipeStep(StepKind.PRESS, null, 240_000),
    )

    @Test
    fun frenchPressLineTracksElapsedTimeAgainstTheTerminalPressTarget() {
        assertEquals(0f, guideProgress(BrewMode.FRENCH_PRESS, frenchPressSteps, currentIndex = 0, elapsedMs = 0), 0.001f)
        assertEquals(0.325f, guideProgress(BrewMode.FRENCH_PRESS, frenchPressSteps, currentIndex = 3, elapsedMs = 78_000), 0.001f)
        assertEquals(1f, guideProgress(BrewMode.FRENCH_PRESS, frenchPressSteps, currentIndex = 3, elapsedMs = 240_000), 0.001f)
        assertEquals(1f, guideProgress(BrewMode.FRENCH_PRESS, frenchPressSteps, currentIndex = 4, elapsedMs = 255_000), 0.001f)
    }

    @Test
    fun frenchPressProgressUsesTheRecipeTargetAndIgnoresPourProgressAndStepIndex() {
        val fiveMinuteRecipe = frenchPressSteps.dropLast(1) + RecipeStep(StepKind.PRESS, null, 300_000)
        val earlyStep = guideProgress(BrewMode.FRENCH_PRESS, fiveMinuteRecipe, currentIndex = 0, elapsedMs = 150_000)
        val terminalStep = guideProgress(BrewMode.FRENCH_PRESS, fiveMinuteRecipe, currentIndex = 3, elapsedMs = 150_000)

        assertEquals(0.5f, earlyStep, 0.001f)
        assertEquals(earlyStep, terminalStep, 0.001f)
    }

    @Test
    fun frenchPressProgressClampsBeforeAndAfterTheRecipeTarget() {
        assertEquals(0f, guideProgress(BrewMode.FRENCH_PRESS, frenchPressSteps, currentIndex = 3, elapsedMs = -1), 0.001f)
        assertEquals(1f, guideProgress(BrewMode.FRENCH_PRESS, frenchPressSteps, currentIndex = 0, elapsedMs = 500_000), 0.001f)
    }

    @Test
    fun missingOrNonpositivePressTargetFallsBackToTheExistingStepProgress() {
        val noPress = frenchPressSteps.dropLast(1)
        val zeroPress = frenchPressSteps.dropLast(1) + RecipeStep(StepKind.PRESS, null, 0)

        assertEquals(1f, guideProgress(BrewMode.FRENCH_PRESS, noPress, currentIndex = 2, elapsedMs = 150_000), 0.001f)
        assertEquals(0.75f, guideProgress(BrewMode.FRENCH_PRESS, zeroPress, currentIndex = 2, elapsedMs = 150_000), 0.001f)
        assertEquals(0f, guideProgress(BrewMode.FRENCH_PRESS, emptyList(), currentIndex = 0, elapsedMs = 0), 0.001f)
    }

    @Test
    fun otherModesKeepTheirStepBasedProgress() {
        assertEquals(0.5f, guideProgress(BrewMode.FILTER, frenchPressSteps, currentIndex = 1, elapsedMs = 120_000), 0.001f)
        assertEquals(0.75f, guideProgress(BrewMode.ESPRESSO, frenchPressSteps, currentIndex = 2, elapsedMs = 120_000), 0.001f)
    }
}
