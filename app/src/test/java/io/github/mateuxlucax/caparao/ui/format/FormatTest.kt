package io.github.mateuxlucax.caparao.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormatTest {
    private val english = Locale.US
    private val portuguese = Locale.forLanguageTag("pt-BR")

    @Test
    fun gramsUseTheScaleResolutionAndTheLocaleSeparator() {
        assertEquals("18.0", Format.grams(18.0, english))
        assertEquals("36,4", Format.grams(36.4, portuguese))
        assertEquals("-0.3", Format.grams(-0.3, english))
        assertEquals("0.1", Format.grams(0.05, english))
    }

    @Test
    fun flowHasOneDecimal() {
        assertEquals("1.3", Format.flow(1.27, english))
        assertEquals("2,0", Format.flow(2.0, portuguese))
    }

    @Test
    fun timerIsMinutesAndSeconds() {
        assertEquals("00:00", Format.timer(0))
        assertEquals("00:28", Format.timer(28))
        assertEquals("03:05", Format.timer(185))
        assertEquals("61:01", Format.timer(3661))
        assertEquals("00:00", Format.timer(-3))
    }

    @Test
    fun ratioHasOneDecimalInTheLocalesStyle() {
        assertEquals("2.0", Format.ratio(2.0, Locale.US))
        assertEquals("15,7", Format.ratio(15.67, Locale.forLanguageTag("pt-BR")))
    }
}
