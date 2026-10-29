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
}
