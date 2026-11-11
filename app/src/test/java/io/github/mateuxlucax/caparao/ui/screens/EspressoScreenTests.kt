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
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticRecorder
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticShare
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
import io.github.mateuxlucax.caparao.ui.settings.DiagnosticsViewModel
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
abstract class ScreenEspressoTests(lang: String, theme: String, compactOnly: Boolean = false) : ScreenTestSupport(lang, theme, compactOnly) {
    @Test
    fun espresso() {
        val scale = open("synthetic-espresso", 0)
        click(R.string.mode_espresso)
        compose.waitForIdle()
        click(R.string.next)
        compose.waitForIdle()
        // Weigh the beans: the scale shows nothing yet, so Next keeps the planned dose.
        click(R.string.next)
        click(R.string.prepare_ready)
        // The shot starts 14 s into the session and pours for about 25 s.
        play(scale, 21_000)
        compose.waitForIdle()
        playUntilShown(scale, R.string.brew_saved, 60_000)
        compose.waitForIdle()
        // By action, not touch: the summary's layout can still shift as the notes load off the main thread.
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_brews)), 5_000)
        compose.onNodeWithText(text(R.string.tab_brews)).performSemanticsAction(SemanticsActions.OnClick)
        // The list loads the brew from the database off the main thread, so wait for its row before taking it.
        compose.waitUntilAtLeastOneExists(hasText(" ${text(R.string.unit_grams)} →", substring = true), 5_000)
        // The dose is what the scale showed on the weigh step, so take the only brew in the list.
        compose.onAllNodes(hasText(" ${text(R.string.unit_grams)} →", substring = true))[0].performSemanticsAction(SemanticsActions.OnClick)
        // The brew loads from the database off the main thread.
        waitFor(R.string.delete)
        compose.waitForIdle()
    }

    /** The espresso from the synthetic session, stopped on its summary: the notes and the Done button (#181). */
    @Test
    fun summaryBackSavesNotesAndCloses() {
        espressoSummary()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.brew_coffee))).performTextReplacement("Kalita")
        compose.waitForIdle()
        tapBack()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_brews)), 5_000)
        compose.onNodeWithText(text(R.string.discard_brew_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.brew_saved)).assertDoesNotExist()
        assertEquals("Kalita", runBlocking { brews.latest()?.coffee })
    }

    /** Discard on the summary still asks first, and Cancel keeps the brew (#181, A1). */
    @Test
    fun summaryDiscardStillAsks() {
        espressoSummary()
        // By action, not touch: the curve pushes Discard below the fold.
        compose.onNode(hasText(text(R.string.discard)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitFor(R.string.discard_brew_title)
        click(R.string.cancel)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.discard_brew_title)).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(text(R.string.brew_saved)).assertIsDisplayed()
    }

    /** An espresso on its dose step, before anything is weighed (#185). */
    @Test
    fun doseBackClosesTheBrew() {
        espressoDose()
        tapBack()
        waitForBrewClosed()
    }

    @Test
    fun doseCloseBrewClosesTheBrew() {
        espressoDose()
        tapCloseBrew()
        waitForBrewClosed()
    }

    /** Back on the beans step returns to the dose (#185, Beans row); the X closes the brew. */
    @Test
    fun beansBackReturnsToTheDoseStep() {
        espressoDose()
        click(R.string.next)
        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
        tapBack()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("weigh-step-next")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(text(R.string.ratio_caption)).assertIsDisplayed()
        // Next from the dose weighs the beans again.
        click(R.string.next)
        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
    }

    @Test
    fun beansCloseBrewClosesTheBrew() {
        espressoDose()
        click(R.string.next)
        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
        tapCloseBrew()
        waitForBrewClosed()
    }

    /** Back on the espresso preparation returns to the beans, and the X closes the brew (#185, Prepare row). */
    @Test
    fun portafilterBackReturnsToTheBeansStep() {
        espressoDose()
        click(R.string.next)
        compose.onNodeWithTag("weigh-step-next").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("portafilter-ready"), 5_000)
        tapBack()
        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
        compose.onNodeWithTag("portafilter-ready").assertDoesNotExist()
    }

    @Test
    fun portafilterCloseBrewClosesTheBrew() {
        espressoDose()
        click(R.string.next)
        compose.onNodeWithTag("weigh-step-next").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("portafilter-ready"), 5_000)
        tapCloseBrew()
        waitForBrewClosed()
    }

    /** The guided espresso from the synthetic session, on its brewing shot (#287). */
    @Test
    fun liveBackAsksToRestartAndCancelKeepsBrewing() {
        espressoLive()
        tapBack()
        waitFor(R.string.restart_brew_title)
        compose.onNodeWithText(text(R.string.restart_brew_text)).assertIsDisplayed()
        click(R.string.cancel)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.restart_brew_title)).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(text(R.string.brewing_espresso)).assertIsDisplayed()
    }

    /** Restart drops the shot and returns to Prepare, and nothing is saved (#287). */
    @Test
    fun liveRestartReturnsToPrepareAndSavesNothing() {
        espressoLive()
        tapBack()
        waitFor(R.string.restart_brew_title)
        click(R.string.restart)
        compose.waitUntilAtLeastOneExists(hasTestTag("portafilter-ready"), 5_000)
        compose.onAllNodesWithText(text(R.string.restart_brew_title)).assertCountEquals(0)
        compose.onAllNodesWithText(text(R.string.brew_saved)).assertCountEquals(0)
        assertTrue(runBlocking { brews.latest() } == null)
    }

    /** The filter checklist keeps its marks through a step back and forward again (#185, Prepare row). */
    @Test
    fun summaryHasNoCloseBrewButton() {
        espressoSummary()
        compose.onAllNodes(hasContentDescription(text(R.string.close_brew))).assertCountEquals(0)
    }

    /** Espresso with a recipe: a target, so its values are shown and nothing changes the dose or ratio (#189). */
    @Test
    fun espressoFreeBrewCalculatesWater() {
        settings.setLastRecipeId(BrewMode.ESPRESSO, SettingsStore.FREE_BREW)
        open("idle-5min", 0)
        click(R.string.mode_espresso)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.free_brew)), 5_000)
        compose.waitForIdle()

        fun water(ratio: Double) = "${Format.grams(18.0 * ratio, locale)} ${text(R.string.unit_ml)}"
        compose.onNodeWithText(water(2.0)).assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithContentDescription(text(R.string.ratio_more)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(water(2.1)), 5_000)

        // The dose is still the user's to set.
        compose.onNodeWithText("${Format.grams(18.0, locale)} ${text(R.string.unit_grams)}").performClick()
        compose.onNodeWithText(text(R.string.dose_field)).assertExists()
    }

    /** The 320dp runner width from #145: keep the weigh action in the rendered viewport. */
    @Test
    fun espressoGuide() {
        val scale = open("synthetic-espresso-guide", 0)
        click(R.string.mode_espresso)
        compose.waitUntilAtLeastOneExists(hasText("Espresso Oster") and isSelectable(), 5_000)
        compose.onNode(hasText("Espresso Oster") and isSelectable()).performClick()
        click(R.string.next)
        // Weigh the beans: the scale shows nothing yet, so Next keeps the planned dose.
        click(R.string.next)
        waitFor(R.string.prepare_portafilter_title)
        compose.onNode(hasContentDescription(text(R.string.milestone_portafilter))).assertIsDisplayed()
        // The localized counter's own prefix ("Step " or "Etapa "), so the check means something in pt-BR too.
        val counterPrefix = context.getString(R.string.step_of_named, 1, 8, "").substringBefore("1")
        compose.onAllNodes(hasText(counterPrefix, substring = true)).assertCountEquals(0)
        compose.onNodeWithText(context.getString(R.string.prepare_dose, Format.grams(19.0, context.resources.configuration.locales[0]))).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.prepare_grind)).assertIsDisplayed()
        compose.onNodeWithTag("portafilter-grind-setting", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("10", useUnmergedTree = true).assertIsDisplayed()
        listOf(R.string.prepare_rdt, R.string.prepare_wdt, R.string.prepare_tamp, R.string.prepare_puck_screen).forEach {
            compose.onNodeWithText(text(it)).assertIsDisplayed()
        }
        compose.onNodeWithTag("portafilter-ready").assertIsDisplayed()
        click(R.string.prepare_ready)
        compose.waitForIdle()
        // The cup lands at 8 s and is tared as soon as it is still.
        playUntilShown(scale, R.string.taring, 15_000)
        compose.waitForIdle()
        playUntilShown(scale, R.string.espresso_ready, 20_000)
        compose.waitForIdle()
    }

    /**
     * With animations off, the espresso pictures are still frames: the cup, the LED and the open ring show the same
     * state as the animated ones, and the weight, timer and flow stay on screen through the shot (#194).
     */
    /**
     * With animations off, the espresso pictures are still frames: the cup, the LED and the open ring show the same
     * state as the animated ones, and the weight, timer and flow stay on screen through the shot (#194).
     */
    @Test
    fun espressoMotionStill() {
        val scale = open("synthetic-espresso-guide", 0)
        click(R.string.mode_espresso)
        compose.waitUntilAtLeastOneExists(hasText("Espresso Oster") and isSelectable(), 5_000)
        compose.onNode(hasText("Espresso Oster") and isSelectable()).performClick()
        click(R.string.next)
        click(R.string.next)
        click(R.string.prepare_ready)
        compose.waitForIdle()
        playUntilShown(scale, R.string.taring, 15_000)
        compose.waitForIdle()
        playUntilShown(scale, R.string.espresso_ready, 20_000)
        compose.waitForIdle()
        compose.onNode(hasContentDescription(text(R.string.milestone_shot))).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.label_flow)).assertIsDisplayed()
        playUntilShown(scale, R.string.brewing_espresso, 60_000)
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.label_flow)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.label_ratio)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.stop)).assertIsDisplayed()
    }

    /**
     * With animations off, the portafilter checklist still ticks: every item can be checked, and Ready stays on screen and
     * enabled, emphasized or not (#198).
     */
    /**
     * With animations off, the portafilter checklist still ticks: every item can be checked, and Ready stays on screen and
     * enabled, emphasized or not (#198).
     */
    @Test
    fun portafilterChecksWithoutMotion() {
        open("synthetic-espresso-guide", 0)
        click(R.string.mode_espresso)
        compose.waitUntilAtLeastOneExists(hasText("Espresso Oster") and isSelectable(), 5_000)
        compose.onNode(hasText("Espresso Oster") and isSelectable()).performClick()
        click(R.string.next)
        click(R.string.next)
        waitFor(R.string.prepare_portafilter_title)
        compose.onAllNodes(isToggleable()).assertCountEquals(5)
        // The text side of the row is part of its touch target, not just the leading mark.
        compose.onNodeWithTag("portafilter-row-0").performClick()
        compose.onAllNodes(isToggleable())[0].assert(isOn())
        compose.onNodeWithTag("portafilter-row-0").performClick()
        compose.onAllNodes(isToggleable())[0].assert(!isOn())
        repeat(5) { compose.onAllNodes(isToggleable())[it].performClick() }
        compose.waitForIdle()
        compose.onAllNodes(isToggleable()).assertAll(isOn())
        compose.onNodeWithTag("portafilter-ready").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(text(R.string.prepare_ready)).assertIsDisplayed()
    }

    /** At 640 dp tall, the picture is omitted and the five rows and Ready fit without scrolling. */
    /**
     * The same espresso path with animations on (#194): the cup lands, the tare rolls the weight to zero and the first
     * drop falls. The milestone, weight, timer and flow stay on screen through each stage. The ring's open state is
     * covered by ScaleReadoutRingTest, since the Canvas has no semantics to query here.
     */
    @Test
    fun espressoMotionAnimated() {
        val before = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            val scale = open("synthetic-espresso-guide", 0)
            click(R.string.mode_espresso)
            compose.waitUntilAtLeastOneExists(hasText("Espresso Oster") and isSelectable(), 5_000)
            compose.onNode(hasText("Espresso Oster") and isSelectable()).performClick()
            click(R.string.next)
            click(R.string.next)
            click(R.string.prepare_ready)
            compose.waitForIdle()
            playUntilShown(scale, R.string.taring, 15_000)
            compose.waitForIdle()
            // The tare rolls the weight down to zero on its 400 ms tween; the readout keeps its labels while it does.
            while (compose.onAllNodes(hasText(Format.grams(0.0, locale))).fetchSemanticsNodes().isEmpty()) {
                check(scale.nowMs < 20_000) { "the weight never rolled to zero" }
                compose.onNodeWithText(text(R.string.label_flow)).assertIsDisplayed()
                compose.onNodeWithText(text(R.string.label_ratio)).assertIsDisplayed()
                play(scale, scale.nowMs + 100)
                compose.waitForIdle()
            }
            assertWeightAndTimerShown()
            compose.onNodeWithText(text(R.string.label_flow)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.label_ratio)).assertIsDisplayed()
            playUntilShown(scale, R.string.espresso_ready, 20_000)
            compose.waitForIdle()
            compose.onNode(hasContentDescription(text(R.string.milestone_shot))).assertIsDisplayed()
            assertWeightAndTimerShown()
            compose.onNodeWithText(text(R.string.label_flow)).assertIsDisplayed()
            playUntilShown(scale, R.string.brewing_espresso, 60_000)
            compose.waitForIdle()
            compose.onNode(hasContentDescription(text(R.string.milestone_shot))).assertIsDisplayed()
            assertWeightAndTimerShown()
            compose.onNodeWithText(text(R.string.label_flow)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.label_ratio)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.stop)).assertIsDisplayed()
        } finally {
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, before)
        }
    }

    /** Retry is greyed out while the scale is disconnected, and the error stays on screen; it works again once connected. */
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class EspressoEnLightTest : ScreenEspressoTests("en", "light")

@Config(qualifiers = "en-w411dp-h891dp-night-xxhdpi")
class EspressoEnDarkTest : ScreenEspressoTests("en", "dark")

@Config(qualifiers = "pt-rBR-w411dp-h891dp-notnight-xxhdpi")
class EspressoPtBrLightTest : ScreenEspressoTests("pt-BR", "light")

@Config(qualifiers = "pt-rBR-w411dp-h891dp-night-xxhdpi")
class EspressoPtBrDarkTest : ScreenEspressoTests("pt-BR", "dark")

@Config(qualifiers = "en-w320dp-h640dp-notnight-xxhdpi")
class EspressoSmallEnLightTest : ScreenEspressoTests("en", "light", compactOnly = true)

@Config(qualifiers = "en-w320dp-h640dp-night-xxhdpi")
class EspressoSmallEnDarkTest : ScreenEspressoTests("en", "dark", compactOnly = true)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-notnight-xxhdpi")
class EspressoSmallPtBrLightTest : ScreenEspressoTests("pt-BR", "light", compactOnly = true)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-night-xxhdpi")
class EspressoSmallPtBrDarkTest : ScreenEspressoTests("pt-BR", "dark", compactOnly = true)
