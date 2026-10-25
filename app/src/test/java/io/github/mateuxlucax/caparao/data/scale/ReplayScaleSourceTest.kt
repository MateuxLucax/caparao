package io.github.mateuxlucax.caparao.data.scale

import io.github.mateuxlucax.caparao.model.ScaleBattery
import io.github.mateuxlucax.caparao.model.ScaleState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ReplayScaleSourceTest {
    // Unit tests run in the module directory.
    private val pressRamp = File("src/test/resources/sessions/press-ramp.jsonl").readLines()

    @Test
    fun playsTheRecordingAtItsPace() = runTest {
        val replay = ReplayScaleSource(backgroundScope, pressRamp)
        replay.start()
        runCurrent()
        assertEquals(0.0, replay.reading.value!!.weightGrams, 0.0)
        // In the recording the weight peaks at 1300.1 g about 46.7 s in, and the battery answer came at 3.6 s.
        advanceTimeBy(46_700 - 2_638)
        assertEquals(1300.1, replay.reading.value!!.weightGrams, 1.0)
        assertEquals(ScaleState.Connected(ScaleBattery(percent = 100, bars = 3)), replay.state.value)
    }

    @Test
    fun tareAndTimerApplyToTheReplayedReadings() = runTest {
        val replay = ReplayScaleSource(backgroundScope, pressRamp, startAtMs = 38_000)
        replay.start()
        advanceTimeBy(500)
        val before = replay.reading.value!!.weightGrams
        replay.tare()
        assertEquals(0.0, replay.reading.value!!.weightGrams, 0.0)
        replay.timer(TimerAction.START)
        advanceTimeBy(3_100)
        assertEquals(3, replay.reading.value!!.timerSeconds)
        replay.timer(TimerAction.RESET)
        advanceTimeBy(200)
        assertEquals(0, replay.reading.value!!.timerSeconds)
        check(before > 0)
    }

    @Test
    fun stopClearsTheReading() = runTest {
        val replay = ReplayScaleSource(backgroundScope, pressRamp)
        replay.start()
        advanceTimeBy(1_000)
        replay.stop()
        assertEquals(ScaleState.Idle, replay.state.value)
        assertEquals(null, replay.reading.value)
    }
}
