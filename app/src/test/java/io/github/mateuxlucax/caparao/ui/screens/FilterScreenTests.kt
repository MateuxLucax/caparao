package io.github.mateuxlucax.caparao.ui.screens
import android.content.Context
import android.os.Looper
import android.provider.Settings
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
import io.github.mateuxlucax.caparao.data.diagnostics.CaptureWriter
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
import io.github.mateuxlucax.caparao.brew.ratioBand
import io.github.mateuxlucax.caparao.brew.RatioBand
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
abstract class ScreenFilterTests(lang: String, theme: String, compactOnly: Boolean = false) : ScreenTestSupport(lang, theme, compactOnly) {
    @Test
    fun filterPrepareMarksSurviveAStepBack() {
        settings.setLastRecipeId(BrewMode.FILTER, BuiltInRecipes.V60_ID)
        open("synthetic-filter-lift", 0)
        click(R.string.mode_filter)
        compose.waitUntilAtLeastOneExists(hasText("V60 One and Done") and isSelectable(), 5_000)
        compose.onNode(hasText("V60 One and Done") and isSelectable()).performScrollTo().performClick()
        click(R.string.next)
        click(R.string.next)
        waitFor(R.string.prepare_filter_title)
        compose.onNodeWithTag("filter-prepare-row-0").performClick()
        compose.onNodeWithTag("filter-prepare-row-0").assert(isOn())
        tapBack()
        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
        compose.onNodeWithTag("weigh-step-next").performClick()
        waitFor(R.string.prepare_filter_title)
        compose.onNodeWithTag("filter-prepare-row-0").assert(isOn())
    }

