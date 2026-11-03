package io.github.mateuxlucax.caparao.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
abstract class HomeModeSelectorTest(
    private val screenWidth: Int,
    private val visibleLabels: List<String>,
    private val accessibleNames: List<String>,
) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fourShortcutsFitOneLineAtDefaultText() = assertSelector(fontScale = 1f)

    @Test
    fun fourShortcutsFitOneLineAtLargeText() = assertSelector(fontScale = 1.3f)

    @Test
    fun fourShortcutsFitOneLineAtVeryLargeText() = assertSelector(fontScale = 2f)

    private fun assertSelector(fontScale: Float) {
        val launched = mutableListOf<BrewMode>()
        val selectedFontSizeSp = AtomicReference(0f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                AppTheme {
                    val textWidth = ((screenWidth.dp - 40.dp - ButtonGroupDefaults.ConnectedSpaceBetween * 3) / 4) - 2.dp
                    val labelSize = rememberModeLabelSize(visibleLabels, textWidth, MaterialTheme.typography.labelSmall)
                    SideEffect { selectedFontSizeSp.set(labelSize.value) }
                    Box(Modifier.width(screenWidth.dp).padding(horizontal = 20.dp)) {
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
        assertTrue("mode buttons keep a usable touch height", first.height >= 48f)

        val labels = visibleLabels.map { label ->
            compose.onNodeWithText(label).assertIsDisplayed()
            val node = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            node.boundsInRoot
        }
        // One shared size and maxLines = 1: every label is one line at both 320 dp and 360 dp screen widths.
        val lineHeight = labels.first().height
        labels.drop(1).forEach { label ->
            assertEquals("labels keep one shared line height at ${fontScale}× text", lineHeight, label.height, 0.5f)
        }
        assertTrue(
            "labels remain at least 13 rendered sp at ${fontScale}× text",
            selectedFontSizeSp.get() * fontScale >= 13f,
        )
        labels.forEachIndexed { index, label ->
            assertTrue("label stays inside its shortcut", label.left >= buttons[index].boundsInRoot.left - 0.5f)
            assertTrue("label stays inside its shortcut", label.right <= buttons[index].boundsInRoot.right + 0.5f)
        }

        compose.onNodeWithContentDescription(accessibleNames.first()).assertIsSelected()
        compose.onNodeWithContentDescription(accessibleNames.last()).assertIsNotSelected()
        compose.onNodeWithContentDescription(accessibleNames.last()).performClick()
        assertEquals(listOf(BrewMode.FRENCH_PRESS), launched)
    }
}

@Config(qualifiers = "en-w320dp-h640dp-notnight-xxhdpi")
class HomeModeSelectorEnglish320Test : HomeModeSelectorTest(
    screenWidth = 320,
    visibleLabels = listOf("Manual", "Filter", "Espresso", "Press"),
    accessibleNames = listOf("Manual scale", "Filter", "Espresso", "French press"),
)

@Config(qualifiers = "en-w360dp-h640dp-notnight-xxhdpi")
class HomeModeSelectorEnglish360Test : HomeModeSelectorTest(
    screenWidth = 360,
    visibleLabels = listOf("Manual", "Filter", "Espresso", "Press"),
    accessibleNames = listOf("Manual scale", "Filter", "Espresso", "French press"),
)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-notnight-xxhdpi")
class HomeModeSelectorPortuguese320Test : HomeModeSelectorTest(
    screenWidth = 320,
    visibleLabels = listOf("Manual", "Filtrado", "Espresso", "Prensa"),
    accessibleNames = listOf("Balança manual", "Filtrado", "Espresso", "Prensa francesa"),
)

@Config(qualifiers = "pt-rBR-w360dp-h640dp-notnight-xxhdpi")
class HomeModeSelectorPortuguese360Test : HomeModeSelectorTest(
    screenWidth = 360,
    visibleLabels = listOf("Manual", "Filtrado", "Espresso", "Prensa"),
    accessibleNames = listOf("Balança manual", "Filtrado", "Espresso", "Prensa francesa"),
)
