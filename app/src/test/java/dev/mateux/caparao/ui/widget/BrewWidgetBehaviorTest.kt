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
    fun suggestsEspressoAfterFrenchPressAndNeverSuggestsFrenchPress() {
        assertEquals(BrewMode.FILTER, suggestedModeAfter(BrewMode.ESPRESSO))
        assertEquals(BrewMode.ESPRESSO, suggestedModeAfter(BrewMode.FILTER))
        assertEquals(BrewMode.ESPRESSO, suggestedModeAfter(BrewMode.FRENCH_PRESS))
        assertEquals(LauncherShortcut.FILTER, shortcutForWidget(BrewMode.ESPRESSO))
        assertEquals(LauncherShortcut.ESPRESSO, shortcutForWidget(BrewMode.FILTER))
        assertEquals(LauncherShortcut.ESPRESSO, shortcutForWidget(BrewMode.FRENCH_PRESS))
        assertEquals(LauncherShortcut.WIDGET_HOME, shortcutForWidget(null))
        assertEquals(LauncherShortcut.FILTER, LauncherShortcut.fromAction(shortcutForWidget(BrewMode.ESPRESSO).action))
    }

    @Test
    fun actionIconMatchesSuggestedModeAndIsAbsentForTheMethodChooser() {
        assertEquals(R.drawable.ic_coffee_maker, actionIconForWidget(BrewMode.ESPRESSO))
        assertEquals(R.drawable.ic_coffee, actionIconForWidget(BrewMode.FILTER))
        assertEquals(R.drawable.ic_coffee, actionIconForWidget(BrewMode.FRENCH_PRESS))
        assertNull(actionIconForWidget(null))
    }

    @Test
    fun emptyWidgetUsesATitleAndGenericAction() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val english = localized(base, Locale.US)
        val portuguese = localized(base, Locale.forLanguageTag("pt-BR"))

        assertEquals("You haven't made coffee yet", english.getString(R.string.widget_empty_title))
        assertEquals("Você ainda não fez café", portuguese.getString(R.string.widget_empty_title))
        assertEquals("Brew a coffee", english.getString(R.string.widget_brew_coffee))
        assertEquals("Preparar um café", portuguese.getString(R.string.widget_brew_coffee))
    }

    private fun localized(context: Context, locale: Locale): Context =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) })
}
