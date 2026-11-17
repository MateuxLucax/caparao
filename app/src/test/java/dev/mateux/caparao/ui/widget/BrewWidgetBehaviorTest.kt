package dev.mateux.caparao.ui.widget

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import dev.mateux.caparao.R
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.ui.components.LauncherShortcut
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrewWidgetBehaviorTest {
    @Test
    fun shortcutRepeatsTheLastMethod() {
        assertEquals(LauncherShortcut.ESPRESSO, shortcutForWidget(BrewMode.ESPRESSO))
        assertEquals(LauncherShortcut.FILTER, shortcutForWidget(BrewMode.FILTER))
        assertEquals(LauncherShortcut.FRENCH_PRESS, shortcutForWidget(BrewMode.FRENCH_PRESS))
        assertEquals(LauncherShortcut.WIDGET_HOME, shortcutForWidget(null))
        assertEquals(LauncherShortcut.FILTER, LauncherShortcut.fromAction(shortcutForWidget(BrewMode.FILTER).action))
    }

    @Test
    fun methodIconMatchesLastBrew() {
        assertEquals(R.drawable.ic_coffee, actionIconForWidget(BrewMode.ESPRESSO))
        assertEquals(R.drawable.ic_coffee_maker, actionIconForWidget(BrewMode.FILTER))
        assertEquals(R.drawable.ic_french_press, actionIconForWidget(BrewMode.FRENCH_PRESS))
        assertNull(actionIconForWidget(null))
    }

    @Test
    fun emptyWidgetUsesLocalizedEmptyAndStartLabels() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val english = localized(base, Locale.US)
        val portuguese = localized(base, Locale.forLanguageTag("pt-BR"))

        assertEquals("No brews yet", english.getString(R.string.widget_empty_title_yet))
        assertEquals("Nenhum preparo ainda", portuguese.getString(R.string.widget_empty_title_yet))
        assertEquals("Start a brew", english.getString(R.string.widget_empty_start))
        assertEquals("Iniciar", portuguese.getString(R.string.widget_empty_start))
    }

    private fun localized(context: Context, locale: Locale): Context =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) })
}
