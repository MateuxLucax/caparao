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
abstract class ScreenHomeTests(lang: String, theme: String, compactOnly: Boolean = false) : ScreenTestSupport(lang, theme, compactOnly) {
    @Test
    fun homeModeSelector() {
        open("press-ramp", 38_300)
        listOf(R.string.mode_manual, R.string.mode_filter, R.string.mode_espresso).forEach {
            compose.onNodeWithText(text(it)).assertIsDisplayed()
        }
    }

    /** The three real brew modes at the top of Recipes. */
    /** Each empty state is one line: the title alone, with no subtitle under it (#184). */
    @Test
    fun emptyStatesAreOneLine() {
        // Every mode seeds a built-in recipe, so the espresso list starts empty only once its seed is gone.
        runBlocking { recipes.all().filter { it.mode == BrewMode.ESPRESSO }.forEach { recipes.delete(it.id) } }
        open("press-ramp", 38_300)
        click(R.string.tab_brews)
        waitFor(R.string.brews_empty_title)
        compose.onNodeWithText(text(R.string.brews_empty_title)).assertIsDisplayed()
        click(R.string.tab_recipes)
        click(R.string.mode_espresso)
        waitFor(R.string.no_recipes_title)
        compose.onNodeWithText(text(R.string.no_recipes_title)).assertIsDisplayed()
        click(R.string.tab_coffees)
        waitFor(R.string.coffees_empty)
        compose.onNodeWithText(text(R.string.coffees_empty)).assertIsDisplayed()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.coffee_add), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun smallScreenWeigh() {
        assumeTrue(
            "small-screen variant only",
            context.resources.configuration.screenWidthDp == 320 && context.resources.configuration.screenHeightDp == 640,
        )
        open("synthetic-espresso", 0)
        click(R.string.mode_espresso)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.free_brew)), 5_000)
        click(R.string.free_brew)
        click(R.string.next)
        val appBarClose = hasContentDescription(text(R.string.close_brew))
        val espressoTitle = hasText(text(R.string.mode_espresso))
        // Espresso shows its named milestone, not a step counter; TalkBack gets the current name as its description.
        val weighProgress = hasContentDescription(text(R.string.milestone_beans))
        val nextAction = hasTestTag("weigh-step-next")
        compose.waitUntilAtLeastOneExists(appBarClose, 5_000)
        compose.waitUntilAtLeastOneExists(espressoTitle, 5_000)
        compose.waitUntilAtLeastOneExists(weighProgress, 5_000)
        compose.waitUntilAtLeastOneExists(nextAction, 5_000)
        // The screen enters through a shared-axis transition; wait for Compose to settle before asserting.
        compose.waitForIdle()
        compose.onNode(appBarClose).assertIsDisplayed()
        compose.onNode(espressoTitle).assertIsDisplayed()
        compose.onNode(weighProgress).assertIsDisplayed()
        compose.onNode(nextAction).assertIsDisplayed()
        compose.waitForIdle()
    }

    /** The full Home selector and manual scale entry at compact 320 dp phone width. */
    @Test
    fun smallScreenHome() {
        assumeTrue(
            "small-screen variant only",
            context.resources.configuration.screenWidthDp == 320 && context.resources.configuration.screenHeightDp == 640,
        )
        open("press-ramp", 38_300)
        listOf(R.string.mode_manual, R.string.mode_filter, R.string.mode_espresso, R.string.mode_french_press_short).forEach {
            compose.onNodeWithText(text(it)).assertIsDisplayed()
        }
        compose.waitForIdle()
    }

    /** The espresso guide prepares the portafilter before cup detection, taring and the first drop. */
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class HomeEnLightTest : ScreenHomeTests("en", "light")

@Config(qualifiers = "en-w411dp-h891dp-night-xxhdpi")
class HomeEnDarkTest : ScreenHomeTests("en", "dark")

@Config(qualifiers = "pt-rBR-w411dp-h891dp-notnight-xxhdpi")
class HomePtBrLightTest : ScreenHomeTests("pt-BR", "light")

@Config(qualifiers = "pt-rBR-w411dp-h891dp-night-xxhdpi")
class HomePtBrDarkTest : ScreenHomeTests("pt-BR", "dark")

@Config(qualifiers = "en-w320dp-h640dp-notnight-xxhdpi")
class HomeSmallEnLightTest : ScreenHomeTests("en", "light", compactOnly = true)

@Config(qualifiers = "en-w320dp-h640dp-night-xxhdpi")
class HomeSmallEnDarkTest : ScreenHomeTests("en", "dark", compactOnly = true)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-notnight-xxhdpi")
class HomeSmallPtBrLightTest : ScreenHomeTests("pt-BR", "light", compactOnly = true)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-night-xxhdpi")
class HomeSmallPtBrDarkTest : ScreenHomeTests("pt-BR", "dark", compactOnly = true)
