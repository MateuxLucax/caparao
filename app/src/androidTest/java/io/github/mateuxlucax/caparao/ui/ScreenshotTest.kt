package io.github.mateuxlucax.caparao.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mateuxlucax.caparao.CaparaoApplication
import io.github.mateuxlucax.caparao.MainActivity
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.data.local.BuiltInRecipes
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.ui.format.Format
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Captures the onboarding and every tab for the README and docs/screenshots, with a recorded session as the scale.
 * Runs only when scripts/screenshots.sh passes the `screenshots` argument (the variant name, e.g. `en-light`); the
 * script sets the language and theme beforehand.
 */
@OptIn(ExperimentalTestApi::class)
class ScreenshotTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private val variant: String? = InstrumentationRegistry.getArguments().getString("screenshots")
    private val dir get() = File(context.getExternalFilesDir(null), "screenshots/$variant").apply { mkdirs() }

    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(800) // let the ripple, tab indicator and live weight settle
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** For a step that lasts under a second (taring): no settle time, just past the slide-in. */
    private fun captureNow(name: String) {
        Thread.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Sleeps until [atMs] after [startMs] (the replay's start), so taps land at a set point of the session. */
    private fun sleepUntil(startMs: Long, atMs: Long) = Thread.sleep((startMs + atMs - SystemClock.elapsedRealtime()).coerceAtLeast(0))

    private fun click(id: Int) = compose.onNodeWithText(context.getString(id)).performClick()

    private fun waitFor(id: Int, timeoutMs: Long) = compose.waitUntilAtLeastOneExists(hasText(context.getString(id)), timeoutMs)

    private fun waitForConnected() =
        compose.waitUntilAtLeastOneExists(hasContentDescription(context.getString(R.string.scale_connected)), 5_000)

    /**
     * A fresh start: no brews, only the built-in recipes, no settings, onboarding as given. The database is emptied in
     * place: deleting the file under a Room instance an earlier test opened leaves it without tables.
     */
    private fun freshApp(onboardingDone: Boolean) {
        val container = (context.applicationContext as CaparaoApplication).container
        container.database.clearAllTables()
        BuiltInRecipes.insert(container.database.openHelper.writableDatabase, System.currentTimeMillis())
        // No server, no remembered dose: whatever an earlier run or a manual test left behind.
        SettingsStore.open(context).edit().clear().commit()
        container.settings.onboardingDone = onboardingDone
    }

    private fun launch(session: String, atMs: Long) = ActivityScenario.launch<MainActivity>(
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_REPLAY, session)
            .putExtra(MainActivity.EXTRA_REPLAY_AT_MS, atMs),
    )

    @Test
    fun onboardingAndEveryTab() {
        assumeTrue("screenshots run only from scripts/screenshots.sh", variant != null)
        freshApp(onboardingDone = false)
        launch("press-ramp", 38_300).use {
            capture("onboarding-welcome")
            click(R.string.get_started)
            capture("onboarding-permissions")
            click(R.string.skip)
            waitFor(R.string.scale_connected, 5_000)
            capture("onboarding-scale")
            click(R.string.next)
            capture("onboarding-server")
            click(R.string.skip)
            Tab.entries.forEach { tab ->
                click(tab.label)
                capture(tab.name.lowercase())
            }
            // The built-in V60 recipe in the editor: its name, then its method on the card.
            click(R.string.tab_recipes)
            compose.onAllNodesWithText("V60").onFirst().performClick()
            capture("recipe-editor")
        }
    }

    /** An espresso from the synthetic session (cup already on the scale), then the list and the brew's chart. */
    @Test
    fun espresso() {
        assumeTrue("screenshots run only from scripts/screenshots.sh", variant != null)
        freshApp(onboardingDone = true)
        launch("synthetic-espresso", 3_000).use {
            waitForConnected()
            click(R.string.mode_espresso)
            // Before typing: a focused field brings up the emulator's keyboard bar.
            capture("brew-dose")
            compose.onNode(hasSetTextAction()).performTextReplacement("18")
            click(R.string.next)
            // The shot starts 14 s into the session (11 s after this replay's start) and pours for about 25 s.
            Thread.sleep(18_000)
            capture("brew-live")
            waitFor(R.string.brew_saved, 40_000)
            capture("brew-summary")
            click(R.string.close)
            click(R.string.tab_brews)
            capture("brews-list")
            compose.onNodeWithText(context.getString(R.string.ratio_value, Format.ratio(2.0, context.resources.configuration.locales[0])), substring = true).performClick()
            capture("brew-detail")
        }
    }

    /** The espresso guide's steps before the shot: cup, taring, waiting for the first drop. */
    @Test
    fun espressoGuide() {
        assumeTrue("screenshots run only from scripts/screenshots.sh", variant != null)
        freshApp(onboardingDone = true)
        launch("synthetic-espresso-guide", 0).use {
            waitForConnected()
            click(R.string.mode_espresso)
            compose.onNode(hasSetTextAction()).performTextReplacement("18")
            click(R.string.next)
            capture("brew-step-cup")
            // The cup lands at 8 s and is tared as soon as it is still.
            waitFor(R.string.taring, 15_000)
            captureNow("brew-step-taring")
            waitFor(R.string.espresso_ready, 5_000)
            capture("brew-step-first-drop")
        }
    }

    /** A filter brew: taring, pouring, brewing, then the server lifted and done tapped 2 s later; its summary and chart. */
    @Test
    fun filter() {
        assumeTrue("screenshots run only from scripts/screenshots.sh", variant != null)
        freshApp(onboardingDone = true)
        launch("synthetic-filter-lift", 0).use {
            val start = SystemClock.elapsedRealtime()
            waitForConnected()
            click(R.string.mode_filter)
            compose.onNode(hasSetTextAction()).performTextReplacement("15")
            click(R.string.next)
            waitFor(R.string.taring, 2_000)
            captureNow("brew-filter-taring")
            waitFor(R.string.filter_ready, 5_000)
            capture("brew-filter-pour")
            // Pours from 20 s, the last one ends at 88 s and everything leaves the scale at 90 s.
            sleepUntil(start, 60_000)
            capture("brew-filter-live")
            sleepUntil(start, 93_000)
            click(R.string.done)
            waitFor(R.string.brew_saved, 5_000)
            capture("brew-filter-summary")
            click(R.string.close)
            click(R.string.tab_brews)
            val locale = context.resources.configuration.locales[0]
            compose.onNodeWithText("${Format.grams(15.0, locale)} ${context.getString(R.string.unit_grams)} →", substring = true).performClick()
            capture("brew-filter-detail")
        }
    }
}
