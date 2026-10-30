package io.github.mateuxlucax.caparao.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchSnapshotTest {
    private val snapshot = WatchSnapshot(WatchState.BREWING, 36.4f, 1.8f, 27_350, timerRunning = true)

    @Test
    fun roundTrips() {
        WatchState.entries.forEach { state ->
            val each = snapshot.copy(state = state, weightGrams = -0.3f, timerRunning = false)
            assertEquals(each, WatchSnapshot.decode(each.encode()))
        }
        assertEquals(snapshot, WatchSnapshot.decode(snapshot.encode()))
    }

    @Test
    fun roundTripsTheGuide() {
        val pouring = snapshot.copy(guide = WatchGuide(2, 5, WatchStepKind.POUR, 150f, null, -3_000))
        assertEquals(pouring, WatchSnapshot.decode(pouring.encode()))
        val waiting = snapshot.copy(guide = WatchGuide(3, 5, WatchStepKind.WAIT, null, 4_200, null))
        assertEquals(waiting, WatchSnapshot.decode(waiting.encode()))
        val allDone = snapshot.copy(guide = WatchGuide(5, 5, null, null, null, null))
        assertEquals(allDone, WatchSnapshot.decode(allDone.encode()))
    }

    @Test
    fun rejectsWhatItDidNotWrite() {
        val bytes = snapshot.encode()
        assertNull(WatchSnapshot.decode(bytes.copyOf(bytes.size - 1)))
        assertNull(WatchSnapshot.decode(bytes.copyOf().also { it[0] = 1 }))
        assertNull(WatchSnapshot.decode(bytes.copyOf().also { it[1] = 99 }))
        assertNull(WatchSnapshot.decode(ByteArray(0)))
    }
}
