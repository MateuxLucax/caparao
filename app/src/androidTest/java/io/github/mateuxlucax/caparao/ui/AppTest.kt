package io.github.mateuxlucax.caparao.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test

/** The tab shell on a device: every tab opens its screen, and Home is inert without a scale. */
class AppTest {
    @get:Rule
    val compose = createComposeRule()

    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun tabsSwitchScreens() {
        compose.setContent { AppTheme { App("1.0-test") } }

        compose.onNodeWithText(text(R.string.scale_disconnected)).assertExists()
        compose.onNodeWithText(text(R.string.mode_espresso)).assertExists()

        compose.onNodeWithText(text(R.string.tab_brews)).performClick()
        compose.onNodeWithText(text(R.string.brews_empty_title)).assertExists()

        compose.onNodeWithText(text(R.string.tab_settings)).performClick()
        compose.onNodeWithText(text(R.string.section_scale)).assertExists()
        compose.onNodeWithText("1.0-test", substring = true).assertExists()

        compose.onNodeWithText(text(R.string.tab_home)).performClick()
        compose.onNodeWithText(text(R.string.tare)).assertIsNotEnabled()
    }
}
