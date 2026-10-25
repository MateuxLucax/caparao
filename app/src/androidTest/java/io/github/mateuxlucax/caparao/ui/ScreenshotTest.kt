package io.github.mateuxlucax.caparao.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mateuxlucax.caparao.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Captures every tab for the README and docs/screenshots. Runs only when scripts/screenshots.sh passes the
 * `screenshots` argument (the variant name, e.g. `en-light`); the script sets the language and theme beforehand.
 */
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun everyTab() {
        val variant = InstrumentationRegistry.getArguments().getString("screenshots")
        assumeTrue("screenshots run only from scripts/screenshots.sh", variant != null)
        val dir = File(compose.activity.getExternalFilesDir(null), "screenshots/$variant").apply { mkdirs() }

        Tab.entries.forEach { tab ->
            compose.onNodeWithText(compose.activity.getString(tab.label)).performClick()
            compose.waitForIdle()
            Thread.sleep(500) // let the ripple and tab indicator settle
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(dir, "${tab.name.lowercase()}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
