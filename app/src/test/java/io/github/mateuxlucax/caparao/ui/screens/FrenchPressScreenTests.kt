package io.github.mateuxlucax.caparao.ui.screens
import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.luminance
import io.github.mateuxlucax.caparao.ui.components.PressArt
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mateuxlucax.caparao.BrewWidgetProvider
import io.github.mateuxlucax.caparao.BuildConfig
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.data.export.BrewExportFiles
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticRecorder
import io.github.mateuxlucax.caparao.data.local.BrewDatabase
import io.github.mateuxlucax.caparao.data.local.BrewEntity
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.BuiltInRecipes
import io.github.mateuxlucax.caparao.data.local.CoffeeBagRepository
import io.github.mateuxlucax.caparao.data.local.RecipeRepository
import io.github.mateuxlucax.caparao.data.local.SeedBuiltInRecipes
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewSample
import io.github.mateuxlucax.caparao.model.CoffeeBag
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.ScaleError
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.model.StepKind
import io.github.mateuxlucax.caparao.ui.recipes.RecipeEditorViewModel
import io.github.mateuxlucax.caparao.ui.coffees.CoffeeBagDetailViewModel
import io.github.mateuxlucax.caparao.ui.coffees.CoffeeBagsViewModel
import io.github.mateuxlucax.caparao.ui.recipes.RecipesViewModel
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.ui.App
import io.github.mateuxlucax.caparao.ui.components.AppActions
import io.github.mateuxlucax.caparao.ui.Tab
import io.github.mateuxlucax.caparao.brew.StepState
import io.github.mateuxlucax.caparao.brew.Pace
import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.ui.components.BrewNumbers
import io.github.mateuxlucax.caparao.ui.brew.BrewStep
import io.github.mateuxlucax.caparao.ui.brew.BrewUiState
import io.github.mateuxlucax.caparao.ui.brew.BrewViewModel
import io.github.mateuxlucax.caparao.ui.brew.FlowChart
import io.github.mateuxlucax.caparao.ui.brew.FlowPoint
import io.github.mateuxlucax.caparao.ui.brew.GuidedStep
import io.github.mateuxlucax.caparao.ui.brews.BrewDetailViewModel
import io.github.mateuxlucax.caparao.ui.brews.BrewsViewModel
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.home.HomeViewModel
import io.github.mateuxlucax.caparao.ui.onboarding.OnboardingViewModel
import io.github.mateuxlucax.caparao.ui.settings.ScaleStatusViewModel
import io.github.mateuxlucax.caparao.ui.server.ServerViewModel
import io.github.mateuxlucax.caparao.ui.settings.ExportViewModel
import io.github.mateuxlucax.caparao.ui.settings.TargetRatioViewModel
import io.github.mateuxlucax.caparao.ui.theme.AppTheme
import io.github.mateuxlucax.caparao.ui.components.ScaleReadout
import io.github.mateuxlucax.caparao.ui.components.readableOn
import io.github.mateuxlucax.caparao.ui.components.ringStroke
import io.github.mateuxlucax.caparao.ui.components.targetBand
import io.github.mateuxlucax.caparao.ui.components.TargetBand
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestName
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenFrenchPressTests(lang: String, theme: String, compactOnly: Boolean = false) : ScreenTestSupport(lang, theme, compactOnly) {
    @Test
    fun pressArtStagesWithAnimationsOff() = pressArtStages(animated = false)

    /** The same stages with animations on: the motion ends on the same frames and the steps stay readable (#226). */
    @Test
    fun pressArtStagesWithAnimationsOn() {
        val before = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            pressArtStages(animated = true)
        } finally {
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, before)
        }
    }

    @Test
    fun guideCountdownCoversWaitSwirlAndSteep() {
        val cases = listOf(
            Triple(BrewMode.FILTER, RecipeStep(StepKind.WAIT, null, 0), R.string.guide_wait),
            Triple(BrewMode.FILTER, RecipeStep(StepKind.SWIRL, null, 0), R.string.guide_swirl),
            Triple(BrewMode.FRENCH_PRESS, RecipeStep(StepKind.PRESS, null, 240_000), R.string.guide_steep_text),
        )
        fun stateFor(index: Int): BrewUiState {
            val (mode, step) = cases[index]
            val recipe = Recipe(
                id = "screen-test-countdown-$index", name = "Countdown test", mode = mode,
                method = if (mode == BrewMode.FRENCH_PRESS) "French press" else "V60", doseGrams = 15.0, targetRatio = 16.0,
                steps = listOf(step), createdAt = 0, updatedAt = 0,
            )
            return BrewUiState(
                mode = mode,
                step = BrewStep.LIVE,
                recipe = recipe,
                guide = StepState(index = 0, progress = 0.5f, dueInMs = 10_000, pace = null, paceMs = null, overshootGrams = null),
            )
        }
        var state by mutableStateOf(stateFor(0))
        compose.setContent { AppTheme { GuidedStep(state) {} } }
        cases.forEachIndexed { index, (_, _, label) ->
            if (index > 0) {
                state = stateFor(index)
                compose.waitForIdle()
            }
            compose.onAllNodesWithText(text(label), substring = true).onFirst().assertIsDisplayed()
            compose.onNodeWithTag(if (cases[index].first == BrewMode.FRENCH_PRESS) "steep-progress" else "countdown-ring").assertIsDisplayed()
        }
    }

    /** The French press steep keeps one clock (#302): the press on its shape, the countdown and the bar to the deadline, no ring and no total elapsed note. */
    @Test
    fun frenchPressSteepShowsOneClock() {
        val recipe = Recipe(
            id = "screen-test-steep-one-clock", name = "Steep one clock", mode = BrewMode.FRENCH_PRESS, method = "French press",
            doseGrams = 20.0, targetRatio = 15.0,
            steps = listOf(RecipeStep(StepKind.BLOOM, 300.0, 0), RecipeStep(StepKind.PRESS, null, 240_000)),
            createdAt = 0, updatedAt = 0,
        )
        compose.setContent {
            AppTheme {
                GuidedStep(
                    BrewUiState(
                        mode = BrewMode.FRENCH_PRESS, step = BrewStep.LIVE, phase = BrewPhase.BREWING, connected = true,
                        weightGrams = 300.0, elapsedMs = 137_000, recipe = recipe,
                        guide = StepState(index = 1, progress = 0.5f, dueInMs = 103_000, pace = null, paceMs = null, overshootGrams = null),
                    )
                ) {}
            }
        }
        compose.onNodeWithTag("steep-press").assertIsDisplayed()
        compose.onNodeWithTag("steep-shape").assertIsDisplayed()
        compose.onNodeWithTag("steep-progress").assertIsDisplayed()
        compose.onAllNodesWithTag("countdown-ring").assertCountEquals(0)
        compose.onNodeWithText(Format.timer(240)).assertIsDisplayed()
        compose.onAllNodesWithText(Format.grams(300.0, locale), substring = true).assertCountEquals(0)
        compose.onNodeWithText(Format.timer(103)).assertIsDisplayed()
        compose.onAllNodesWithText(Format.timer(137)).assertCountEquals(0)
    }

    /** The stir step counts down with the shared ring and says the spoon is not water (#219, R3b). */
    @Test
    fun guideStirShowsTheRingAndTheSpoonHint() {
        val recipe = Recipe(
            id = "screen-test-stir", name = "Stir test", mode = BrewMode.FRENCH_PRESS, method = "French press",
            doseGrams = 20.0, targetRatio = 15.0,
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 62.0, 0),
                RecipeStep(StepKind.STIR, null, 5_000),
                RecipeStep(StepKind.PRESS, null, 240_000),
            ),
            createdAt = 0, updatedAt = 0,
        )
        compose.setContent {
            AppTheme {
                GuidedStep(
                    BrewUiState(
                        mode = BrewMode.FRENCH_PRESS, step = BrewStep.LIVE, phase = BrewPhase.BREWING, connected = true,
                        weightGrams = 62.0, elapsedMs = 8_000, recipe = recipe,
                        guide = StepState(index = 1, progress = 0.5f, dueInMs = 15_000, pace = null, paceMs = null, overshootGrams = null),
                    )
                ) {}
            }
        }
        compose.onNodeWithTag("countdown-ring").assertIsDisplayed()
        compose.onAllNodesWithText(text(R.string.guide_stir)).onFirst().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.guide_stir_text)).assertIsDisplayed()
        compose.onNodeWithText(Format.timer(15)).assertIsDisplayed()
    }

    /** The ideal drain-time bar follows the timer before, within and after its range; recipes without one stay unchanged. */
    @Test
    fun frenchPressFlowShowsOnlyWhilePouring() {
        val recipe = Recipe(
            id = "press-flow", name = "Press flow", mode = BrewMode.FRENCH_PRESS, method = "French press", doseGrams = 20.0, targetRatio = 15.0,
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 100.0, 0),
                RecipeStep(StepKind.WAIT, null, 0),
                RecipeStep(StepKind.POUR, 300.0, 60_000),
                RecipeStep(StepKind.PRESS, null, 240_000),
            ),
            createdAt = 0, updatedAt = 0,
        )
        fun state(index: Int) = BrewUiState(
            mode = BrewMode.FRENCH_PRESS,
            step = BrewStep.LIVE,
            flowGramsPerSecond = 3.0,
            recipe = recipe,
            guide = StepState(index = index, progress = 0.5f, dueInMs = 10_000, pace = null, paceMs = null, overshootGrams = null),
        )
        var current by mutableStateOf(state(0))
        compose.setContent { AppTheme { GuidedStep(current) {} } }
        listOf(0 to true, 1 to false, 2 to true, 3 to false).forEach { (index, shown) ->
            current = state(index)
            compose.waitForIdle()
            compose.onAllNodes(hasTestTag("flow-chart-no-target")).assertCountEquals(if (shown) 1 else 0)
            compose.onAllNodesWithText(context.getString(R.string.guide_flow, Format.flow(3.0, locale))).assertCountEquals(if (shown) 1 else 0)
        }
    }

    /** The French press summary leaves out the average flow, which divides the water by the whole steep (#224). */
    @Test
    fun frenchPressSummaryHidesTheAverageFlow() {
        fun brew(mode: BrewMode) = Brew("avg-flow-${mode.name}", mode, 0L, 240_000L, 20.0, 300.0, null, 1.25, 0.0, false)
        var current by mutableStateOf(brew(BrewMode.FRENCH_PRESS))
        compose.setContent { AppTheme { BrewNumbers(current) } }
        compose.onNodeWithText(text(R.string.label_time)).assertIsDisplayed()
        compose.onAllNodesWithText(text(R.string.label_flow_avg)).assertCountEquals(0)

        current = brew(BrewMode.FILTER)
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.label_flow_avg)).assertIsDisplayed()
    }

    /** The third mode selector and its preselected built-in French press recipe. */
    @Test
    fun frenchPressGuide() {
        val scale = open("synthetic-french-press", 0)
        compose.onNodeWithContentDescription(text(R.string.mode_french_press)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("French press") and isSelectable(), 5_000)
        assertMilestone(R.string.milestone_recipe)
        click(R.string.next)
        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
        assertMilestone(R.string.milestone_beans)
        compose.onNodeWithTag("weigh-step-next").performClick()

        waitFor(R.string.prepare_press_title)
        assertMilestone(R.string.milestone_prepare_press)
        val beanWeighTares = scale.tareCommands
        compose.onNodeWithTag("press-prepare-picture").assertIsDisplayed()
        listOf(R.string.prepare_rdt, R.string.prepare_grind, R.string.prepare_add_grounds, R.string.prepare_press_scale)
            .forEach { compose.onNodeWithText(text(it)).assertIsDisplayed() }
        compose.onNodeWithTag("press-prepare-ready").assertIsDisplayed().assertIsEnabled()
        play(scale, 0) // a reading after Beans cannot start the brew; the zeros after Ready finish the tare
        compose.waitForIdle()
        waitFor(R.string.prepare_press_title)
        assertEquals("only Ready starts the tare", beanWeighTares, scale.tareCommands)
        click(R.string.prepare_ready)
        assertTrue("Ready starts the tare", scale.tareCommands > beanWeighTares)

        play(scale, 80_000)
        compose.waitForIdle()
        assertMilestone(R.string.milestone_brew)
        // The milestone bar names the step; the guide carries no "Step n of m" counter.
        compose.onAllNodes(hasText(context.getString(R.string.step_of_named, 1, 4, "").substringBefore("1"), substring = true)).assertCountEquals(0)
        play(scale, 241_100)
        waitFor(R.string.guide_remove_press)
        compose.waitForIdle()
        assertMilestone(R.string.milestone_serve)
        play(scale, 255_000)
        waitFor(R.string.guide_press_serve)
        compose.waitForIdle()
        assertMilestone(R.string.milestone_serve)
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).performClick()
        waitFor(R.string.brew_saved)
        assertMilestone(R.string.milestone_done)
    }

    /** A free French press: the Steep field on the dose screen, the pour on the ring, then the steep like a recipe's (#218). */
    @Test
    fun frenchPressFreeBrewSteepsOnReachingTheTargetWater() {
        val scale = openFreePress(waterMl = "90")
        play(scale, 5_000) // the scale jumps to 100 g at 1.1 s, past the 90 g target
        waitFor(R.string.guide_steep_text)
        compose.onAllNodesWithText(text(R.string.brewing_press)).assertCountEquals(0)
    }

    /** A steep that ends before the pour does goes straight to the alert (#218). */
    @Test
    fun frenchPressFreeBrewDeadlineDuringThePourGoesStraightToTheAlert() {
        val scale = openFreePress(steep = "0:10")
        play(scale, 8_000)
        waitFor(R.string.brewing_press)
        // The session's next frame comes at 60 s: the deadline, 10 s after the first water, has long passed.
        play(scale, 61_000)
        waitFor(R.string.guide_remove_press)
        compose.onAllNodesWithText(text(R.string.guide_steep_text)).assertCountEquals(0)
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun frenchPressDoneAsksBeforeTheDeadline() {
        val scale = frenchPressSteep()
        play(scale, 80_000)
        compose.waitForIdle()
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).performClick()
        waitFor(R.string.finish_brew_title)
        compose.onNodeWithText(text(R.string.keep_brewing)).assertIsDisplayed()
    }

    /** After the deadline Done ends the brew directly (#222). */
    @Test
    fun frenchPressDoneEndsDirectlyAfterTheDeadline() {
        val scale = frenchPressSteep()
        play(scale, 241_100)
        waitFor(R.string.guide_remove_press)
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText(text(R.string.finish_brew_title)).assertCountEquals(0)
        compose.onAllNodesWithText(text(R.string.guide_remove_press)).assertCountEquals(0)
    }

    /** A press lifted before the deadline keeps the deadline copy and error color off while it is being confirmed (#279). */
    @Test
    fun frenchPressRemovalCandidatePastTheDeadlineShowsNoDeadlineState() {
        val scale = frenchPressSteep("synthetic-french-press-early-lift")
        play(scale, 241_500)
        compose.waitForIdle()

        compose.onAllNodesWithText(text(R.string.guide_remove_press)).assertCountEquals(0)
        compose.onAllNodesWithText(text(R.string.guide_time_up)).assertCountEquals(0)
        val bitmap = compose.onNodeWithTag("brew-screen").captureToImage().asAndroidBitmap()
        val deadlineColors = listOf(dynamicLightColorScheme(context).errorContainer, dynamicDarkColorScheme(context).errorContainer).map { it.toArgb() }
        assertTrue(
            "the deadline error color is off while the press is being confirmed",
            (0 until bitmap.height).none { y -> (0 until bitmap.width).any { x -> bitmap.getPixel(x, y) in deadlineColors } },
        )
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.guide_press_serve)), 5_000)
    }
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class FrenchPressEnLightTest : ScreenFrenchPressTests("en", "light")

@Config(qualifiers = "en-w411dp-h891dp-night-xxhdpi")
class FrenchPressEnDarkTest : ScreenFrenchPressTests("en", "dark")

@Config(qualifiers = "pt-rBR-w411dp-h891dp-notnight-xxhdpi")
class FrenchPressPtBrLightTest : ScreenFrenchPressTests("pt-BR", "light")

@Config(qualifiers = "pt-rBR-w411dp-h891dp-night-xxhdpi")
class FrenchPressPtBrDarkTest : ScreenFrenchPressTests("pt-BR", "dark")

@Config(qualifiers = "en-w320dp-h640dp-notnight-xxhdpi")
class FrenchPressSmallEnLightTest : ScreenFrenchPressTests("en", "light", compactOnly = true)

@Config(qualifiers = "en-w320dp-h640dp-night-xxhdpi")
class FrenchPressSmallEnDarkTest : ScreenFrenchPressTests("en", "dark", compactOnly = true)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-notnight-xxhdpi")
class FrenchPressSmallPtBrLightTest : ScreenFrenchPressTests("pt-BR", "light", compactOnly = true)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-night-xxhdpi")
class FrenchPressSmallPtBrDarkTest : ScreenFrenchPressTests("pt-BR", "dark", compactOnly = true)
