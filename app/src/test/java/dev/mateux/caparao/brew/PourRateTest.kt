package dev.mateux.caparao.brew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PourRateTest {
    @Test
    fun targetBandAllowsAQuarterEitherWay() {
        val band = PourRate.band(5.0)
        assertEquals(3.75..6.25, band)
        assertTrue(4.0 in band)
        assertTrue(6.25 in band)
        assertFalse(3.5 in band)
        assertFalse(6.5 in band)
    }
}
