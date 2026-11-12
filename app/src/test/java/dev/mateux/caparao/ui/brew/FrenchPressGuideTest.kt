package dev.mateux.caparao.ui.brew

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mateux.caparao.brew.BrewPhase
import dev.mateux.caparao.brew.StepState
import dev.mateux.caparao.data.local.BuiltInRecipes
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The French press guide names the stage the user is in: steep with the timer until 4:00, then removal, and only after
 * the press is off the scale the press-and-serve instruction (#126). Never "press the plunger at 4:00" while steeping.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class FrenchPressGuideTest(
    private val steepLabel: String,
    private val steepText: String,
    private val removeText: String,
    private val serveText: String,
    private val prematurePress: String,
    private val timeUp: String,
) {
    @get:Rule
    val compose = createComposeRule()

    private val recipe = BuiltInRecipes.frenchPress(0)

    private fun state(index: Int, removed: Boolean = false) = BrewUiState(
        mode = BrewMode.FRENCH_PRESS,
        step = BrewStep.LIVE,
        phase = BrewPhase.BREWING,
        weightGrams = 300.0,
        dose = 20.0,
        elapsedMs = if (index < recipe.steps.size) 78_000 else 245_000,
        recipe = recipe,
        guide = StepState(
            index = index,
            progress = if (index < recipe.steps.size) 0.33f else 1f,
            dueInMs = if (index < recipe.steps.size) 162_000 else null,
            pace = null,
            paceMs = null,
            overshootGrams = null,
        ),
        frenchPressRemoved = removed,
    )

    private fun show(state: BrewUiState) {
        compose.setContent { AppTheme { GuidedStep(state, onDone = {}) } }
    }

    @Test
    fun steepingShowsTheCurrentWaitAndNoPressInstruction() {
        show(state(recipe.steps.lastIndex))
        // "Let it steep" names the step: no separate stage label (#302).
        compose.onAllNodesWithText(steepLabel).assertCountEquals(0)
        compose.onNodeWithText(steepText).assertIsDisplayed()
        compose.onNodeWithText("02:42").assertIsDisplayed()
        compose.onAllNodesWithText(prematurePress, substring = true).assertCountEquals(0)
        compose.onNodeWithText(removeText).assertDoesNotExist()
        compose.onNodeWithText(serveText).assertDoesNotExist()
    }

    @Test
    fun theDeadlineAsksToRemoveThePressFirst() {
        show(state(recipe.steps.size))
        compose.onNodeWithText(removeText).assertIsDisplayed()
        compose.onNodeWithText(timeUp).assertIsDisplayed()
        compose.onNodeWithText("+00:05").assertIsDisplayed()
        compose.onAllNodesWithText(prematurePress, substring = true).assertCountEquals(0)
        compose.onNodeWithText(serveText).assertDoesNotExist()
    }

    @Test
    fun pressingAndServingComeOnlyAfterRemoval() {
        show(state(recipe.steps.size, removed = true))
        compose.onNodeWithText(serveText).assertIsDisplayed()
        compose.onNodeWithText(removeText).assertDoesNotExist()
    }
}

@Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
class FrenchPressGuideEnglishTest : FrenchPressGuideTest(
    steepLabel = "Steep",
    steepText = "Let it steep",
    removeText = "Remove the press from the scale.",
    serveText = "Press and serve",
    prematurePress = "Press the plunger",
    timeUp = "Time’s up",
)

@Config(qualifiers = "pt-rBR-w411dp-h891dp-xxhdpi")
class FrenchPressGuidePortugueseTest : FrenchPressGuideTest(
    steepLabel = "Infusão",
    steepText = "Deixe em infusão",
    removeText = "Tire a prensa da balança.",
    serveText = "Abaixe o êmbolo e sirva",
    prematurePress = "Abaixe o êmbolo às",
    timeUp = "Tempo esgotado",
)
