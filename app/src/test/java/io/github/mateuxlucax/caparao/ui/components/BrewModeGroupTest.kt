package io.github.mateuxlucax.caparao.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class BrewModeGroupTest(
    private val filterLabel: String,
    private val espressoLabel: String,
    private val frenchPressLabel: String,
) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun allModesStayVisibleAndShareTheTallestLabelSizeAtCompactWidthAndLargeText() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.3f)) {
                AppTheme {
                    Box(Modifier.width(320.dp).padding(horizontal = 16.dp)) {
                        BrewModeGroup(selected = BrewMode.FRENCH_PRESS, onSelect = {}, icons = true)
                    }
                }
            }
        }

        val buttons = listOf(filterLabel, espressoLabel, frenchPressLabel).map { label ->
            compose.onNode(hasText(label)).fetchSemanticsNode()
        }
        assertEquals("all three modes remain in the compact selector", 3, buttons.size)
        val first = buttons.first().boundsInRoot
        buttons.drop(1).forEach { button ->
            assertEquals("segments have balanced widths", first.width, button.boundsInRoot.width, 1.1f)
            assertEquals("segments fill the same natural height", first.height, button.boundsInRoot.height, 0.5f)
        }
        compose.onNodeWithText(filterLabel).assertIsDisplayed()
        compose.onNodeWithText(espressoLabel).assertIsDisplayed()
        compose.onNodeWithText(frenchPressLabel).assertIsDisplayed()
    }
}

@Config(qualifiers = "en")
class BrewModeGroupEnglishTest : BrewModeGroupTest("Filter", "Espresso", "French press")

@Config(qualifiers = "pt-rBR")
class BrewModeGroupPortugueseTest : BrewModeGroupTest("Filtrado", "Espresso", "Prensa francesa")
