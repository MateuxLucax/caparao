package io.github.mateuxlucax.caparao.brew

import org.junit.Assert.assertEquals
import org.junit.Test

class FlowSmootherTest {
    @Test
    fun constantRampGivesItsSlope() {
        val session = SyntheticSession().ramp(0.0, 40.0, 20.0)
        val smoother = FlowSmoother()
        val flows = session.readings.map { smoother.add(it) }
        flows.drop(10).forEach { assertEquals(2.0, it, 0.05) }
    }

    @Test
    fun noisyRampStaysCloseToItsSlope() {
        val session = SyntheticSession().ramp(0.0, 30.0, 20.0, noise = 0.1)
        val smoother = FlowSmoother()
        val flows = session.readings.map { smoother.add(it) }
        flows.drop(10).forEach { assertEquals(1.5, it, 0.35) }
    }

    @Test
    fun isZeroUntilHalfASecondOfData() {
        val smoother = FlowSmoother()
        (0..4).forEach { assertEquals(0.0, smoother.add(it * 100L, it * 1.0), 0.0) }
        assertEquals(10.0, smoother.add(500, 5.0), 1e-9)
    }

    @Test
    fun isNeverNegative() {
        val smoother = FlowSmoother()
        SyntheticSession().ramp(50.0, 0.0, 3.0).readings.forEach { assertEquals(0.0, smoother.add(it), 0.0) }
    }

    @Test
    fun forgetsReadingsOlderThanTheWindow() {
        val smoother = FlowSmoother()
        val session = SyntheticSession().ramp(0.0, 20.0, 5.0).hold(20.0, 1.5)
        session.readings.forEach { smoother.add(it) }
        assertEquals(0.0, smoother.flow(), 1e-9)
    }
}
