package io.github.mateuxlucax.caparao.ui.brews

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartScaleTest {
    @Test
    fun espressoWeightGetsARoundAxis() {
        assertEquals(Axis(40.0, 10.0), ChartScale.axis(36.4, minimum = 10.0))
        assertEquals(listOf(0.0, 10.0, 20.0, 30.0, 40.0), ChartScale.axis(36.4, minimum = 10.0).ticks)
    }

    @Test
    fun filterWeightGetsARoundAxis() {
        assertEquals(Axis(500.0, 100.0), ChartScale.axis(480.0, minimum = 10.0))
        assertEquals(Axis(300.0, 100.0), ChartScale.axis(250.1, minimum = 10.0))
        assertEquals(Axis(250.0, 50.0), ChartScale.axis(240.0, minimum = 10.0))
    }

    @Test
    fun anEmptyCurveUsesTheMinimum() {
        assertEquals(Axis(10.0, 2.0), ChartScale.axis(0.0, minimum = 10.0))
    }

    @Test
    fun aValueOnAGridlineIsNotPushedUp() {
        assertEquals(Axis(40.0, 10.0), ChartScale.axis(40.0, minimum = 1.0))
    }

    @Test
    fun timeAxisStepsFitTheDuration() {
        assertEquals(Axis(30.0, 5.0), ChartScale.timeAxis(28_000))
        assertEquals(Axis(240.0, 60.0), ChartScale.timeAxis(215_000))
        assertEquals(Axis(5.0, 5.0), ChartScale.timeAxis(0))
    }

    @Test
    fun fractionIsClamped() {
        val axis = Axis(40.0, 10.0)
        assertEquals(0.5, axis.fraction(20.0), 0.0)
        assertEquals(1.0, axis.fraction(99.0), 0.0)
        assertEquals(0.0, axis.fraction(-3.0), 0.0)
    }
}
