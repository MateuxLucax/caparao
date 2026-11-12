package dev.mateux.caparao.ui.brew

import org.junit.Assert.assertEquals
import org.junit.Test

/** The weigh step takes a reading only when it is a dose (#195). */
class WeighedDoseTest {
    @Test
    fun aReadingOutsideTheDoseLimitsKeepsThePlannedDose() {
        assertEquals(18.0, weighedDose(connected = true, readingGrams = 0.1, plannedDose = 18.0), 0.0)
        assertEquals(18.0, weighedDose(connected = true, readingGrams = 150.0, plannedDose = 18.0), 0.0)
    }

    @Test
    fun aReadingInsideTheDoseLimitsBecomesTheDose() {
        assertEquals(1.0, weighedDose(connected = true, readingGrams = 1.0, plannedDose = 18.0), 0.0)
        assertEquals(100.0, weighedDose(connected = true, readingGrams = 100.0, plannedDose = 18.0), 0.0)
    }

    @Test
    fun aDisconnectedScaleKeepsThePlannedDose() {
        assertEquals(18.0, weighedDose(connected = false, readingGrams = 20.0, plannedDose = 18.0), 0.0)
    }
}
