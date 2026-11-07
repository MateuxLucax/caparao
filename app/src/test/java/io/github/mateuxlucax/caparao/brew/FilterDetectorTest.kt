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
        assertEquals(liftAt, detector.pauseState!!.startedAtMs)
        assertEquals(250.0, detector.pauseState!!.waterWeightGrams, 0.2)

        session.ramp(190.0, 250.0, 0.3).hold(250.0, 2.0)
        val returnedAt = session.readings.first { it.reading.weightGrams >= 247.0 }.atMs
        val resumedAt = returnedAt + 1_000L
        detector.feed(session)
        assertEquals(BrewPhase.BREWING, detector.phase)
        assertNull(detector.pauseState)

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
    fun aHandPressingAboveTheWaterDoesNotResumeThePause() {
        openAndTare()
        pourWithBloom()
        session.ramp(250.0, 190.0, 0.3)
        detector.feed(session)
        assertEquals(BrewPhase.PAUSED, detector.phase)

        // A hand pressing on the dripper reads well above the 250 g of water: the pause holds.
        session.ramp(190.0, 300.0, 0.3).hold(300.0, 2.0)
        detector.feed(session)
        assertEquals("a hand over the water level does not resume", BrewPhase.PAUSED, detector.phase)

        session.ramp(300.0, 250.0, 0.3).hold(250.0, 2.0)
        detector.feed(session)
        assertEquals("the dripper back on the water resumes", BrewPhase.BREWING, detector.phase)
    }

    @Test
    fun dripperBackThenPourAtOnceResumesFromTheReturn() {
        openAndTare()
        pourWithBloom()
        val liftAt = session.nowMs
        session.ramp(250.0, 0.0, 0.3)
        detector.feed(session)
        assertEquals(BrewPhase.PAUSED, detector.phase)

        // The dripper goes back and the pour starts at once, well above the 250 g level for longer than a hand spike.
        session.ramp(0.0, 280.0, 0.3).hold(280.0, 4.0)
        // feed() clears the session, so the moment the weight first came back is read before it.
        val backAt = session.readings.first { it.reading.weightGrams >= 247.0 }.atMs
        detector.feed(session)
        assertEquals("a pour that stays above the band resumes the brew", BrewPhase.BREWING, detector.phase)
        assertNull(detector.pauseState)

        // The timer resumes from when the dripper came back on the scale, not three seconds later.
        assertEquals(backAt, detector.lastResumedAtMs!!)
        detector.stop(session.nowMs)
        assertEquals(
            (session.nowMs - SyntheticSession.STEP_MS - (backAt - liftAt)).toDouble(),
            detector.result!!.endedAtMs.toDouble(),
            100.0,
        )
    }

    @Test
    fun handPressOnReturnDoesNotBecomeWaterOrStartAnotherPause() {
        openAndTare()
        pourWithBloom()
        session.ramp(250.0, 0.0, 0.3)
        detector.feed(session)
        assertEquals(BrewPhase.PAUSED, detector.phase)

        // The dripper returns under pressure, stays at 290 g for about two seconds, then is released to 250 g.
        session.hold(290.0, 2.2).ramp(290.0, 250.0, 0.3).hold(250.0, 2.0)
        detector.feed(session)

        assertEquals(BrewPhase.BREWING, detector.phase)
        detector.stop(session.nowMs)
        assertEquals(250.0, detector.result!!.yieldGrams, 0.3)
    }

    @Test
    fun waterAddedWhileLiftedIsConfirmedAfterTheResumeSettleWindow() {
        openAndTare()
        pourWithBloom()
        session.ramp(250.0, 0.0, 0.3)
        detector.feed(session)
        assertEquals(BrewPhase.PAUSED, detector.phase)

        session.ramp(0.0, 280.0, 0.3).hold(280.0, 4.0)
        detector.feed(session)

        assertEquals(BrewPhase.BREWING, detector.phase)
        detector.stop(session.nowMs)
        assertEquals(280.0, detector.result!!.yieldGrams, 0.3)
    }

    @Test
    fun twoLiftPausesAccumulateWithoutChangingTheWaterYield() {
        openAndTare()
        session.ramp(0.0, 250.0, 50.0).hold(250.0, 3.0)
        detector.feed(session)
        val brewStart = detector.startedAtMs!!

        fun returnToBrew(): Long {
            val pending = session.readings.toList()
            session.readings.clear()
            var resumedAt: Long? = null
            pending.forEach { reading ->
                detector.onReading(reading)
                if (resumedAt == null && detector.phase == BrewPhase.BREWING) resumedAt = reading.atMs
            }
            return requireNotNull(resumedAt)
        }

        session.ramp(250.0, 0.0, 0.3)
        detector.feed(session)
        val firstPauseAt = detector.pauseState!!.startedAtMs
        session.ramp(0.0, 250.0, 0.3).hold(250.0, 3.0)
        val firstResumeAt = returnToBrew()

        session.ramp(250.0, 0.0, 0.3)
        detector.feed(session)
        val secondPauseAt = detector.pauseState!!.startedAtMs
        session.ramp(0.0, 250.0, 0.3).hold(250.0, 3.0)
        val secondResumeAt = returnToBrew()

        val stoppedAt = session.nowMs
        detector.stop(stoppedAt)
        assertEquals(BrewPhase.DONE, detector.phase)
        assertEquals(250.0, detector.result!!.yieldGrams, 0.3)
        assertEquals(
            stoppedAt - (firstResumeAt - firstPauseAt) - (secondResumeAt - secondPauseAt) - brewStart,
            detector.result!!.endedAtMs - detector.result!!.startedAtMs,
        )
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
        session.ramp(250.0, 180.0, 0.2)
        detector.feed(session)
        assertEquals(BrewPhase.PAUSED, detector.phase)

        session.hold(180.0, 0.5).ramp(180.0, 250.0, 0.3).hold(250.0, 5.0)
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
    fun aShortPourPlateauBeforeLiftDoesNotReplaceSettledWater() {
        openAndTare()
        session.ramp(0.0, 45.0, 9.0).hold(45.0, 3.0)
        detector.feed(session)

        session.ramp(45.0, 60.0, 1.5).hold(60.0, 0.2).hold(45.0, 5.5)
        detector.feed(session)

        assertEquals(BrewPhase.PAUSED, detector.phase)
        detector.stop(session.nowMs)
        assertEquals(56.0, detector.result!!.yieldGrams, 0.1)
    }

    @Test
    fun removingASpoonDuringThePauseReturnsToTheSettledWaterWeight() {
        openAndTare()
        session.ramp(0.0, 45.0, 9.0).hold(45.0, 3.0)
        detector.feed(session)

        // A spoon raises the reading slowly enough to look like water, but it is lifted before settling.
        session.ramp(45.0, 52.0, 1.0).ramp(52.0, 0.0, 0.3)
        detector.feed(session)
        assertEquals(BrewPhase.PAUSED, detector.phase)
        assertEquals(45.0, detector.pauseState!!.waterWeightGrams, 0.1)

        // The spoon comes off while the dripper is away; it returns to the settled water level.
        session.ramp(0.0, 45.0, 0.3).hold(45.0, 2.0)
        detector.feed(session)

        assertEquals(BrewPhase.BREWING, detector.phase)
        detector.stop(session.nowMs)
        assertEquals(45.0, detector.result!!.yieldGrams, 0.1)
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
