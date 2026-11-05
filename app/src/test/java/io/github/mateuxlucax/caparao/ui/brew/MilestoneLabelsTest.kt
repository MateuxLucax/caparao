package io.github.mateuxlucax.caparao.ui.brew

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The five espresso milestone names on a 320 dp phone: each fits its weighted segment on one line at the minimum size. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class MilestoneLabelsTest(private val names: List<String>) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fiveNamesFitOneLineAtDefaultText() = assertFit(fontScale = 1f)

    @Test
    fun fiveNamesFitOneLineAtLargeText() = assertFit(fontScale = 1.3f)

    @Test
    fun fiveNamesFitOneLineAtVeryLargeText() = assertFit(fontScale = 2f)

    private fun assertFit(fontScale: Float) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                AppTheme {
                    // 320 dp wide, with the 24 dp side padding the brew screen gives the indicator.
                    Box(Modifier.width(320.dp)) {
                        GuideIndicator(BrewMode.ESPRESSO, GuideStage.FIRST_DROP, Modifier.padding(horizontal = 24.dp))
                    }
                }
            }
        }

        names.forEach { name ->
            val node = compose.onNodeWithText(name, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode()
            val layout = layoutOf(node)
            assertFalse("$name overflows its segment at ${fontScale}× text", layout.didOverflowWidth)
            assertEquals("$name stays on one line at ${fontScale}× text", 1, layout.lineCount)
            assertTrue(
                "$name renders at least ${MilestoneLabelMinRenderedSize.value} sp at ${fontScale}× text",
                layout.layoutInput.style.fontSize.value * fontScale >= MilestoneLabelMinRenderedSize.value - 0.01f,
            )
        }
    }

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        checkNotNull(node.config[SemanticsActions.GetTextLayoutResult].action).invoke(results)
        return results.single()
    }
}

@Config(qualifiers = "en-w320dp-h640dp-notnight-xxhdpi")
class MilestoneLabelsEnglishTest : MilestoneLabelsTest(listOf("Recipe", "Beans", "Portafilter", "Shot", "Done"))

@Config(qualifiers = "pt-rBR-w320dp-h640dp-notnight-xxhdpi")
class MilestoneLabelsPortugueseTest : MilestoneLabelsTest(listOf("Receita", "Grãos", "Porta-filtro", "Extração", "Concluído"))

/** Narrower than the names can fit in pt-BR: the indicator shows the current name alone instead of clipping the rest. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "pt-rBR-w280dp-h640dp-notnight-xxhdpi")
class MilestoneLabelsNarrowPortugueseTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsOnlyTheCurrentNameWhenTheOthersCannotFit() {
        compose.setContent {
            AppTheme {
                Box(Modifier.width(280.dp)) {
                    GuideIndicator(BrewMode.ESPRESSO, GuideStage.FIRST_DROP, Modifier.padding(horizontal = 24.dp))
                }
            }
        }

        compose.onNodeWithText("Extração", useUnmergedTree = true).assertIsDisplayed()
        listOf("Receita", "Grãos", "Porta-filtro", "Concluído").forEach { name ->
            compose.onAllNodesWithText(name, useUnmergedTree = true).assertCountEquals(0)
        }
    }
}
