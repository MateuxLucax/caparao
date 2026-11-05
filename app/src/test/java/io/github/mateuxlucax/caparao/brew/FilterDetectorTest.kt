package io.github.mateuxlucax.caparao.brew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterDetectorTest {
    private val detector = FilterDetector()
    private val session = SyntheticSession()

    /** Dripper and server on the scale (400 g) at open; the scale tares and settles. Ends READY. */
    private fun openAndTare(): List<Pair<Long, ScaleCommand>> {
        session.hold(400.0, 0.3).hold(0.0, 1.0, 0.1)
        return detector.feed(session)
    }

    /** Bloom: 50 g, 30 s wait, then up to 250 g. Returns when the first water hit. */
    private fun pourWithBloom(): Long {
        session.hold(0.0, 2.0, 0.1)
        val firstWater = session.nowMs
        session.ramp(0.0, 50.0, 8.0, 0.1).hold(50.0, 30.0, 0.1).ramp(50.0, 250.0, 60.0, 0.1).hold(250.0, 20.0, 0.1)
        detector.feed(session)
        return firstWater
    }

    @Test
    fun taresOnTheFirstReading() {
        // Only the first reading asks for a tare; the scale zeroes in time, so no retry.
        assertEquals(listOf(0L to ScaleCommand.TARE), openAndTare())
        assertEquals(BrewPhase.READY, detector.phase)
    }

    @Test
    fun retriesTheTareOnce() {
        session.hold(400.0, 8.0)
        val commands = detector.feed(session)
        assertEquals(listOf(0L, 3_000L), commands.map { it.first })
        session.hold(0.0, 1.0)
        detector.feed(session)
        assertEquals(BrewPhase.READY, detector.phase)
    }

    /** The scale never zeroes: tared at 0 s, retried at 3 s, timed out 5 s after the retry (8 s). */
    private fun scaleThatStaysAtWeight() {
        session.hold(400.0, 7.0)
        detector.feed(session)
        assertFalse(detector.tareTimedOut)
        session.hold(400.0, 3.0)
        detector.feed(session)
        assertEquals(BrewPhase.TARING, detector.phase)
        assertTrue(detector.tareTimedOut)
    }

    @Test
    fun tareTimesOutAfterTheRetryAndALateZeroStillContinues() {
        scaleThatStaysAtWeight()
        session.hold(0.0, 1.0)
        detector.feed(session)
        assertEquals(BrewPhase.READY, detector.phase)
        assertFalse(detector.tareTimedOut)

        // The late zero arrives with no retry: pouring still starts the brew, and the zero is kept for the result.
        session.ramp(0.0, 250.0, 60.0, 0.1).hold(250.0, 5.0, 0.1)
        detector.feed(session)
        assertEquals(BrewPhase.BREWING, detector.phase)
        detector.stop(session.nowMs)
        val result = detector.result!!
        assertEquals(250.0, result.yieldGrams, 0.3)
        assertNotNull(result.taredAtMs)
        assertFalse(result.interrupted)
    }

    @Test
    fun retryTareAsksAgainClearsTheTimeoutAndAZeroThenContinues() {
        scaleThatStaysAtWeight()
        assertEquals(ScaleCommand.TARE, detector.retryTare(session.nowMs))
        assertEquals(BrewPhase.TARING, detector.phase)
        assertFalse(detector.tareTimedOut)

        session.hold(0.0, 1.0)
        assertEquals(emptyList<Pair<Long, ScaleCommand>>(), detector.feed(session))
        assertEquals(BrewPhase.READY, detector.phase)
        assertFalse(detector.tareTimedOut)
    }

    @Test
    fun normalPourWithBloomPauseThenDone() {
        openAndTare()
        val firstWater = pourWithBloom()
        assertEquals(BrewPhase.BREWING, detector.phase)

        detector.stop(session.nowMs)
        val result = detector.result!!
        assertEquals(firstWater.toDouble(), result.startedAtMs.toDouble(), 500.0)
        assertEquals(result.startedAtMs, detector.startedAtMs)
        assertEquals(session.nowMs, result.endedAtMs)
        assertEquals(250.0, result.yieldGrams, 0.3)
        assertEquals(false, result.interrupted)
    }

    @Test
    fun dripperLiftPausesAtOnceAndReturningResumesTheClock() {
        openAndTare()
        pourWithBloom()
        val liftAt = session.nowMs
        // The dripper (and the water still in it) leaves; the server keeps 190 g.
        session.ramp(250.0, 190.0, 0.3)
        detector.feed(session)
        assertEquals(BrewPhase.PAUSED, detector.phase)
        assertEquals(liftAt, detector.pauseStartedAtMs)

        session.ramp(190.0, 250.0, 0.3).hold(250.0, 2.0)
        val returnedAt = session.readings.first { it.reading.weightGrams >= 247.0 }.atMs
        val resumedAt = returnedAt + 1_000L
        detector.feed(session)
        assertEquals(BrewPhase.BREWING, detector.phase)
        assertEquals(null, detector.pauseStartedAtMs)

        detector.stop(session.nowMs)
        val result = detector.result!!
        assertEquals(
            (session.nowMs - SyntheticSession.STEP_MS - (resumedAt - liftAt)).toDouble(),
            result.endedAtMs.toDouble(),
            100.0,
        )
        assertEquals(250.0, result.yieldGrams, 0.3)
    }

    @Test
    fun stopRightAfterLiftingTheServerKeepsTheWaterPoured() {
        openAndTare()
        pourWithBloom()
        val liftAt = session.nowMs - SyntheticSession.STEP_MS
        // Everything off the scale: below the tare, and Done tapped during the pause.
        session.ramp(250.0, -280.0, 0.3).hold(-280.0, 2.0)
        detector.feed(session)
        detector.stop(session.nowMs)
        val result = detector.result!!
        assertEquals(liftAt.toDouble(), result.endedAtMs.toDouble(), 100.0)
        assertEquals(250.0, result.yieldGrams, 0.3)
    }

    @Test
    fun smallBrewLiftedByFractionPausesAndEndsAfterTimeout() {
        openAndTare()
        session.ramp(0.0, 100.0, 20.0).hold(100.0, 2.0) // 15 %, below 30 g
        val liftAt = session.nowMs - SyntheticSession.STEP_MS
        session.hold(85.0, 6.0)
        detector.feed(session)
        assertEquals(BrewPhase.PAUSED, detector.phase)
        session.hold(85.0, 22.0)
        detector.feed(session)
        assertEquals(BrewPhase.PAUSED, detector.phase)
        session.hold(85.0, 6.0)
        detector.feed(session)
        assertEquals(100.0, detector.result!!.yieldGrams, 0.01)
        assertEquals(liftAt, detector.result!!.endedAtMs)
    }

    @Test
    fun dripperBumpedAndPutBackKeepsBrewing() {
        openAndTare()
        pourWithBloom()
        session.ramp(250.0, 225.0, 0.2).hold(225.0, 3.0).ramp(225.0, 250.0, 2.0).hold(250.0, 10.0)
        detector.feed(session)
        assertEquals(BrewPhase.BREWING, detector.phase)
        detector.stop(session.nowMs)
        assertEquals(250.0, detector.result!!.yieldGrams, 0.5)
    }

    @Test
    fun transientSpoonWeightDoesNotEndTheBrewOrReplaceSettledWater() {
        openAndTare()
        session.ramp(0.0, 45.0, 9.0).hold(45.0, 3.0)
        detector.feed(session)

        session.hold(52.0, 0.5).hold(45.0, 2.0)
        detector.feed(session)
        assertEquals(BrewPhase.BREWING, detector.phase)

        session.ramp(45.0, 60.0, 3.0).hold(60.0, 2.0).hold(45.0, 6.0)
        detector.feed(session)

        assertEquals(BrewPhase.PAUSED, detector.phase)
        detector.stop(session.nowMs)
        assertEquals(60.0, detector.result!!.yieldGrams, 0.1)
    }

    @Test
    fun spoonHeldForMoreThanTheSettleWindowIsStillDiscardedWhenItReturns() {
        openAndTare()
        session.ramp(0.0, 45.0, 9.0).hold(45.0, 3.0)
        detector.feed(session)

        session.hold(52.0, 1.5).hold(45.0, 6.0)
        detector.feed(session)

        assertEquals(BrewPhase.BREWING, detector.phase)
        detector.stop(session.nowMs)
        assertEquals(45.0, detector.result!!.yieldGrams, 0.1)
    }

    @Test
    fun doneWithASpoonOnTheScaleSavesTheLastSettledWaterWeight() {
        openAndTare()
        session.ramp(0.0, 45.0, 9.0).hold(45.0, 3.0).hold(52.0, 0.5)
        detector.feed(session)

        detector.stop(session.nowMs)

        assertEquals(45.0, detector.result!!.yieldGrams, 0.1)
    }

    @Test
    fun aShortPourPlateauBeforeLiftKeepsTheLastUnconfirmedWater() {
        openAndTare()
        session.ramp(0.0, 45.0, 9.0).hold(45.0, 3.0)
        detector.feed(session)

        session.ramp(45.0, 60.0, 1.5).hold(60.0, 0.2).hold(45.0, 5.5)
        detector.feed(session)

        assertEquals(BrewPhase.PAUSED, detector.phase)
        detector.stop(session.nowMs)
        assertEquals(60.0, detector.result!!.yieldGrams, 0.1)
    }

    @Test
    fun noiseEarlyInThePourIsNotALift() {
        openAndTare()
        session.ramp(0.0, 3.0, 2.0).hold(3.0, 10.0, 0.1).hold(2.0, 10.0)
        detector.feed(session)
        assertEquals(BrewPhase.BREWING, detector.phase)
    }

    @Test
    fun disconnectMidPourIsInterrupted() {
        openAndTare()
        session.ramp(0.0, 120.0, 30.0)
        detector.feed(session)
        detector.interrupt(session.nowMs)
        val result = detector.result!!
        assertTrue(result.interrupted)
        // An interrupt during the unconfirmed final rise saves the last settled water (about one second behind).
        assertEquals(116.0, result.yieldGrams, 0.1)
    }

    @Test
    fun doneBeforeAnyWaterSavesNothing() {
        openAndTare()
        detector.stop(session.nowMs)
        assertEquals(BrewPhase.DONE, detector.phase)
        assertNull(detector.result)
    }

    @Test
    fun disabledLiftDetectionKeepsBrewingAndDoneUsesTheHighestWaterWeight() {
        val detector = FilterDetector(FilterConfig(liftDetectionEnabled = false))
        val session = SyntheticSession().hold(400.0, 0.3).hold(0.0, 1.0)
        detector.feed(session)
        session.ramp(0.0, 300.0, 30.0)
        detector.feed(session)
        session.ramp(300.0, 0.0, 0.4).hold(0.0, 10.0)
        detector.feed(session)

        assertEquals(BrewPhase.BREWING, detector.phase)
        detector.stop(session.nowMs)

        assertEquals(BrewPhase.DONE, detector.phase)
        assertEquals(300.0, detector.result!!.yieldGrams, 0.3)
        assertEquals(session.nowMs, detector.result!!.endedAtMs)
        assertEquals(false, detector.result!!.interrupted)
    }
}
