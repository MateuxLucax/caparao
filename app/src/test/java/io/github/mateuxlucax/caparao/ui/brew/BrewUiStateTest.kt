package io.github.mateuxlucax.caparao.ui.brew

import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.model.BrewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewUiStateTest {
    private fun espresso(weight: Double, dose: Double? = 18.0) =
        BrewUiState(BrewMode.ESPRESSO, dose = dose, phase = BrewPhase.BREWING, weightGrams = weight, targetRatio = 2.0)

    @Test
    fun progressGoesFromNothingToTheTargetAndStops() {
        assertEquals(0f, espresso(0.0).ratioProgress, 0.001f)
        assertEquals(0.5f, espresso(18.0).ratioProgress, 0.001f)
        assertEquals(1f, espresso(50.0).ratioProgress, 0.001f)
        assertEquals(0f, espresso(20.0, dose = null).ratioProgress, 0.001f)
    }

    @Test
    fun onTargetWhenTheShownRatioReachesIt() {
        assertFalse(espresso(34.0).onTarget) // 1:1.9
        assertTrue(espresso(35.2).onTarget) // 1:1.96 reads 1:2.0
        assertTrue(espresso(45.0).onTarget) // past it
        assertFalse(espresso(40.0, dose = null).onTarget)
    }

    @Test
    fun noRatioBeforeTheBrewStarts() {
        val cupOnTheScale = espresso(150.0).copy(phase = BrewPhase.WAITING_FOR_CUP)
        assertNull(cupOnTheScale.ratio)
        assertFalse(cupOnTheScale.onTarget)
    }

    @Test
    fun theEspressoWindowFollowsTheShotTime() {
        val recipe = io.github.mateuxlucax.caparao.model.Recipe(
            id = "e", name = "E", mode = BrewMode.ESPRESSO, method = "Espresso", doseGrams = 18.0, targetRatio = 2.4,
            targetMinMs = 28_000, targetMaxMs = 34_000, createdAt = 0, updatedAt = 0,
        )
        fun at(ms: Long, phase: BrewPhase = BrewPhase.BREWING) = BrewUiState(BrewMode.ESPRESSO, phase = phase, recipe = recipe, elapsedMs = ms).window
        assertEquals(null, at(0, BrewPhase.READY))
        assertEquals(BrewUiState.Window.BEFORE, at(27_900))
        assertEquals(BrewUiState.Window.INSIDE, at(28_000))
        assertEquals(BrewUiState.Window.INSIDE, at(34_000, BrewPhase.DONE))
        assertEquals(BrewUiState.Window.PAST, at(34_100))
        assertEquals(null, BrewUiState(BrewMode.ESPRESSO, phase = BrewPhase.BREWING, elapsedMs = 30_000).window)
    }
}
