package io.github.mateuxlucax.caparao.wear

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class WatchFormatTest {
    private val brazil = Locale.forLanguageTag("pt-BR")

    @Test
    fun weightAndFlowUseTheLocale() {
        assertEquals("36.4 g", WatchFormat.grams(36.43f, Locale.US))
        assertEquals("36,4 g", WatchFormat.grams(36.43f, brazil))
        assertEquals("1,8 g/s", WatchFormat.flow(1.8f, brazil))
        assertEquals("0.0 g", WatchFormat.grams(-0.04f, Locale.US))
        assertEquals("-0.3 g", WatchFormat.grams(-0.3f, Locale.US))
    }

    @Test
    fun timeIsMinutesAndSeconds() {
        assertEquals("0:00", WatchFormat.time(0))
        assertEquals("0:27", WatchFormat.time(27_900))
        assertEquals("3:05", WatchFormat.time(185_000))
    }
}
