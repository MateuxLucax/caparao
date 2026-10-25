package io.github.mateuxlucax.caparao.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mateuxlucax.caparao.CaparaoApplication
import io.github.mateuxlucax.caparao.MainActivity
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.data.local.BrewDatabase
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

    private fun click(id: Int) = compose.onNodeWithText(context.getString(id)).performClick()

    private fun waitFor(id: Int, timeoutMs: Long) = compose.waitUntilAtLeastOneExists(hasText(context.getString(id)), timeoutMs)

    /** A fresh start: no brews, no settings, onboarding as given. Runs before the container opens the database. */
    private fun freshApp(onboardingDone: Boolean) {
        context.deleteDatabase(BrewDatabase.NAME)
        // No server, no remembered dose: whatever an earlier run or a manual test left behind.
        SettingsStore.open(context).edit().clear().commit()
        (context.applicationContext as CaparaoApplication).container.settings.onboardingDone = onboardingDone
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
        }
    }

    /** An espresso from the synthetic session (cup already on the scale), then the list and the brew's chart. */
    @Test
    fun espresso() {
        assumeTrue("screenshots run only from scripts/screenshots.sh", variant != null)
        freshApp(onboardingDone = true)
        launch("synthetic-espresso", 3_000).use {
            waitFor(R.string.scale_connected, 5_000)
            click(R.string.mode_espresso)
            compose.onNode(hasSetTextAction()).performTextReplacement("18")
            capture("brew-dose")
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
}
