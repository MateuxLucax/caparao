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
abstract class ScreenRecipesTests(lang: String, theme: String, compactOnly: Boolean = false) : ScreenTestSupport(lang, theme, compactOnly) {
    @Test
    fun recipesModeSelector() {
        open("press-ramp", 38_300)
        click(R.string.tab_recipes)
        compose.waitUntilAtLeastOneExists(hasText("V60"), 5_000)
        compose.waitForIdle()
    }

    /** The new-recipe editor, where the brew mode is chosen once (#125); an existing recipe does not show it. */
    @Test
    fun newRecipeEditor() {
        open("press-ramp", 38_300)
        click(R.string.tab_recipes)
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.new_recipe), useUnmergedTree = true).performClick()
        waitFor(R.string.mode_espresso)
        compose.waitForIdle()
    }

    @Test
    fun filterRecipeEditorShowsIdealTotalTime() {
        open("press-ramp", 38_300)
        click(R.string.tab_recipes)
        compose.waitUntilAtLeastOneExists(hasText("V60 One and Done"), 5_000)
        compose.onNodeWithText("V60 One and Done").performClick()
        waitFor(R.string.recipe_name)
        compose.onNodeWithText(text(R.string.recipe_total_time)).assertIsDisplayed()
        compose.onAllNodesWithText(text(R.string.recipe_window_min)).onFirst().assertIsDisplayed()
        compose.onAllNodesWithText(text(R.string.recipe_window_max)).onFirst().assertIsDisplayed()
        compose.onNodeWithText("120").assertIsDisplayed()
        compose.onNodeWithText("150").assertIsDisplayed()
    }

    /** The editor offers the stir step for French press recipes (#219). The filter list is checked in RecipeEditorViewModelTest. */
    @Test
    fun frenchPressRecipeEditorOffersStir() {
        open("press-ramp", 38_300)
        click(R.string.tab_recipes)
        compose.onNodeWithContentDescription(text(R.string.mode_french_press)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("French press"), 5_000)
        compose.onNode(hasText("French press")).performClick()
        waitFor(R.string.recipe_name)
        compose.onAllNodesWithText(text(R.string.step_bloom)).onFirst().performClick()
        compose.onNodeWithText(text(R.string.step_stir)).assertIsDisplayed()
    }

    /** Opens the V60 recipe in its editor, as the recipes list leads to it. */
    @Test
    fun untouchedRecipeEditorBackClosesAtOnce() {
        openV60Editor()
        tapEditorClose()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.recipe_name)).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(text(R.string.discard_changes_title)).assertDoesNotExist()
    }

    /** Back with changes asks first; Cancel keeps the editor, Discard closes it (#181, B1 → B2). */
    @Test
    fun changedRecipeEditorBackAsksBeforeDiscarding() {
        openV60Editor()
        compose.onNode(hasSetTextAction() and hasText("V60 One and Done")).performTextReplacement("V60 short")
        compose.waitForIdle()
        tapEditorClose()
        waitFor(R.string.discard_changes_title)
        compose.onNodeWithText(text(R.string.discard)).assertIsDisplayed()
        click(R.string.cancel)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.discard_changes_title)).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(text(R.string.recipe_name)).assertIsDisplayed()
        tapEditorClose()
        waitFor(R.string.discard_changes_title)
        click(R.string.discard)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.recipe_name)).fetchSemanticsNodes().isEmpty() }
    }

    /**
     * The coffee step before the beans, in [mode] (#291): the newest open bag is selected, No bag is offered, and a bag
     * with less than the dose left says so. Next then goes on to weighing.
     */
    /** Two open bags: the newest, with plenty left, and an older one with 10 g, under the dose of any mode. */
    @Test
    fun espressoRecipeIsReadOnly() {
        settings.setLastRecipeId(BrewMode.ESPRESSO, BuiltInRecipes.ESPRESSO_ID)
        open("idle-5min", 0)
        click(R.string.mode_espresso)
        compose.waitUntilAtLeastOneExists(hasText("Espresso Oster"), 5_000)
        compose.waitForIdle()

        val grams = text(R.string.unit_grams)
        compose.onNodeWithText("${Format.grams(19.0, locale)} $grams").assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithText("${Format.grams(19.0 * 2.2, locale)} $grams").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.ratio_value, Format.ratio(2.2, locale))).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.espresso_window, "27", "33")).assertIsDisplayed()
        compose.onNodeWithText("10").assertIsDisplayed()
        compose.onNodeWithText("92 ${text(R.string.unit_celsius)}").assertIsDisplayed()
        compose.onNodeWithText("Pirados por Café", substring = true).assertIsDisplayed()

        // No ratio or dose controls: to change the recipe, the user edits it in Recipes.
        compose.onAllNodes(hasText(text(R.string.ratio_caption))).assertCountEquals(0)
        compose.onNodeWithContentDescription(text(R.string.ratio_less)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.ratio_more)).assertDoesNotExist()
    }

    /** Espresso free brew: the water follows the dose and the ratio, and can't be edited (#189). */
    @Test
    fun filterRecipeWaterScalesItsSteps() {
        val recipe = Recipe(
            id = "screen-test-water", name = "V60 water test", mode = BrewMode.FILTER, method = "V60", doseGrams = 15.0, targetRatio = 16.7,
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 45.0, 0),
                RecipeStep(StepKind.POUR, 90.0, 30_000),
                RecipeStep(StepKind.POUR, 250.0, 60_000),
            ),
            createdAt = 0, updatedAt = 0,
        )
        runBlocking { recipes.save(recipe) }
        settings.setLastRecipeId(BrewMode.FILTER, recipe.id)
        open("idle-5min", 0)
        click(R.string.mode_filter)
        compose.waitUntilAtLeastOneExists(hasText(recipe.name), 5_000)
        compose.onNode(hasText(recipe.name) and isSelectable()).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("${Format.grams(250.0, locale)} ${text(R.string.unit_ml)}").assertIsDisplayed()
        repeat(15) { compose.onNodeWithContentDescription(text(R.string.ratio_more)).performClick() }
        compose.waitUntilAtLeastOneExists(hasText("${Format.grams(400.0, locale)} ${text(R.string.unit_ml)}"), 5_000)
        compose.onNodeWithText("${Format.grams(400.0, locale)} ${text(R.string.unit_ml)}").assertIsDisplayed()
        compose.onNodeWithText("${Format.grams(24.0, locale)} ${text(R.string.unit_grams)}").assertIsDisplayed()
        compose.onNodeWithText("${Format.grams(72.0, locale)} ${text(R.string.unit_grams)}").assertIsDisplayed()
        compose.onNodeWithText("${Format.grams(144.0, locale)} ${text(R.string.unit_grams)}").assertIsDisplayed()
        compose.onNodeWithText("${Format.grams(400.0, locale)} ${text(R.string.unit_grams)}").assertIsDisplayed()
        // Reduced motion switches the whole scaled plan together, with no stale outgoing recipe values left behind.
        compose.onAllNodesWithText("${Format.grams(45.0, locale)} ${text(R.string.unit_grams)}").assertCountEquals(0)
        compose.onAllNodesWithText("${Format.grams(90.0, locale)} ${text(R.string.unit_grams)}").assertCountEquals(0)
        compose.onAllNodesWithText("${Format.grams(250.0, locale)} ${text(R.string.unit_grams)}").assertCountEquals(0)
        compose.onAllNodesWithText("${Format.grams(15.0, locale)} ${text(R.string.unit_grams)}").assertCountEquals(0)
        compose.onAllNodesWithText("${Format.grams(24.0, locale)} ${text(R.string.unit_grams)}").assertCountEquals(1)
        compose.onAllNodesWithText("${Format.grams(72.0, locale)} ${text(R.string.unit_grams)}").assertCountEquals(1)
        compose.onAllNodesWithText("${Format.grams(144.0, locale)} ${text(R.string.unit_grams)}").assertCountEquals(1)
        compose.onAllNodesWithText("${Format.grams(400.0, locale)} ${text(R.string.unit_grams)}").assertCountEquals(1)
        val ratio = context.getString(R.string.ratio_value, Format.ratio(recipe.targetRatio, locale))
        compose.onNodeWithText(ratio).assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithText("00:30").assertIsDisplayed()
        compose.onNodeWithText("01:00").assertIsDisplayed()
    }

    /**
     * The guided filter brew (wireframes 3a–3e) on the synthetic filter session, following a recipe shaped like it:
     * bloom to 50 g, a wait, then pours to 100, 150 and 200 g at 0:35, 0:50 and 1:00 from the first water (20 s in).
     */
    @Test
    fun frenchPressRecipeSelector() {
        open("press-ramp", 38_300)
        click(R.string.tab_recipes)
        // The visible label is short; the full method name is the button's accessible name. The built-in recipe is
        // named "French press" in both languages, so its card confirms the mode switch.
        compose.onNodeWithContentDescription(text(R.string.mode_french_press)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("French press"), 5_000)
        compose.waitForIdle()
    }

    /** The progress bar's single TalkBack node names the current milestone, with the recipe step during Brew. */
    @Test
    fun frenchPressRecipeWaterScalesItsSteps() {
        open("idle-5min", 0)
        compose.onNodeWithContentDescription(text(R.string.mode_french_press)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("French press") and isSelectable(), 5_000)
        compose.waitForIdle()

        compose.onNodeWithText("${Format.grams(300.0, locale)} ${text(R.string.unit_ml)}").assertIsDisplayed()
        repeat(10) { compose.onNodeWithContentDescription(text(R.string.ratio_more)).performClick() }
        compose.waitUntilAtLeastOneExists(hasText("${Format.grams(400.0, locale)} ${text(R.string.unit_ml)}"), 5_000)
        compose.onNodeWithText("${Format.grams(26.7, locale)} ${text(R.string.unit_grams)}").assertIsDisplayed()
        compose.onNodeWithText("${Format.grams(133.3, locale)} ${text(R.string.unit_grams)}").assertIsDisplayed()
        compose.onNodeWithText("${Format.grams(400.0, locale)} ${text(R.string.unit_grams)}").assertIsDisplayed()
        compose.onNodeWithText("04:00").assertIsDisplayed()
        val ratio = context.getString(R.string.ratio_value, "15")
        compose.onNodeWithText(ratio).assertIsDisplayed().assertHasNoClickAction()
    }

    /** The timer, post-timer removal prompt and plunger-only serve animation (#126). */
    @Test
    fun frenchPressFreeBrewHasASteepFieldAndSteepsLikeARecipe() {
        val scale = open("synthetic-french-press", 0)
        compose.onNodeWithContentDescription(text(R.string.mode_french_press)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("French press") and isSelectable(), 5_000)
        click(R.string.free_brew)
        compose.waitUntilAtLeastOneExists(hasTestTag("press-steep-field"), 5_000)
        compose.onNodeWithTag("press-steep-field").assertIsDisplayed()
        compose.onNodeWithText("4:00").assertIsDisplayed()
        compose.onNodeWithTag("press-steep-field").performClick()
        compose.onNodeWithTag("press-steep-input").performTextReplacement("4:30")
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).performClick()
        compose.waitUntilAtLeastOneExists(hasText("4:30"), 5_000)
        click(R.string.next)
        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
        compose.onNodeWithTag("weigh-step-next").performClick()
        waitFor(R.string.prepare_press_title)
        click(R.string.prepare_ready)

        // The first water: the ring pours under a title of its own, with no steep yet.
        play(scale, 8_000)
        compose.waitForIdle()
        waitFor(R.string.brewing_press)
        compose.onAllNodesWithText(text(R.string.guide_steep_text)).assertCountEquals(0)
        // The scale reads 300 g at 80 s, the target: the steep starts.
        play(scale, 80_000)
        waitFor(R.string.guide_steep_text)
        compose.onAllNodesWithText(text(R.string.brewing_press)).assertCountEquals(0)
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).assertIsDisplayed()
    }

    /** Walks a free French press from the dose screen: the steep and the water typed first, then Next, Beans and Ready. */
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class RecipesEnLightTest : ScreenRecipesTests("en", "light")

@Config(qualifiers = "en-w411dp-h891dp-night-xxhdpi")
class RecipesEnDarkTest : ScreenRecipesTests("en", "dark")

@Config(qualifiers = "pt-rBR-w411dp-h891dp-notnight-xxhdpi")
class RecipesPtBrLightTest : ScreenRecipesTests("pt-BR", "light")

@Config(qualifiers = "pt-rBR-w411dp-h891dp-night-xxhdpi")
class RecipesPtBrDarkTest : ScreenRecipesTests("pt-BR", "dark")

@Config(qualifiers = "en-w320dp-h640dp-notnight-xxhdpi")
class RecipesSmallEnLightTest : ScreenRecipesTests("en", "light", compactOnly = true)

@Config(qualifiers = "en-w320dp-h640dp-night-xxhdpi")
class RecipesSmallEnDarkTest : ScreenRecipesTests("en", "dark", compactOnly = true)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-notnight-xxhdpi")
class RecipesSmallPtBrLightTest : ScreenRecipesTests("pt-BR", "light", compactOnly = true)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-night-xxhdpi")
class RecipesSmallPtBrDarkTest : ScreenRecipesTests("pt-BR", "dark", compactOnly = true)
