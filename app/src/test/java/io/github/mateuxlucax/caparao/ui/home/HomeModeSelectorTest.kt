package io.github.mateuxlucax.caparao.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
abstract class HomeModeSelectorTest(
    private val visibleLabels: List<String>,
    private val accessibleNames: List<String>,
) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun allFourShortcutsFitAndRemainAccessibleAtCompactWidthAndLargeText() {
        val launched = mutableListOf<BrewMode>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.3f)) {
                AppTheme {
                    Box(Modifier.width(280.dp)) {
                        HomeModeSelector(connected = true, onBrew = launched::add)
                    }
                }
            }
        }

        val buttons = accessibleNames.map { name ->
            compose.onNodeWithContentDescription(name).assertIsDisplayed().fetchSemanticsNode()
        }
        val first = buttons.first().boundsInRoot
        buttons.drop(1).forEach { button ->
            assertEquals("shortcuts share balanced widths", first.width, button.boundsInRoot.width, 1.1f)
            assertEquals("shortcuts share the same touch height", first.height, button.boundsInRoot.height, 1.1f)
        }
        val labels = visibleLabels.map { label ->
            compose.onNodeWithText(label).assertIsDisplayed()
            compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        }
        val firstLabel = labels.first()
        labels.drop(1).forEach { label ->
            assertEquals("short labels fit on one line at 1.3× text", firstLabel.height, label.height, 0.5f)
        }

        compose.onNodeWithContentDescription(accessibleNames.first()).assertIsSelected()
        compose.onNodeWithContentDescription(accessibleNames.last()).performClick()
        assertEquals(listOf(BrewMode.FRENCH_PRESS), launched)
    }
}

@Config(qualifiers = "en-w320dp-h640dp-notnight-xxhdpi")
class HomeModeSelectorEnglishTest : HomeModeSelectorTest(
    visibleLabels = listOf("Manual", "Filter", "Espresso", "Press"),
    accessibleNames = listOf("Manual scale", "Filter", "Espresso", "French press"),
)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-notnight-xxhdpi")
class HomeModeSelectorPortugueseTest : HomeModeSelectorTest(
    visibleLabels = listOf("Manual", "Filtrado", "Espresso", "Prensa"),
    accessibleNames = listOf("Balança manual", "Filtrado", "Espresso", "Prensa francesa"),
)
