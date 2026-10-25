package io.github.mateuxlucax.caparao.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mateuxlucax.caparao.CaparaoApplication
import io.github.mateuxlucax.caparao.MainActivity
import io.github.mateuxlucax.caparao.R
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

    @Test
    fun onboardingAndEveryTab() {
        val variant = InstrumentationRegistry.getArguments().getString("screenshots")
        assumeTrue("screenshots run only from scripts/screenshots.sh", variant != null)
        val dir = File(context.getExternalFilesDir(null), "screenshots/$variant").apply { mkdirs() }
        fun capture(name: String) {
            compose.waitForIdle()
            Thread.sleep(800) // let the ripple, tab indicator and live weight settle
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        fun click(id: Int) = compose.onNodeWithText(context.getString(id)).performClick()

        (context.applicationContext as CaparaoApplication).container.settings.onboardingDone = false
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_REPLAY, "press-ramp")
            .putExtra(MainActivity.EXTRA_REPLAY_AT_MS, 38_300L)
        ActivityScenario.launch<MainActivity>(intent).use {
            capture("onboarding-welcome")
            click(R.string.get_started)
            capture("onboarding-permissions")
            click(R.string.skip)
            compose.waitUntilAtLeastOneExists(hasText(context.getString(R.string.scale_connected)), 5_000)
            capture("onboarding-scale")
            click(R.string.finish)
            Tab.entries.forEach { tab ->
                click(tab.label)
                capture(tab.name.lowercase())
            }
        }
    }
}
