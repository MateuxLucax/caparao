package io.github.mateuxlucax.caparao.ui.brew

import org.junit.Assert.assertEquals
import org.junit.Test

class EspressoPictureTest {
    @Test
    fun dropsStartSlowAndSpeedUpWhileWaiting() {
        assertEquals(1.6, dropPeriodSeconds(0.0), 1e-9)
        assertEquals(1.05, dropPeriodSeconds(10.0), 1e-9)
    }

    @Test
    fun dropsStopSpeedingUpAfterTheRamp() {
        assertEquals(0.5, dropPeriodSeconds(20.0), 1e-9)
        assertEquals(0.5, dropPeriodSeconds(300.0), 1e-9)
    }

    @Test
    fun negativeWaitsStayAtTheSlowestDrop() {
        assertEquals(1.6, dropPeriodSeconds(-5.0), 1e-9)
    }
}
