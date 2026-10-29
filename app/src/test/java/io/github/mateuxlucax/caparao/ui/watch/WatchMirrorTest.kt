package io.github.mateuxlucax.caparao.ui.watch

import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.ScaleReading
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.ui.brew.BrewStep
import io.github.mateuxlucax.caparao.ui.brew.BrewUiState
import io.github.mateuxlucax.caparao.watch.WatchSnapshot
import io.github.mateuxlucax.caparao.watch.WatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchMirrorTest {
    private val connected = ScaleState.Connected(battery = null)
    private val reading = ScaleReading(weightGrams = 18.2, flowGramsPerSecond = -0.4, timerSeconds = 12, overload = false)

    @Test
    fun disconnectedSendsNothingButTheState() {
        val snapshot = WatchMirror.snapshot(ScaleState.Searching, reading, null, timerRunning = true)
        assertEquals(WatchSnapshot(WatchState.DISCONNECTED, 0f, 0f, 0, timerRunning = false), snapshot)
    }

    @Test
    fun withoutABrewTheScaleItselfShows() {
        val snapshot = WatchMirror.snapshot(connected, reading, null, timerRunning = true)
        assertEquals(WatchSnapshot(WatchState.SCALE, 18.2f, 0f, 12_000, timerRunning = true), snapshot)
        // The dose step is still weighing, not brewing.
        assertEquals(WatchState.SCALE, WatchMirror.snapshot(connected, reading, BrewUiState(BrewMode.ESPRESSO), false).state)
    }

    @Test
    fun aLiveBrewShowsItsOwnNumbers() {
        val brew = BrewUiState(BrewMode.ESPRESSO, step = BrewStep.LIVE, phase = BrewPhase.BREWING, weightGrams = 30.5, flowGramsPerSecond = 1.9, elapsedMs = 21_400)
        assertEquals(WatchSnapshot(WatchState.BREWING, 30.5f, 1.9f, 21_400, timerRunning = false), WatchMirror.snapshot(connected, reading, brew, false))
        assertEquals(WatchState.READY, WatchMirror.snapshot(connected, reading, brew.copy(phase = BrewPhase.READY), false).state)
        assertEquals(WatchState.READY, WatchMirror.snapshot(connected, reading, brew.copy(phase = BrewPhase.WAITING_FOR_CUP), false).state)
        assertEquals(WatchState.DONE, WatchMirror.snapshot(connected, reading, brew.copy(step = BrewStep.SUMMARY), false).state)
    }

    @Test
    fun throttleSendsFiveASecondButStateChangesAtOnce() {
        val throttle = SnapshotThrottle(intervalMs = 200)
        val scale = WatchSnapshot(WatchState.SCALE, 1f, 0f, 0, false)
        assertTrue(throttle.offer(0, scale))
        assertFalse(throttle.offer(100, scale.copy(weightGrams = 2f)))
        assertTrue(throttle.offer(200, scale.copy(weightGrams = 3f)))
        assertTrue(throttle.offer(250, scale.copy(state = WatchState.BREWING)))
        assertTrue(throttle.offer(260, scale.copy(state = WatchState.BREWING, timerRunning = true)))
        assertFalse(throttle.offer(300, scale.copy(state = WatchState.BREWING, timerRunning = true)))
    }
}
