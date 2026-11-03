package io.github.mateuxlucax.caparao.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.ui.components.rememberModeLabelSize
import io.github.mateuxlucax.caparao.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class BrewModeGroupTest(
    private val screenWidth: Int,
    private val shortLabels: List<String>,
    private val accessibleNames: List<String>,
) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun threeModesFitOneLineAtDefaultText() = assertGroup(fontScale = 1f)

    @Test
    fun threeModesFitOneLineAtLargeText() = assertGroup(fontScale = 1.3f)

    @Test
    fun threeModesFitOneLineAtVeryLargeText() = assertGroup(fontScale = 2f)

    private fun assertGroup(fontScale: Float) {
        val selectedFontSizeSp = AtomicReference(0f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                AppTheme {
                    val textWidth = ((screenWidth.dp - 40.dp - ButtonGroupDefaults.ConnectedSpaceBetween * 2) / 3) - 2.dp
                    val labelSize = rememberModeLabelSize(shortLabels, textWidth, MaterialTheme.typography.labelSmall)
                    SideEffect { selectedFontSizeSp.set(labelSize.value) }
                    Box(Modifier.width(screenWidth.dp).padding(horizontal = 20.dp)) {
                        BrewModeGroup(selected = BrewMode.FRENCH_PRESS, onSelect = {})
                    }
                }
            }
        }

        val buttons = accessibleNames.map { name ->
            compose.onNodeWithContentDescription(name).assertIsDisplayed().fetchSemanticsNode()
        }
        assertEquals("only the three real brew modes are shown", 3, buttons.size)
        val first = buttons.first().boundsInRoot
        buttons.drop(1).forEach { button ->
            assertEquals("segments have balanced widths", first.width, button.boundsInRoot.width, 1.1f)
            assertEquals("segments fill the same height", first.height, button.boundsInRoot.height, 0.5f)
        }
        assertTrue("mode buttons keep a usable touch height", first.height >= 48f)

        val labels = shortLabels.map { label ->
            compose.onNodeWithText(label).assertIsDisplayed()
            val node = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            node.boundsInRoot
        }
        val lineHeight = labels.first().height
        labels.drop(1).forEach { label ->
            assertEquals("short labels keep one shared line height at ${fontScale}× text", lineHeight, label.height, 0.5f)
        }
        assertTrue(
            "labels remain at least 13 rendered sp at ${fontScale}× text",
            selectedFontSizeSp.get() * fontScale >= 13f,
        )
        labels.forEachIndexed { index, label ->
            assertTrue("label stays inside its segment", label.left >= buttons[index].boundsInRoot.left - 0.5f)
            assertTrue("label stays inside its segment", label.right <= buttons[index].boundsInRoot.right + 0.5f)
        }

        // Manual is the scale screen on Home, never a brew mode here.
        compose.onAllNodesWithText("Manual").assertCountEquals(0)
        compose.onNodeWithContentDescription(accessibleNames.last()).assertIsSelected()
    }
}

@Config(qualifiers = "en-w320dp-h640dp-notnight-xxhdpi")
class BrewModeGroupEnglish320Test : BrewModeGroupTest(
    screenWidth = 320,
    shortLabels = listOf("Filter", "Espresso", "Press"),
    accessibleNames = listOf("Filter", "Espresso", "French press"),
)

@Config(qualifiers = "en-w360dp-h640dp-notnight-xxhdpi")
class BrewModeGroupEnglish360Test : BrewModeGroupTest(
    screenWidth = 360,
    shortLabels = listOf("Filter", "Espresso", "Press"),
    accessibleNames = listOf("Filter", "Espresso", "French press"),
)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-notnight-xxhdpi")
class BrewModeGroupPortuguese320Test : BrewModeGroupTest(
    screenWidth = 320,
    shortLabels = listOf("Filtrado", "Espresso", "Prensa"),
    accessibleNames = listOf("Filtrado", "Espresso", "Prensa francesa"),
)

@Config(qualifiers = "pt-rBR-w360dp-h640dp-notnight-xxhdpi")
class BrewModeGroupPortuguese360Test : BrewModeGroupTest(
    screenWidth = 360,
    shortLabels = listOf("Filtrado", "Espresso", "Prensa"),
    accessibleNames = listOf("Filtrado", "Espresso", "Prensa francesa"),
)
