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
abstract class ScreenCoffeesTests(lang: String, theme: String, compactOnly: Boolean = false) : ScreenTestSupport(lang, theme, compactOnly) {
    /**
     * Back rows with the coffee step (#291): Beans goes back to Coffee with its pick kept, Coffee goes back to Dose, and the
     * X closes the brew from Coffee.
     */
    @Test
    fun coffeeBackRowsKeepThePickAndTheXClosesFromCoffee() {
        seedCoffeeBags()
        espressoDose()
        // The bags load off the main thread; Next goes to Coffee only once they are in.
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.milestone_coffee)), 5_000)
        click(R.string.next)
        waitFor(R.string.coffee_step_title)
        compose.onNodeWithText("Mantiqueira").performClick()
        click(R.string.next)
        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
        tapBack()
        waitFor(R.string.coffee_step_title)
        compose.onNode(hasText("Mantiqueira") and isSelected()).assertExists()
        tapBack()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text(R.string.coffee_step_title))).fetchSemanticsNodes().isEmpty() }
        compose.waitForIdle()
        // The dose step again: its recipe chips and Next (the espresso recipe shows its summary, not the free ratio).
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.free_brew)), 5_000)
        compose.onNodeWithText(text(R.string.next)).assertIsDisplayed()
        // Next from the dose goes to Coffee again, with the same bag still picked.
        click(R.string.next)
        waitFor(R.string.coffee_step_title)
        compose.onNode(hasText("Mantiqueira") and isSelected()).assertExists()
        tapCloseBrew()
        waitForBrewClosed()
    }

    @Test
    fun coffeeStepBeforeTheBeansInEspresso() = coffeeStepBeforeTheBeans("synthetic-espresso") { click(R.string.mode_espresso) }

    @Test
    fun coffeeStepBeforeTheBeansInFilter() = coffeeStepBeforeTheBeans("synthetic-filter-lift") { click(R.string.mode_filter) }

    /** The home selector names the press by its content description; its text is the short "Press". */
    @Test
    fun coffeeStepBeforeTheBeansInFrenchPress() = coffeeStepBeforeTheBeans("synthetic-french-press") {
        compose.onNodeWithContentDescription(text(R.string.mode_french_press)).performClick()
    }

    /** Seeds one open bag, roasted 12 days ago, and opens its detail from the Coffees tab. */
    @Test
    fun coffeeBagAddSheet() {
        open("press-ramp", 38_300)
        click(R.string.tab_coffees)
        waitFor(R.string.coffees_empty)
        compose.onNodeWithText(text(R.string.coffee_add), useUnmergedTree = true).performClick()
        waitFor(R.string.coffee_weight)
        compose.onNodeWithText(text(R.string.save)).assertIsNotEnabled()
        compose.onAllNodesWithText(text(R.string.coffee_origin)).assertCountEquals(0)
        compose.onNode(hasSetTextAction() and hasText(text(R.string.coffee_name))).performTextReplacement("Mantiqueira")
        compose.onNodeWithText(text(R.string.save)).assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.coffee_weight))).performTextReplacement("250")
        compose.onNodeWithText(text(R.string.save)).assertIsEnabled()
        click(R.string.coffee_more_details)
        compose.onNodeWithText(text(R.string.coffee_origin)).assertIsDisplayed()
        click(R.string.save)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.coffee_weight)).fetchSemanticsNodes().isEmpty() }
        compose.waitUntilAtLeastOneExists(hasText("Mantiqueira", substring = true), 5_000)
        compose.onNodeWithText(context.getString(R.string.coffee_left, "250", "250")).assertIsDisplayed()
    }

    /**
     * A bag's detail (C4): what is left, roast and brews. A brew opens and Back returns here; finishing and reopening
     * move the bag between the states; Edit opens the same sheet, filled.
     */
    /**
     * A bag's detail (C4): what is left, roast and brews. A brew opens and Back returns here; finishing and reopening
     * move the bag between the states; Edit opens the same sheet, filled.
     */
    @Test
    fun coffeeBagDetail() {
        openCoffeeBag()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.coffee_roasted_days, 12, 12), substring = true).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.rating_stars, 4)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.mode_filter), substring = true).performClick()
        waitFor(R.string.share)
        // The brew detail's arrow is labelled Close.
        compose.onNodeWithContentDescription(text(R.string.close)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(context.getString(R.string.coffee_left, "232", "250")), 5_000)
        click(R.string.coffee_mark_finished)
        waitFor(R.string.coffee_mark_open)
        compose.onNodeWithText(text(R.string.coffee_finished)).assertIsDisplayed()
        click(R.string.coffee_mark_open)
        waitFor(R.string.coffee_mark_finished)
        click(R.string.edit)
        waitFor(R.string.coffee_edit)
        compose.onNode(hasSetTextAction() and hasText("Caparaó natural")).assertIsDisplayed()
    }

    /** A bag with no brews says so (C4). */
    @Test
    fun coffeeBagDetailWithoutBrews() {
        openCoffeeBag(withBrew = false)
        compose.onNodeWithText(context.getString(R.string.coffee_left, "250", "250")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.coffee_no_brews)).assertIsDisplayed()
    }

    /** The coffee bag's Delete asks first; Cancel keeps the bag, Delete removes it and keeps the brews' names (#181, C5). */
    @Test
    fun coffeeBagDeleteAsksFirst() {
        openCoffeeBag()
        click(R.string.delete)
        waitFor(R.string.delete_coffee_title)
        compose.onNodeWithText(text(R.string.delete_coffee_text)).assertIsDisplayed()
        clickLast(R.string.cancel)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.delete_coffee_title)).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(text(R.string.edit)).assertIsDisplayed()
        click(R.string.delete)
        waitFor(R.string.delete_coffee_title)
        clickLast(R.string.delete)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Caparaó natural", substring = true).fetchSemanticsNodes().isEmpty() }
    }

    /** Closing the sheet with other edits asks first (#181): Cancel keeps editing, Discard closes it. */
    @Test
    fun coffeeBagSheetAsksBeforeDiscarding() {
        openCoffeeBag()
        click(R.string.edit)
        waitFor(R.string.coffee_edit)
        compose.onNode(hasSetTextAction() and hasText(text(R.string.coffee_roaster))).performTextReplacement("Outra torra")
        click(R.string.cancel)
        waitFor(R.string.discard_changes_title)
        clickLast(R.string.cancel)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.discard_changes_title)).fetchSemanticsNodes().isEmpty() }
        compose.onNode(hasSetTextAction() and hasText("Outra torra")).assertIsDisplayed()
        click(R.string.cancel)
        waitFor(R.string.discard_changes_title)
        clickLast(R.string.discard)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.coffee_edit)).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(text(R.string.edit)).assertIsDisplayed()
    }

    /** Back and a clean sheet: Back with other edits asks first; a clean sheet closes without asking. */
    @Test
    fun coffeeBagSheetBackAsksOnlyWithEdits() {
        openCoffeeBag()
        click(R.string.edit)
        waitFor(R.string.coffee_edit)
        Espresso.pressBack()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.coffee_edit)).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(text(R.string.edit)).assertIsDisplayed()
        click(R.string.edit)
        waitFor(R.string.coffee_edit)
        compose.onNode(hasSetTextAction() and hasText(text(R.string.coffee_roaster))).performTextReplacement("Outra torra")
        Espresso.pressBack()
        waitFor(R.string.discard_changes_title)
    }

    /** A brew that used [dose] grams of the bag [bagId], saved with its bag link (#288). */
    /**
     * The Coffees tab (C2): open bags first, with what is left and what their brews say. The finished bag sits under
     * its button, hidden until it is expanded, and hides again.
     */
    @Test
    fun coffeesTabCollapsesFinishedBags() {
        val day = 86_400_000L
        val now = System.currentTimeMillis()
        runBlocking {
            CoffeeBagRepository(db.coffeeBags()).apply {
                save(CoffeeBag("bag-open", "Caparaó natural", roaster = "Torra Alta", roastedAt = now - 12 * day, weightGrams = 250.0, createdAt = now))
                save(CoffeeBag("bag-low", "Mantiqueira", weightGrams = 250.0, createdAt = now - 1_000))
                save(CoffeeBag("bag-done", "Bourbon amarelo", weightGrams = 250.0, finished = true, createdAt = now - 2_000))
            }
        }
        linkBrew("brew-open", "bag-open", 18.0, rating = 4)
        linkBrew("brew-low", "bag-low", 238.0)
        open("press-ramp", 38_300)
        click(R.string.tab_coffees)
        compose.waitUntilAtLeastOneExists(hasText("Caparaó natural", substring = true), 5_000)
        // An open bag with grams left, and a nearly used one.
        compose.onNodeWithText(context.getString(R.string.coffee_left, "232", "250")).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.coffee_left, "12", "250")).assertIsDisplayed()
        // Both bags have one linked brew each.
        compose.onAllNodesWithText(context.resources.getQuantityString(R.plurals.coffee_brew_count, 1, 1), substring = true).assertCountEquals(2)
        // Collapsed: the finished bag is out of sight, behind its button with the count.
        compose.onNodeWithText("Bourbon amarelo", substring = true).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.coffees_finished_count, 1)).performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Bourbon amarelo", substring = true), 5_000)
        compose.onNodeWithText("Bourbon amarelo", substring = true).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.coffees_finished_count, 1)).performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Bourbon amarelo", substring = true).fetchSemanticsNodes().isEmpty() }
    }

    /** The home-screen widget: saved and empty at the declared 180x176 dp minimum, rendered and asserted. */
    @Test
    fun coffees() {
        val day = 86_400_000L
        val now = System.currentTimeMillis()
        kotlinx.coroutines.runBlocking {
            val bags = CoffeeBagRepository(db.coffeeBags())
            bags.save(CoffeeBag("bag-1", "Caparaó natural", roaster = "Torra Alta", origin = "Catuaí amarelo, natural", roastedAt = now - 12 * day, weightGrams = 250.0, createdAt = now))
            bags.save(CoffeeBag("bag-2", "Mantiqueira", roaster = "Sul de Minas", roastedAt = now - 40 * day, weightGrams = 500.0, finished = true, createdAt = now - 30 * day))
        }
        open("press-ramp", 38_300)
        click(R.string.tab_coffees)
        compose.waitUntilAtLeastOneExists(hasText("Caparaó natural", substring = true), 5_000)
        compose.onNodeWithText("Caparaó natural", substring = true).assertIsDisplayed()
    }

    /** The home tab in each brand color (#108). */
    /** H1: plain http to a public host is refused, with the fix under the field, and nothing is sent. */
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class CoffeesEnLightTest : ScreenCoffeesTests("en", "light")

@Config(qualifiers = "en-w411dp-h891dp-night-xxhdpi")
class CoffeesEnDarkTest : ScreenCoffeesTests("en", "dark")

@Config(qualifiers = "pt-rBR-w411dp-h891dp-notnight-xxhdpi")
class CoffeesPtBrLightTest : ScreenCoffeesTests("pt-BR", "light")

@Config(qualifiers = "pt-rBR-w411dp-h891dp-night-xxhdpi")
class CoffeesPtBrDarkTest : ScreenCoffeesTests("pt-BR", "dark")

@Config(qualifiers = "en-w320dp-h640dp-notnight-xxhdpi")
class CoffeesSmallEnLightTest : ScreenCoffeesTests("en", "light", compactOnly = true)

@Config(qualifiers = "en-w320dp-h640dp-night-xxhdpi")
class CoffeesSmallEnDarkTest : ScreenCoffeesTests("en", "dark", compactOnly = true)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-notnight-xxhdpi")
class CoffeesSmallPtBrLightTest : ScreenCoffeesTests("pt-BR", "light", compactOnly = true)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-night-xxhdpi")
class CoffeesSmallPtBrDarkTest : ScreenCoffeesTests("pt-BR", "dark", compactOnly = true)
