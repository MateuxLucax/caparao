package io.github.mateuxlucax.caparao.brew

import org.junit.Assert.assertEquals
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

    @Test
    fun normalPourWithBloomPauseThenDone() {
        openAndTare()
        val firstWater = pourWithBloom()
        assertEquals(BrewPhase.BREWING, detector.phase)

        detector.stop(session.nowMs)
        val result = detector.result!!
        assertEquals(firstWater.toDouble(), result.startedAtMs.toDouble(), 500.0)
        assertEquals(session.nowMs, result.endedAtMs)
        assertEquals(250.0, result.yieldGrams, 0.3)
        assertEquals(false, result.interrupted)
    }

    @Test
    fun dripperLiftedEndsAtTheLiftWithTheWaterPoured() {
        openAndTare()
        pourWithBloom()
        val liftAt = session.nowMs - SyntheticSession.STEP_MS
        // The dripper (and the water still in it) leaves; the server keeps 190 g.
        session.ramp(250.0, 190.0, 0.3).hold(190.0, 4.5)
        detector.feed(session)
        assertEquals(BrewPhase.BREWING, detector.phase)

        session.hold(190.0, 1.0)
        detector.feed(session)
        val result = detector.result!!
        assertEquals(liftAt.toDouble(), result.endedAtMs.toDouble(), 100.0)
        assertEquals(250.0, result.yieldGrams, 0.3)
    }

    @Test
    fun stopRightAfterLiftingTheServerKeepsTheWaterPoured() {
        openAndTare()
        pourWithBloom()
        val liftAt = session.nowMs - SyntheticSession.STEP_MS
        // Everything off the scale: below the tare, and done tapped before the lift hold ran out.
        session.ramp(250.0, -280.0, 0.3).hold(-280.0, 2.0)
        detector.feed(session)
        detector.stop(session.nowMs)
        val result = detector.result!!
        assertEquals(liftAt.toDouble(), result.endedAtMs.toDouble(), 100.0)
        assertEquals(250.0, result.yieldGrams, 0.3)
    }

    @Test
    fun smallBrewLiftedByFractionEnds() {
        openAndTare()
        session.ramp(0.0, 100.0, 20.0).hold(100.0, 2.0).hold(85.0, 6.0) // 15 %, below 30 g
        detector.feed(session)
        assertEquals(100.0, detector.result!!.yieldGrams, 0.01)
    }

    @Test
    fun dripperBumpedAndPutBackKeepsBrewing() {
        openAndTare()
        pourWithBloom()
        session.ramp(250.0, 180.0, 0.2).hold(180.0, 3.0).ramp(180.0, 250.0, 0.3).hold(250.0, 10.0)
        detector.feed(session)
        assertEquals(BrewPhase.BREWING, detector.phase)
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
        assertEquals(120.0, result.yieldGrams, 0.01)
    }

    @Test
    fun doneBeforeAnyWaterSavesNothing() {
        openAndTare()
        detector.stop(session.nowMs)
        assertEquals(BrewPhase.DONE, detector.phase)
        assertNull(detector.result)
    }
}