    /** The summary closes through Back, which saves the notes; it has no X (#185, Summary row). */
    @Test
    fun filter() {
        val scale = open("synthetic-filter-lift", 0)
        click(R.string.mode_filter)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.free_brew)), 5_000)
        compose.waitForIdle()
        click(R.string.free_brew)
        click(R.string.next)
        // Weigh the beans: the scale shows nothing yet, so Next keeps the planned dose.
        click(R.string.next)
        click(R.string.prepare_ready)
        playUntilShown(scale, R.string.taring, 2_000)
        compose.waitForIdle()
        playUntilShown(scale, R.string.filter_ready, 7_000)
        compose.waitForIdle()
        // Pours from 20 s, then the dripper lifts at 90 s and pauses the brew.
        play(scale, 60_000)
        compose.waitForIdle()
        play(scale, 90_000)
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.filter_paused)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.filter_paused_hint)).assertIsDisplayed()
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).assertIsDisplayed()
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.brew_saved)), 5_000)
        compose.waitForIdle()
        // By action, not touch: the summary's layout can still shift as the notes load off the main thread.
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_brews)), 5_000)
        compose.onNodeWithText(text(R.string.tab_brews)).performSemanticsAction(SemanticsActions.OnClick)
        // The list loads the brew from the database off the main thread, so wait for its row before taking it.
        compose.waitUntilAtLeastOneExists(hasText(" ${text(R.string.unit_grams)} →", substring = true), 5_000)
        // The dose is what the scale showed on the weigh step, so take the only brew in the list.
        compose.onAllNodes(hasText(" ${text(R.string.unit_grams)} →", substring = true))[0].performSemanticsAction(SemanticsActions.OnClick)
        waitFor(R.string.delete)
        compose.waitForIdle()
    }

    /** Filter checklist follows its recipe, and weighing alone does not tare the dripper and server. */
    @Test
    fun filterPrepareStepWaitsForReady() {
        settings.setLastRecipeId(BrewMode.FILTER, BuiltInRecipes.V60_ID)
        val scale = open("synthetic-filter-lift", 0)
        click(R.string.mode_filter)
        compose.waitUntilAtLeastOneExists(hasText("V60 One and Done") and isSelectable(), 5_000)
        compose.onNode(hasText("V60 One and Done") and isSelectable()).performScrollTo().performClick()
        compose.waitForIdle()
        click(R.string.next)
        val beanWeighTares = scale.tareCommands
        click(R.string.next)

        waitFor(R.string.prepare_filter_title)
        assertEquals("the filter is not tared during preparation", beanWeighTares, scale.tareCommands)
        compose.onNodeWithContentDescription(text(R.string.milestone_prepare)).assertIsDisplayed()
        compose.onNodeWithTag("filter-prepare-picture").assertIsDisplayed()
        compose.onNodeWithTag("filter-prepare-dose").assertIsDisplayed()
        // The single source of the filter row order: the dripper and server go on the scale before the grounds (#278).
        listOf(
            R.string.prepare_rdt,
            R.string.prepare_grind,
            R.string.prepare_rinse_filter,
            R.string.prepare_dripper_server,
            R.string.prepare_add_grounds,
        ).forEachIndexed { row, label ->
            compose.onNodeWithTag("filter-prepare-row-$row").assertIsDisplayed().assertTextContains(text(label))
        }
        compose.onNodeWithTag("filter-prepare-grind-setting", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("filter-prepare-ready").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("filter-prepare-row-0").performClick()
        compose.onNodeWithTag("filter-prepare-row-0").assert(isOn())
        (1..4).forEach { index ->
            compose.onNodeWithTag("filter-prepare-row-$index").performClick()
        }
        compose.onAllNodes(isToggleable()).assertAll(isOn())
        compose.onNodeWithTag("filter-prepare-ready").assertIsDisplayed().assertIsEnabled()

        click(R.string.prepare_ready)
        compose.waitForIdle()
        assertTrue("Ready starts the tare", scale.tareCommands > 0)
        playUntilShown(scale, R.string.taring, 2_000)
    }

    /** Filter opens free with manual controls even when the last brew used a recipe. */
    @Test
    fun filterFreeBrewKeepsItsManualControls() {
        settings.setLastRecipeId(BrewMode.FILTER, BuiltInRecipes.V60_ID)
        open("idle-5min", 0)
        click(R.string.mode_filter)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.free_brew)), 5_000)
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.ratio_caption)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.ratio_less)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.ratio_more)).assertIsDisplayed()
    }

    /** A filter recipe makes water primary, adjusts only this brew and shows scaled targets alongside their times. */
    /**
     * The guided filter brew (wireframes 3a–3e) on the synthetic filter session, following a recipe shaped like it:
     * bloom to 50 g, a wait, then pours to 100, 150 and 200 g at 0:35, 0:50 and 1:00 from the first water (20 s in).
     */
    @Test
    fun filterGuide() {
        val recipe = Recipe(
            id = "screen-test-guide", name = "V60 · 15 g", mode = BrewMode.FILTER, method = "V60", doseGrams = 15.0, targetRatio = 17.0,
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 50.0, 0),
                RecipeStep(StepKind.WAIT, null, 10_000),
                RecipeStep(StepKind.SWIRL, null, 30_000),
                RecipeStep(StepKind.POUR, 100.0, 35_000),
                RecipeStep(StepKind.POUR, 150.0, 50_000),
                RecipeStep(StepKind.POUR, 200.0, 60_000),
            ),
            createdAt = 0, updatedAt = 0,
        )
        runBlocking { recipes.save(recipe) }
        settings.setLastRecipeId(BrewMode.FILTER, recipe.id)
        val scale = open("synthetic-filter-lift", 0)
        click(R.string.mode_filter)
        // The recipe loads off the main thread: Next only takes the dose once its chip is selected.
        compose.waitUntilAtLeastOneExists(hasText(recipe.name), 5_000)
        compose.onNode(hasText(recipe.name) and isSelectable()).performScrollTo().performClick()
        compose.waitForIdle()
        click(R.string.next)
        click(R.string.next)
        click(R.string.prepare_ready)
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text(R.string.ratio_caption))).fetchSemanticsNodes().isEmpty() }
        play(scale, 24_000)
        compose.waitUntilAtLeastOneExists(hasText(context.getString(R.string.guide_bloom, Format.grams(50.0, locale))), 5_000)
        compose.waitForIdle()
        val counterPrefix = context.getString(R.string.step_of_named, 1, recipe.steps.size, "").substringBefore("1")
        compose.onAllNodes(hasText(counterPrefix, substring = true)).assertCountEquals(0)
        compose.onNodeWithContentDescription(
            context.getString(R.string.milestone_and_step, text(R.string.milestone_brew), text(R.string.step_bloom)),
        ).assertIsDisplayed()
        // The brew segments fill as their step opens (shown at once with animations off): earlier ones full, later ones empty.
        compose.onNodeWithTag("milestone-fill-2", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("milestone-fill-3", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("milestone-fill-4", useUnmergedTree = true).assertDoesNotExist()
        play(scale, 45_000)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.guide_wait)), 5_000)
        compose.onNodeWithTag("countdown-ring").assertIsDisplayed()
        compose.waitForIdle()
        play(scale, 52_000)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.guide_swirl)), 5_000)
        compose.onNodeWithTag("countdown-ring").assertIsDisplayed()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(
            context.getString(R.string.milestone_and_step, text(R.string.milestone_brew), text(R.string.step_swirl)),
        ).assertIsDisplayed()
        compose.onNodeWithTag("milestone-fill-4", useUnmergedTree = true).assertExists()
        play(scale, 59_000)
        compose.waitUntilAtLeastOneExists(hasText(context.getString(R.string.guide_pour, Format.grams(100.0, locale))), 5_000)
        compose.waitForIdle()
        play(scale, 69_000)
        compose.waitUntilAtLeastOneExists(hasText(context.getString(R.string.guide_pour, Format.grams(150.0, locale))), 5_000)
        compose.waitForIdle()
        play(scale, 89_500)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.guide_drawdown)), 5_000)
        compose.waitForIdle()
        // The lift comes after the last pour (200 g) and the drawdown has begun, so it ends the brew at once, with no Done (#277).
        play(scale, 90_000)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.brew_saved)), 5_000)
        compose.waitForIdle()
        // The summary opens before the save is written: close it and wait for the brew in the list, so the save finishes.
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_brews)), 5_000)
        compose.onNodeWithText(text(R.string.tab_brews)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilAtLeastOneExists(hasText(" ${text(R.string.unit_grams)} →", substring = true), 5_000)
    }

    /** The guided cup and weight use the shared target bands in filter and French press; lateness stays muted text. */
    @Test
    fun guidedCupAndWeightFollowStepRatioBandsInBothModes() {
        val target = 100.0
        val cases = listOf(
            94.9 to RatioBand.BELOW,
            95.0 to RatioBand.AT,
            105.0 to RatioBand.AT,
            105.1 to RatioBand.ABOVE,
        )
        fun state(mode: BrewMode, weight: Double) = BrewUiState(
            mode = mode,
            step = BrewStep.LIVE,
            phase = BrewPhase.BREWING,
            weightGrams = weight,
            recipe = Recipe(
                id = "guided-cup-${mode.name}", name = "Cup color", mode = mode,
                method = if (mode == BrewMode.FRENCH_PRESS) "French press" else "V60",
                doseGrams = 15.0, targetRatio = 16.0,
                steps = listOf(RecipeStep(StepKind.POUR, target, 0)), createdAt = 0, updatedAt = 0,
            ),
            guide = StepState(0, (weight / target).toFloat(), null, Pace.BEHIND, 11_000, null),
        )

        var current by mutableStateOf(state(BrewMode.FILTER, 94.9))
        var expectedCup = Color.Unspecified
        var expectedWeight = Color.Unspecified
        var expectedPace = Color.Unspecified
        var expectedServerBody = Color.Unspecified
        compose.setContent {
            AppTheme {
                val scheme = MaterialTheme.colorScheme
                expectedCup = scheme.ringStroke(ratioBand(current.weightGrams, target), isSystemInDarkTheme())
                expectedWeight = expectedCup.readableOn(scheme.surface)
                expectedPace = scheme.onSurfaceVariant
                expectedServerBody = scheme.surfaceContainerHighest
                GuidedStep(current) {}
            }
        }

        fun assertContainsColor(tag: String, color: Color) {
            val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            val expected = color.toArgb()
            val found = (0 until bitmap.height).any { y -> (0 until bitmap.width).any { x -> bitmap.getPixel(x, y) == expected } }
            assertTrue("$tag should render $color", found)
        }

        fun assertServerHasNoTargetMark() {
            val bitmap = compose.onNodeWithTag("guided-cup").captureToImage().asAndroidBitmap()
            val scale = bitmap.width / 80f
            val x = ((65f - 26f) * scale).toInt()
            val y = ((bitmap.height - 52f * scale) / 2f + (69.875f - 50f) * scale).toInt()
            assertEquals(expectedServerBody.toArgb(), bitmap.getPixel(x, y))
        }

        listOf(BrewMode.FILTER, BrewMode.FRENCH_PRESS).forEach { mode ->
            cases.forEach { (weight, band) ->
                current = state(mode, weight)
                compose.waitForIdle()
                assertEquals(band, ratioBand(weight, target))
                compose.onNodeWithTag("guided-server").assertIsDisplayed()
                compose.onNodeWithTag("guided-cup").assertIsDisplayed()
                assertContainsColor("guided-cup", expectedCup)
                if (mode == BrewMode.FILTER && weight == 94.9) assertServerHasNoTargetMark()
                assertContainsColor("guided-weight", expectedWeight)
                assertContainsColor("guide-pace", expectedPace)
                compose.onNodeWithText(context.getString(R.string.guide_behind, 11, text(R.string.unit_seconds))).assertIsDisplayed()
            }
        }
    }

    /** The press art on each stage with animations off: still frames that carry all the information (#226). */
    @Test
    fun filterDrawdownShowsThreeDrainTimeStatesAndOmitsTheBarWithoutARange() {
        val recipe = Recipe(
            id = "drain-time-screen", name = "Drain time", mode = BrewMode.FILTER, method = "V60",
            doseGrams = 15.0, targetRatio = 16.0,
            targetMinMs = 120_000, targetMaxMs = 150_000,
            steps = listOf(RecipeStep(StepKind.POUR, 240.0, 0)), createdAt = 0, updatedAt = 0,
        )
        val pouringRecipe = recipe.copy(
            steps = listOf(RecipeStep(StepKind.BLOOM, 40.0, 0), RecipeStep(StepKind.POUR, 240.0, 30_000)),
        )
        fun state(elapsedMs: Long, shownRecipe: Recipe = recipe, phase: BrewPhase = BrewPhase.BREWING): BrewUiState {
            val guidedSteps = if (shownRecipe == pouringRecipe) 2 else 1
            val drainWindow = when {
                shownRecipe.mode != BrewMode.FILTER || shownRecipe.targetMinMs == null || shownRecipe.targetMaxMs == null ||
                    shownRecipe.steps.size != guidedSteps -> null
                elapsedMs < shownRecipe.targetMinMs -> BrewUiState.Window.BEFORE
                elapsedMs <= shownRecipe.targetMaxMs -> BrewUiState.Window.INSIDE
                else -> BrewUiState.Window.PAST
            }
            return BrewUiState(
                mode = shownRecipe.mode,
                step = BrewStep.LIVE,
                phase = phase,
                elapsedMs = elapsedMs,
                drainWindow = drainWindow,
                recipe = shownRecipe,
                guide = StepState(1, 1f, null, null, null, null),
            )
        }

        var current by mutableStateOf(state(100_000, pouringRecipe))
        compose.setContent { AppTheme { GuidedStep(current) {} } }
        compose.onNodeWithTag("drain-time-progress").assertDoesNotExist()

        current = state(100_000)
        compose.waitForIdle()
        compose.onNodeWithTag("drain-time-before").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.drain_time_ideal_range, "02:00", "02:30")).assertIsDisplayed()

        current = state(132_000)
        compose.waitForIdle()
        compose.onNodeWithTag("drain-time-inside").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.drain_time_in_range)).assertIsDisplayed()

        current = state(132_000, phase = BrewPhase.PAUSED)
        compose.waitForIdle()
        compose.onNodeWithTag("drain-time-inside").assertIsDisplayed()
        compose.onNodeWithTag("drain-time-progress").assertIsDisplayed()

        current = state(161_000)
        compose.waitForIdle()
        compose.onNodeWithTag("drain-time-past").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.drain_time_past_range, 11, text(R.string.unit_seconds))).assertIsDisplayed()

        current = state(132_000, recipe.copy(targetMinMs = null, targetMaxMs = null))
        compose.waitForIdle()
        compose.onNodeWithTag("drain-time-progress").assertDoesNotExist()

        current = state(132_000, recipe.copy(mode = BrewMode.FRENCH_PRESS, method = "French press"))
        compose.waitForIdle()
        compose.onNodeWithTag("drain-time-progress").assertDoesNotExist()
        current = state(132_000, recipe.copy(mode = BrewMode.ESPRESSO, method = "Espresso"))
        compose.waitForIdle()
        compose.onNodeWithTag("drain-time-progress").assertDoesNotExist()
    }

    /** After a pause the readout shows its held numbers at once, with the ring colour coming back from the paused one. */
    @Test
    fun scaleReadoutResumesFromPauseWithItsNumbers() {
        var paused by mutableStateOf(true)
        compose.setContent {
            AppTheme {
                ScaleReadout(
                    weightGrams = 142.0, flowGramsPerSecond = 0.0, timerSeconds = 74, timerRunning = !paused,
                    started = true, ringAlpha = if (paused) 0.45f else 1f, initialRingAlpha = 0.45f,
                )
            }
        }
        compose.onNodeWithText("01:14").assertIsDisplayed()
        paused = false
        compose.waitForIdle()
        compose.onNodeWithText("01:14").assertIsDisplayed()
        compose.onNodeWithText(Format.grams(142.0, locale), substring = true).assertIsDisplayed()
    }

    /** Live flow stays current with animations disabled, and the optional target only adds its ideal band. */
    @Test
    fun guidedFlowChartHasAnOptionalTargetAndUpdatesLive() {
        fun state(mode: BrewMode, target: Double?, flow: Double) = BrewUiState(
            mode = mode,
            step = BrewStep.LIVE,
            flowGramsPerSecond = flow,
            recipe = Recipe(
                id = "flow-chart-${mode.name}", name = "Flow chart", mode = mode,
                method = if (mode == BrewMode.FRENCH_PRESS) "French press" else "V60",
                doseGrams = 15.0, targetRatio = 16.0,
                steps = listOf(RecipeStep(StepKind.BLOOM, 45.0, 0, targetFlowGramsPerSecond = target)),
                createdAt = 0, updatedAt = 0,
            ),
            guide = StepState(index = 0, progress = 0.5f, dueInMs = null, pace = Pace.ON_TIME, paceMs = 0, overshootGrams = null),
        )

        var current by mutableStateOf(state(BrewMode.FILTER, 5.0, 2.0))
        compose.setContent { AppTheme { GuidedStep(current) {} } }
        compose.onNodeWithTag("flow-chart-target").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.guide_flow, Format.flow(2.0, locale))).assertIsDisplayed()

        current = state(BrewMode.FILTER, null, 3.0)
        compose.waitForIdle()
        compose.onNodeWithTag("flow-chart-no-target").assertIsDisplayed()
        compose.onAllNodes(hasTestTag("flow-chart-target")).assertCountEquals(0)
        compose.onNodeWithText(context.getString(R.string.guide_flow, Format.flow(3.0, locale))).assertIsDisplayed()

        current = state(BrewMode.FRENCH_PRESS, null, 4.0)
        compose.waitForIdle()
        compose.onNodeWithTag("flow-chart-no-target").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.guide_flow, Format.flow(4.0, locale))).assertIsDisplayed()
    }

    /** The cached chart paints its curve, and its moving last point follows the animated flow in the draw phase (#321). */
    @Test
    fun guidedFlowChartDrawsItsCurveAndFollowsTheFlow() {
        val flow = mutableStateOf(2f)
        var stroke = Color.Unspecified
        compose.setContent {
            AppTheme {
                stroke = MaterialTheme.colorScheme.primary
                FlowChart(
                    liveFlow = 5.0,
                    animatedFlow = flow,
                    history = listOf(FlowPoint(0, 1.0), FlowPoint(2_000, 2.0), FlowPoint(4_000, 5.0)),
                    target = 5.0,
                    locale = locale,
                )
            }
        }
        fun chartPixels() = compose.onNodeWithTag("flow-chart-target").captureToImage().asAndroidBitmap().let { bitmap ->
            IntArray(bitmap.width * bitmap.height) { i -> bitmap.getPixel(i % bitmap.width, i / bitmap.width) }
        }
        val painted = chartPixels()
        assertTrue("the chart should draw its curve", painted.any { it == stroke.toArgb() })
        flow.value = 0f
        compose.waitForIdle()
        assertTrue("the moving last point should follow the flow", !painted.contentEquals(chartPixels()))
    }

    /** French press shows live flow and its chart on bloom and pour only; wait and steep hide them (#224). */
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class FilterEnLightTest : ScreenFilterTests("en", "light")

@Config(qualifiers = "en-w411dp-h891dp-night-xxhdpi")
class FilterEnDarkTest : ScreenFilterTests("en", "dark")

@Config(qualifiers = "pt-rBR-w411dp-h891dp-notnight-xxhdpi")
class FilterPtBrLightTest : ScreenFilterTests("pt-BR", "light")

@Config(qualifiers = "pt-rBR-w411dp-h891dp-night-xxhdpi")
class FilterPtBrDarkTest : ScreenFilterTests("pt-BR", "dark")

@Config(qualifiers = "en-w320dp-h640dp-notnight-xxhdpi")
class FilterSmallEnLightTest : ScreenFilterTests("en", "light", compactOnly = true)

@Config(qualifiers = "en-w320dp-h640dp-night-xxhdpi")
class FilterSmallEnDarkTest : ScreenFilterTests("en", "dark", compactOnly = true)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-notnight-xxhdpi")
class FilterSmallPtBrLightTest : ScreenFilterTests("pt-BR", "light", compactOnly = true)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-night-xxhdpi")
class FilterSmallPtBrDarkTest : ScreenFilterTests("pt-BR", "dark", compactOnly = true)
