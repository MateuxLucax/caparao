package io.github.mateuxlucax.caparao.brew

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
}
