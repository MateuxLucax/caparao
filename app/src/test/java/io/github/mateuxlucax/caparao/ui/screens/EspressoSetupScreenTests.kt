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
import io.github.mateuxlucax.caparao.ui.widget.brewWidgetWeekWindow
import io.github.mateuxlucax.caparao.ui.widget.summarizeBrewWidgetWeek
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
import io.github.mateuxlucax.caparao.ui.AppActions
import io.github.mateuxlucax.caparao.ui.Tab
import io.github.mateuxlucax.caparao.brew.StepState
import io.github.mateuxlucax.caparao.brew.Pace
import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.ui.brew.BrewNumbers
import io.github.mateuxlucax.caparao.ui.brew.BrewStep
import io.github.mateuxlucax.caparao.ui.brew.BrewUiState
import io.github.mateuxlucax.caparao.ui.brew.BrewViewModel
import io.github.mateuxlucax.caparao.ui.brew.FlowChart
import io.github.mateuxlucax.caparao.ui.brew.FlowPoint
import io.github.mateuxlucax.caparao.ui.brew.GuidedStep
import io.github.mateuxlucax.caparao.ui.brews.BrewDetailViewModel
import io.github.mateuxlucax.caparao.ui.brews.BrewsViewModel
import io.github.mateuxlucax.caparao.ui.brews.CompareViewModel
import io.github.mateuxlucax.caparao.ui.brews.TrendsViewModel
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.home.HomeViewModel
import io.github.mateuxlucax.caparao.ui.onboarding.OnboardingViewModel
import io.github.mateuxlucax.caparao.ui.settings.ScaleStatusViewModel
import io.github.mateuxlucax.caparao.ui.settings.ServerViewModel
import io.github.mateuxlucax.caparao.ui.settings.ExportViewModel
import io.github.mateuxlucax.caparao.ui.settings.DiagnosticsViewModel
import io.github.mateuxlucax.caparao.ui.settings.TargetRatioViewModel
import io.github.mateuxlucax.caparao.ui.theme.AppTheme
import io.github.mateuxlucax.caparao.ui.theme.BrandColor
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
abstract class ScreenEspressoSetupTests(lang: String, theme: String, compactOnly: Boolean = false) : ScreenTestSupport(lang, theme, compactOnly) {
    @Test
    fun smallScreenPortafilter() {
        assumeTrue("small-screen variant only", context.resources.configuration.screenWidthDp == 320 && context.resources.configuration.screenHeightDp == 640)
        open("synthetic-espresso-guide", 0)
        click(R.string.mode_espresso)
        compose.waitUntilAtLeastOneExists(hasText("Espresso Oster") and isSelectable(), 5_000)
        compose.onNode(hasText("Espresso Oster") and isSelectable()).performClick()
        click(R.string.next)
        click(R.string.next)
        waitFor(R.string.prepare_portafilter_title)
        compose.onNodeWithTag("portafilter-picture").assertDoesNotExist()
        compose.onNodeWithTag("portafilter-dose").assertIsDisplayed()
        repeat(5) { compose.onNodeWithTag("portafilter-row-$it").assertIsDisplayed() }
        compose.onNodeWithTag("portafilter-ready").assertIsDisplayed().assertIsEnabled()
    }

    /** At 640 dp tall, the filter picture is omitted so the five rows and Ready fit without scrolling. */
    @Test
    fun smallScreenFilterPrepare() {
        assumeTrue("small-screen variant only", context.resources.configuration.screenWidthDp == 320 && context.resources.configuration.screenHeightDp == 640)
        settings.setLastRecipeId(BrewMode.FILTER, BuiltInRecipes.V60_ID)
        open("synthetic-filter-lift", 0)
        click(R.string.mode_filter)
        compose.waitUntilAtLeastOneExists(hasText("V60 One and Done") and isSelectable(), 5_000)
        compose.onNode(hasText("V60 One and Done") and isSelectable()).performScrollTo().performClick()
        click(R.string.next)
        click(R.string.next)
        waitFor(R.string.prepare_filter_title)
        compose.onNodeWithTag("filter-prepare-picture").assertDoesNotExist()
        compose.onNodeWithTag("filter-prepare-dose").assertIsDisplayed()
        repeat(5) { compose.onNodeWithTag("filter-prepare-row-$it").assertIsDisplayed() }
        compose.onNodeWithTag("filter-prepare-ready").assertIsDisplayed().assertIsEnabled()
    }

    /**
     * The cup lands at 8 s but the scale never reads zero: taring times out after the retry and shows the error. Retry
     * with the scale zeroing again tares, and the guide carries on to the first drop.
     */
    /**
     * The cup lands at 8 s but the scale never reads zero: taring times out after the retry and shows the error. Retry
     * with the scale zeroing again tares, and the guide carries on to the first drop.
     */
    @Test
    fun espressoTareTimesOut() {
        val scale = open("synthetic-espresso-guide", 0)
        scale.zeroOnTare = false
        click(R.string.mode_espresso)
        click(R.string.next)
        click(R.string.next)
        click(R.string.prepare_ready)
        compose.waitForIdle()
        playUntilShown(scale, R.string.taring, 15_000)
        playUntilShown(scale, R.string.tare_failed_title, 40_000)
        compose.onNodeWithText(text(R.string.tare_failed_hint)).assertIsDisplayed()
        scale.zeroOnTare = true
        click(R.string.retry)
        compose.waitForIdle()
        playUntilShown(scale, R.string.espresso_ready, 55_000)
        compose.onAllNodes(hasText(text(R.string.tare_failed_title))).assertCountEquals(0)

    }

