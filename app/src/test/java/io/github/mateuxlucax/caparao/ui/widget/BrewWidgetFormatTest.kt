package io.github.mateuxlucax.caparao.ui.widget

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrewWidgetFormatTest {
    private val base = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun beverageVolumeUsesMillilitersUntilOneLiterThenFormatsLiters() {
        val english = localized(Locale.US)
        val portuguese = localized(Locale.forLanguageTag("pt-BR"))

        assertEquals("999 ml", BrewWidgetFormat.beverageVolume(english, 999.0))
        assertEquals("1 L", BrewWidgetFormat.beverageVolume(english, 1_000.0))
        assertEquals("1.2 L", BrewWidgetFormat.beverageVolume(english, 1_234.0))
        assertEquals("999 ml", BrewWidgetFormat.beverageVolume(portuguese, 999.0))
        assertEquals("1 L", BrewWidgetFormat.beverageVolume(portuguese, 1_000.0))
        assertEquals("1,2 L", BrewWidgetFormat.beverageVolume(portuguese, 1_234.0))
    }

    @Test
    fun groupedTotalsUseTheWidgetLocale() {
        val english = localized(Locale.US)
        val portuguese = localized(Locale.forLanguageTag("pt-BR"))

        assertEquals("1,234 mg", BrewWidgetFormat.caffeine(english, 1_234.0))
        assertEquals("1.234 mg", BrewWidgetFormat.caffeine(portuguese, 1_234.0))
        assertEquals("1,234 g", BrewWidgetFormat.dose(english, 1_234.0))
        assertEquals("1.234 g", BrewWidgetFormat.dose(portuguese, 1_234.0))
    }

    private fun localized(locale: Locale): Context =
        base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(locale) })
}
