package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.ScaleReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrenchPressFinishTrackerTest {
    @Test
    fun requiresAStableNearZeroOrNegativeReadingAndDoesNotFinishTheBrew() {
        val tracker = FrenchPressFinishTracker()

        assertFalse(tracker.update(0, 300.0))
        assertNull(tracker.removalStartedAtMs)
        assertFalse(tracker.update(1_000, -300.0))
        assertEquals(1_000L, tracker.removalStartedAtMs)
        assertFalse(tracker.update(4_000, 300.0))
        assertNull("a return to the scale cancels a brief dip", tracker.removalStartedAtMs)
        assertFalse(tracker.update(5_000, -300.0))
        assertFalse(tracker.update(9_999, 0.0))
        assertTrue(tracker.update(10_000, -300.0))
        assertEquals(5_000L, tracker.removedAtMs)
        assertTrue("removal stays confirmed", tracker.update(20_000, 300.0))
    }

    @Test
    fun backdateStartsAtTheFirstLowReadingOfTheCurrentRunNotBeforeSince() {
        val tracker = FrenchPressFinishTracker()
        val readings = listOf(
            reading(234_000, 300.0),
            reading(235_000, 0.0),
            reading(236_000, -400.0),
            reading(237_000, 2.0),
        )

        tracker.backdateFrom(readings, sinceMs = 235_500, atMs = 237_000)

        assertEquals("the run starts after the bound", 236_000L, tracker.removalStartedAtMs)
    }

    @Test
    fun backdateDoesNothingWhenTheNewestReadingIsHigh() {
        val tracker = FrenchPressFinishTracker()
        val readings = listOf(reading(235_000, 0.0), reading(237_000, 300.0))

        tracker.backdateFrom(readings, sinceMs = 0, atMs = 237_000)

        assertNull(tracker.removalStartedAtMs)
    }

    @Test
    fun backdateDoesNothingWhenTheNewestReadingIsNotTheCurrentOne() {
        val tracker = FrenchPressFinishTracker()
        val readings = listOf(reading(235_000, 0.0), reading(236_000, 0.0))

        tracker.backdateFrom(readings, sinceMs = 0, atMs = 240_000)

        assertNull("a buffer that stopped growing is not evidence of a removal", tracker.removalStartedAtMs)
    }

    @Test
    fun backdateStopsAtANonFiniteWeight() {
        val tracker = FrenchPressFinishTracker()
        val readings = listOf(reading(235_000, 0.0), reading(236_000, Double.NaN), reading(237_000, 0.0))

        tracker.backdateFrom(readings, sinceMs = 0, atMs = 237_000)

        assertEquals("the non-finite reading breaks the low run", 237_000L, tracker.removalStartedAtMs)
    }

    private fun reading(atMs: Long, weightGrams: Double) =
        TimedReading(atMs, ScaleReading(weightGrams, 0.0, 0, overload = false))
}