    /**
     * The same espresso path with animations on (#194): the cup lands, the tare rolls the weight to zero and the first
     * drop falls. The milestone, weight, timer and flow stay on screen through each stage. The ring's open state is
     * covered by ScaleReadoutRingTest, since the Canvas has no semantics to query here.
     */
    @Test
    fun espressoTareRetryNeedsAConnectedScale() {
        val scale = open("synthetic-espresso-guide", 0)
        scale.zeroOnTare = false
        click(R.string.mode_espresso)
        click(R.string.next)
        click(R.string.next)
        click(R.string.prepare_ready)
        compose.waitForIdle()
        playUntilShown(scale, R.string.tare_failed_title, 40_000)
        scale.state.value = ScaleState.Failed(ScaleError.CONNECTION_LOST, retrying = true)
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.retry)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.tare_failed_title)).assertIsDisplayed()
        scale.state.value = ScaleState.Connected(battery = null)
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.retry)).assertIsEnabled()
    }

    /** #182 D1: the dose stays editable with a quiet Connecting line while no scale is connected. */
    @Test
    fun doseWithoutAScaleShowsConnecting() {
        val scale = open("synthetic-espresso", 0)
        click(R.string.mode_espresso)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.free_brew)), 5_000)
        click(R.string.free_brew)
        compose.waitForIdle()
        // Home opens a brew only with a scale, so the scale drops once the dose is on screen (#182).
        scale.state.value = ScaleState.Idle
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.scale_connecting)).assertIsDisplayed()
        compose.onAllNodes(hasText(text(R.string.error_not_found))).assertCountEquals(0)
    }

    /** #182 B1 and B2: Next waits with Connecting and is disabled; when the scale connects the weighing opens by itself. */
    @Test
    fun nextWithoutAScaleWaitsThenContinuesWhenItConnects() {
        val scale = open("synthetic-espresso", 0)
        click(R.string.mode_espresso)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.free_brew)), 5_000)
        click(R.string.free_brew)
        compose.waitForIdle()
        // Home opens a brew only with a scale, so the scale drops once the dose is on screen (#182).
        scale.state.value = ScaleState.Idle
        compose.waitForIdle()
        click(R.string.next)
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.next)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.scale_connecting)).assertIsDisplayed()

        scale.state.value = ScaleState.Connected(battery = null)

        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
        compose.onAllNodes(hasText(text(R.string.scale_connecting))).assertCountEquals(0)
    }

    /** #182 B3: after ten seconds without a scale the card says so, with Retry; a scale that connects later still continues. */
    @Test
    fun aScaleThatNeverConnectsShowsNotFoundAndStillContinuesWhenItConnects() {
        val scale = open("synthetic-espresso", 0)
        click(R.string.mode_espresso)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.free_brew)), 5_000)
        click(R.string.free_brew)
        compose.waitForIdle()
        // Home opens a brew only with a scale, so the scale drops once the dose is on screen (#182).
        scale.state.value = ScaleState.Idle
        compose.waitForIdle()
        click(R.string.next)
        shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.SECONDS)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.error_not_found)), 5_000)
        compose.onNodeWithText(text(R.string.hint_not_found)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.retry)).assertIsEnabled()

        scale.state.value = ScaleState.Connected(battery = null)

        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
        compose.onAllNodes(hasText(text(R.string.error_not_found))).assertCountEquals(0)
    }

    /** A filter brew: taring, pouring, brewing, then the server lifted and Done; its summary and chart. */
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class EspressoSetupEnLightTest : ScreenEspressoSetupTests("en", "light")

@Config(qualifiers = "en-w411dp-h891dp-night-xxhdpi")
class EspressoSetupEnDarkTest : ScreenEspressoSetupTests("en", "dark")

@Config(qualifiers = "pt-rBR-w411dp-h891dp-notnight-xxhdpi")
class EspressoSetupPtBrLightTest : ScreenEspressoSetupTests("pt-BR", "light")

@Config(qualifiers = "pt-rBR-w411dp-h891dp-night-xxhdpi")
class EspressoSetupPtBrDarkTest : ScreenEspressoSetupTests("pt-BR", "dark")

@Config(qualifiers = "en-w320dp-h640dp-notnight-xxhdpi")
class EspressoSetupSmallEnLightTest : ScreenEspressoSetupTests("en", "light", compactOnly = true)

@Config(qualifiers = "en-w320dp-h640dp-night-xxhdpi")
class EspressoSetupSmallEnDarkTest : ScreenEspressoSetupTests("en", "dark", compactOnly = true)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-notnight-xxhdpi")
class EspressoSetupSmallPtBrLightTest : ScreenEspressoSetupTests("pt-BR", "light", compactOnly = true)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-night-xxhdpi")
class EspressoSetupSmallPtBrDarkTest : ScreenEspressoSetupTests("pt-BR", "dark", compactOnly = true)
