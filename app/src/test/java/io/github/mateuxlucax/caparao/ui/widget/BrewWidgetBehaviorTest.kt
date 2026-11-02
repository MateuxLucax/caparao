package io.github.mateuxlucax.caparao.ui.widget

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.ui.launcher.LauncherShortcut
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrewWidgetBehaviorTest {
    @Test
    fun alternatesBetweenEspressoAndFilterAndReusesLauncherActions() {
        assertEquals(BrewMode.FILTER, suggestedModeAfter(BrewMode.ESPRESSO))
        assertEquals(BrewMode.ESPRESSO, suggestedModeAfter(BrewMode.FILTER))
        assertEquals(LauncherShortcut.FILTER, shortcutForWidget(BrewMode.ESPRESSO))
        assertEquals(LauncherShortcut.ESPRESSO, shortcutForWidget(BrewMode.FILTER))
        assertEquals(LauncherShortcut.WIDGET_HOME, shortcutForWidget(null))
        assertEquals(LauncherShortcut.FILTER, LauncherShortcut.fromAction(shortcutForWidget(BrewMode.ESPRESSO).action))
    }

    @Test
    fun elapsedAgeUsesTheBrewEndAndLocalCalendarBoundaries() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val endBeforeMidnight = millis("2026-11-01T23:59:00-03:00")
        val twoMinutesAfterMidnight = millis("2026-11-02T00:01:00-03:00")
        val nextDay = millis("2026-11-02T23:59:00-03:00")
        val secondDay = millis("2026-11-03T00:00:00-03:00")

        assertEquals(BrewAge.Yesterday, brewAge(endBeforeMidnight, twoMinutesAfterMidnight, zone))
        assertEquals(BrewAge.Today, brewAge(twoMinutesAfterMidnight, nextDay, zone))
        assertEquals(BrewAge.DaysAgo(2), brewAge(endBeforeMidnight, secondDay, zone))
        assertEquals(BrewAge.Today, brewAge(secondDay, endBeforeMidnight, zone))
    }

    @Test
    fun relativeLabelsFollowEnglishAndBrazilianPortugueseResources() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val english = localized(base, Locale.US)
        val portuguese = localized(base, Locale("pt", "BR"))

        assertEquals("Saved today", english.getString(R.string.widget_saved_today))
        assertEquals("Salvo hoje", portuguese.getString(R.string.widget_saved_today))
        assertEquals("Saved 2 days ago", english.resources.getQuantityString(R.plurals.widget_saved_days, 2, 2))
        assertEquals("Salvo há 2 dias", portuguese.resources.getQuantityString(R.plurals.widget_saved_days, 2, 2))
    }

    private fun localized(context: Context, locale: Locale): Context =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) })

    private fun millis(value: String): Long = ZonedDateTime.parse(value).toInstant().toEpochMilli()
}
