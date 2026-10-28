package io.github.mateuxlucax.caparao.brew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EspressoDetectorTest {
    private val detector = EspressoDetector()
    private val session = SyntheticSession()

    /** Empty scale, cup put down, the scale tares (we script the zero), and it settles. Ends READY. */
    private fun placeCupAndTare(noise: Double = 0.0): List<Pair<Long, ScaleCommand>> {
        session.hold(0.0, 2.0, noise).ramp(0.0, 150.0, 0.3, noise).hold(150.0, 1.5, noise).hold(0.0, 2.0, noise)
        return detector.feed(session)
    }

    @Test
    fun normalShotIsDetectedWithStartEndAndYield() {
        val commands = placeCupAndTare(noise = 0.1)
        assertEquals(listOf(ScaleCommand.TARE), commands.map { it.second })
        assertEquals(BrewPhase.READY, detector.phase)

        session.hold(0.0, 6.0, 0.1) // pre-infusion
        val firstDrop = session.nowMs
        session.ramp(0.0, 4.0, 4.0, 0.1).ramp(4.0, 34.0, 15.0, 0.1).ramp(34.0, 36.0, 4.0, 0.1)
        val lastDrip = session.nowMs
        session.hold(36.0, 8.0, 0.1)
        detector.feed(session)

        val result = detector.result!!
        assertEquals(BrewPhase.DONE, detector.phase)
        assertEquals(firstDrop.toDouble(), result.startedAtMs.toDouble(), 500.0)
        assertEquals(lastDrip.toDouble(), result.endedAtMs.toDouble(), 1_000.0)
        assertEquals(36.0, result.yieldGrams, 0.3)
        assertTrue((result.endedAtMs - result.startedAtMs) in 20_000..30_000)
        assertNotNull(result.taredAtMs)
        assertTrue(result.taredAtMs!! < result.startedAtMs)
        assertEquals(false, result.interrupted)
    }

    @Test
    fun endIsTheStartOfTheFlatWindow() {
        placeCupAndTare()
        session.ramp(0.0, 30.0, 15.0)
        val lastRise = session.nowMs - SyntheticSession.STEP_MS
        session.hold(30.0, 5.0)
        detector.feed(session)
        // The flat window opens at the last reading still at 30 g... the reading where the rise finished.
        assertEquals(lastRise.toDouble(), detector.result!!.endedAtMs.toDouble(), 100.0)
    }

    @Test
    fun cupPlacedWithNoiseIsDetected() {
        session.hold(0.0, 2.0, 0.1).ramp(0.0, 95.0, 0.5, 0.1).hold(95.0, 1.2, 0.1)
        val commands = detector.feed(session)
        assertEquals(listOf(ScaleCommand.TARE), commands.map { it.second })
        assertEquals(BrewPhase.TARING, detector.phase)
    }

    @Test
    fun noiseAloneIsNotACup() {
        session.hold(0.0, 10.0, 0.1)
        assertTrue(detector.feed(session).isEmpty())
        assertEquals(BrewPhase.WAITING_FOR_CUP, detector.phase)
    }

    @Test
    fun cupAlreadyOnTheScaleAtStart() {
        session.hold(150.0, 1.2)
        assertEquals(listOf(ScaleCommand.TARE), detector.feed(session).map { it.second })
        assertEquals(BrewPhase.TARING, detector.phase)
    }

    @Test
    fun armNowTaresWithoutACup() {
        assertEquals(ScaleCommand.TARE, detector.armNow(0))
        assertEquals(BrewPhase.TARING, detector.phase)
        session.hold(0.0, 1.0)
        detector.feed(session)
        assertEquals(BrewPhase.READY, detector.phase)
        session.ramp(0.0, 20.0, 10.0).hold(20.0, 5.0)
        detector.feed(session)
        assertEquals(0L, detector.result!!.taredAtMs)
    }

    @Test
    fun tareIsRetriedOnceWhenTheScaleDoesNotZero() {
        session.hold(0.0, 2.0).ramp(0.0, 150.0, 0.3).hold(150.0, 9.0)
        val commands = detector.feed(session)
        assertEquals(listOf(ScaleCommand.TARE, ScaleCommand.TARE), commands.map { it.second })
        val (firstAt, secondAt) = commands.map { it.first }
        assertEquals(3_000.0, (secondAt - firstAt).toDouble(), 100.0)
        assertEquals(BrewPhase.TARING, detector.phase)

        session.hold(0.0, 1.0)
        detector.feed(session)
        assertEquals(BrewPhase.READY, detector.phase)
    }

    @Test
    fun cupLiftedBeforeTheFirstDropGoesBackToWaiting() {
        placeCupAndTare()
        session.hold(-150.0, 2.0)
        assertTrue(detector.feed(session).isEmpty())
        assertEquals(BrewPhase.WAITING_FOR_CUP, detector.phase)
        assertNull(detector.result)

        // Put it back: a new cup relative to the new baseline.
        session.ramp(-150.0, 0.0, 0.3).hold(0.0, 1.2)
        assertEquals(listOf(ScaleCommand.TARE), detector.feed(session).map { it.second })

        detector.stop(session.nowMs)
        assertEquals(BrewPhase.DONE, detector.phase)
        assertNull(detector.result)
    }

    @Test
    fun aSingleBlipIsNotTheFirstDrop() {
        placeCupAndTare()
        session.hold(0.4, 0.1).hold(0.0, 2.0)
        detector.feed(session)
        assertEquals(BrewPhase.READY, detector.phase)
    }

    @Test
    fun slowFlowWithAThreeSecondPauseKeepsBrewing() {
        placeCupAndTare()
        session.ramp(0.0, 10.0, 10.0).hold(10.0, 3.0)
        detector.feed(session)
        assertEquals(BrewPhase.BREWING, detector.phase)

        session.ramp(10.0, 20.0, 10.0).hold(20.0, 5.0)
        detector.feed(session)
        assertEquals(20.0, detector.result!!.yieldGrams, 0.01)
    }

    @Test
    fun manualStopEndsAtTheTapWithTheCurrentWeight() {
        placeCupAndTare()
        session.ramp(0.0, 25.0, 12.0)
        detector.feed(session)
        detector.stop(session.nowMs)
        val result = detector.result!!
        assertEquals(session.nowMs, result.endedAtMs)
        assertEquals(25.0, result.yieldGrams, 0.01)
        assertEquals(false, result.interrupted)
    }

    @Test
    fun stopBeforeBrewingSavesNothing() {
        placeCupAndTare()
        detector.stop(session.nowMs)
        assertEquals(BrewPhase.DONE, detector.phase)
        assertNull(detector.result)
    }

    @Test
    fun disconnectMidBrewIsInterrupted() {
        placeCupAndTare()
        session.ramp(0.0, 18.0, 9.0)
        detector.feed(session)
        detector.interrupt(session.nowMs)
        val result = detector.result!!
        assertTrue(result.interrupted)
        assertEquals(18.0, result.yieldGrams, 0.01)
        assertEquals(session.nowMs, result.endedAtMs)
    }

    @Test
    fun disconnectBeforeBrewingSavesNothing() {
        detector.interrupt(0)
        assertEquals(BrewPhase.DONE, detector.phase)
        assertNull(detector.result)
    }

    @Test
    fun cupLiftedMidBrewEndsAtTheLiftWithTheMaxWeight() {
        placeCupAndTare()
        session.ramp(0.0, 30.0, 15.0)
        val liftAt = session.nowMs - SyntheticSession.STEP_MS
        session.ramp(30.0, -150.0, 0.2).hold(-150.0, 2.0)
        detector.feed(session)
        val result = detector.result!!
        assertEquals(liftAt.toDouble(), result.endedAtMs.toDouble(), 100.0)
        assertEquals(30.0, result.yieldGrams, 0.01)
    }

    @Test
    fun stopDuringASlowLiftEndsBeforeItWithTheMaxWeight() {
        placeCupAndTare()
        session.ramp(0.0, 30.0, 15.0)
        val riseEnd = session.nowMs
        session.ramp(30.0, 10.0, 2.5)
        detector.feed(session)
        detector.stop(session.nowMs)
        val result = detector.result!!
        assertEquals(30.0, result.yieldGrams, 0.3)
        assertEquals(riseEnd.toDouble(), result.endedAtMs.toDouble(), 1_000.0)
    }

    @Test
    fun slowLiftBelowZeroEndsByItselfWithTheMaxWeight() {
        placeCupAndTare()
        session.ramp(0.0, 30.0, 15.0).ramp(30.0, -2.0, 4.0).hold(-2.0, 1.0)
        detector.feed(session)
        assertEquals(BrewPhase.DONE, detector.phase)
        assertEquals(30.0, detector.result!!.yieldGrams, 0.3)
    }

    @Test
    fun overloadAndSaturatedFlowDoNotConfuseIt() {
        session.hold(0.0, 1.0).ramp(0.0, 150.0, 0.2, flow = 99.9, overload = true)
            .hold(150.0, 1.5, flow = 99.9, overload = true).hold(0.0, 2.0, flow = 99.9)
        assertEquals(listOf(ScaleCommand.TARE), detector.feed(session).map { it.second })
        assertEquals(BrewPhase.READY, detector.phase)

        val firstDrop = session.nowMs
        session.ramp(0.0, 36.0, 18.0, flow = 99.9, overload = true).hold(36.0, 6.0, flow = 99.9)
        detector.feed(session)
        val result = detector.result!!
        assertEquals(firstDrop.toDouble(), result.startedAtMs.toDouble(), 500.0)
        assertEquals(36.0, result.yieldGrams, 0.3)
    }
}
