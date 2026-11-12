package dev.mateux.caparao.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchAlertTest {
    @Test
    fun roundTrips() {
        WatchAlert.entries.forEach { assertEquals(it, WatchAlert.decode(it.encode())) }
    }

    @Test
    fun rejectsWhatItDidNotWrite() {
        assertNull(WatchAlert.decode(ByteArray(0)))
        assertNull(WatchAlert.decode(byteArrayOf(99)))
        assertNull(WatchAlert.decode(byteArrayOf(0, 0)))
        // Appended in #184: the French press deadline keeps its wire byte, so older builds decode it as null.
        assertEquals(WatchAlert.FRENCH_PRESS_DEADLINE, WatchAlert.decode(byteArrayOf(16)))
    }
}
