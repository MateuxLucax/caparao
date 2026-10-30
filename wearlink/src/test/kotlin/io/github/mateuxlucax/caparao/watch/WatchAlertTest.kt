package io.github.mateuxlucax.caparao.watch

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
    }
}
