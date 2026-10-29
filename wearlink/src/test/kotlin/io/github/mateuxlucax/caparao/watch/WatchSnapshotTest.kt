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
    fun rejectsWhatItDidNotWrite() {
        val bytes = snapshot.encode()
        assertNull(WatchSnapshot.decode(bytes.copyOf(bytes.size - 1)))
        assertNull(WatchSnapshot.decode(bytes.copyOf().also { it[0] = 2 }))
        assertNull(WatchSnapshot.decode(bytes.copyOf().also { it[1] = 99 }))
        assertNull(WatchSnapshot.decode(ByteArray(0)))
    }
}
