package dev.mateux.caparao.ui.screens
import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.luminance
import dev.mateux.caparao.ui.components.PressArt
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
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
import dev.mateux.caparao.BrewWidgetProvider
import dev.mateux.caparao.BuildConfig
import dev.mateux.caparao.R
import dev.mateux.caparao.data.diagnostics.CaptureWriter
import dev.mateux.caparao.data.local.BrewDatabase
import dev.mateux.caparao.data.local.BrewEntity
import dev.mateux.caparao.data.local.BrewRepository
import dev.mateux.caparao.data.local.BuiltInRecipes
import dev.mateux.caparao.data.local.CoffeeBagRepository
import dev.mateux.caparao.data.local.RecipeRepository
import dev.mateux.caparao.data.local.SeedBuiltInRecipes
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.BrewSample
import dev.mateux.caparao.model.CoffeeBag
import dev.mateux.caparao.model.Recipe
import dev.mateux.caparao.model.RecipeStep
import dev.mateux.caparao.model.ScaleError
import dev.mateux.caparao.model.ScaleState
import dev.mateux.caparao.model.StepKind
import dev.mateux.caparao.ui.recipes.RecipeEditorViewModel
import dev.mateux.caparao.ui.coffees.CoffeeBagDetailViewModel
import dev.mateux.caparao.ui.coffees.CoffeeBagsViewModel
import dev.mateux.caparao.ui.recipes.RecipesViewModel
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
import dev.mateux.caparao.data.local.SettingsStore
import dev.mateux.caparao.ui.App
import dev.mateux.caparao.ui.components.AppActions
import dev.mateux.caparao.ui.Tab
import dev.mateux.caparao.brew.StepState
import dev.mateux.caparao.brew.Pace
import dev.mateux.caparao.brew.BrewPhase
import dev.mateux.caparao.ui.components.BrewNumbers
import dev.mateux.caparao.ui.brew.BrewStep
import dev.mateux.caparao.ui.brew.BrewUiState
import dev.mateux.caparao.ui.brew.BrewViewModel
import dev.mateux.caparao.ui.brew.FlowChart
import dev.mateux.caparao.ui.brew.FlowPoint
import dev.mateux.caparao.ui.brew.GuidedStep
import dev.mateux.caparao.ui.brews.BrewDetailViewModel
import dev.mateux.caparao.ui.brews.BrewsViewModel
import dev.mateux.caparao.ui.format.Format
import dev.mateux.caparao.ui.home.HomeViewModel
import dev.mateux.caparao.ui.onboarding.OnboardingViewModel
import dev.mateux.caparao.ui.settings.ScaleStatusViewModel
import dev.mateux.caparao.ui.server.ServerViewModel
import dev.mateux.caparao.ui.settings.TargetRatioViewModel
import dev.mateux.caparao.ui.theme.AppTheme
import dev.mateux.caparao.ui.components.ScaleReadout
import dev.mateux.caparao.ui.components.readableOn
import dev.mateux.caparao.ui.components.ringStroke
import dev.mateux.caparao.brew.ratioBand
import dev.mateux.caparao.brew.RatioBand
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
abstract class ScreenBrewsTests(lang: String, theme: String, compactOnly: Boolean = false) : ScreenTestSupport(lang, theme, compactOnly) {
    /** Long presses do not enter a selection mode; tapping a brew still opens its detail. */
    @Test
    fun longPressDoesNotSelectAndTapOpensDetail() {
        val saved = listOf(Triple(238.0, 185_000L, 4), Triple(245.0, 200_000L, 3), Triple(250.0, 178_000L, 5))
        runBlocking {
            saved.forEachIndexed { i, (water, duration, rating) ->
                val brew = Brew(
                    "saved-$i", BrewMode.FILTER, BREWED_AT - (2 - i) * 86_400_000L, duration, 15.0, water, null, water / (duration / 1000.0), 6.0,
                    interrupted = false, recipeId = "screen-test-v60", recipeName = "V60", rating = rating,
                )
                brews.save(brew, curve(water, duration))
            }
        }
        open("press-ramp", 0)
        click(R.string.tab_brews)
        val rowText = { water: Double ->
            "${Format.grams(15.0, locale)} ${text(R.string.unit_grams)} → ${Format.grams(water, locale)}"
        }
        val row = { water: Double ->
            compose.onNodeWithText(
                rowText(water),
                substring = true,
                useUnmergedTree = true,
            )
        }
        compose.waitUntilAtLeastOneExists(hasText(rowText(250.0), substring = true), 5_000)
        // A long press no longer starts a selection mode; it opens the detail like a tap.
        row(250.0).performTouchInput { longClick() }
        waitFor(R.string.delete)
        compose.onNodeWithContentDescription(text(R.string.close)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(rowText(250.0), substring = true), 5_000)
        // A tap still opens the detail.
        row(250.0).performClick()
        waitFor(R.string.delete)
        compose.onNodeWithText(text(R.string.brew_notes)).assertExists()
        compose.waitForIdle()
    }

    private fun save(id: String, daysAgo: Int, bagId: String? = null, ratedAt: Long? = null, rating: Int? = null, recipe: String = "V60") = runBlocking {
        brews.save(
            Brew(
                id, BrewMode.FILTER, BREWED_AT - daysAgo * 86_400_000L, 185_000, 15.0, 240.0 + daysAgo, null, 1.3, 6.0, interrupted = false,
                recipeName = recipe, bagId = bagId, ratedAt = ratedAt, rating = rating, grind = "22",
                taste = if (ratedAt != null) dev.mateux.caparao.model.BrewTaste.BALANCED else null,
            ),
            curve(240.0, 185_000),
        )
    }

    /** With every brew rated there is no "To rate" section; a rated row shows its taste, grind and stars. */
    @Test
    fun ratedBrewsHideTheToRateSection() {
        save("rated", 0, ratedAt = BREWED_AT, rating = 4)
        open("press-ramp", 0)
        click(R.string.tab_brews)
        compose.waitUntilAtLeastOneExists(hasText(context.getString(R.string.brew_row_grind, "22"), substring = true), 5_000)
        compose.onNodeWithText(text(R.string.brews_to_rate)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.brew_rate)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.brew_taste_balanced), substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.rating_stars, 4), useUnmergedTree = true).assertExists()
        compose.onNodeWithText("${text(R.string.no_bag)} · V60", useUnmergedTree = true).assertExists()
    }

    /** A brew not rated yet sits under "To rate"; its Rate button opens the rating screen. */
    @Test
    fun toRateSectionOpensTheRating() {
        save("rated", 1, ratedAt = BREWED_AT, rating = 5)
        save("pending", 0)
        open("press-ramp", 0)
        click(R.string.tab_brews)
        waitFor(R.string.brews_to_rate)
        compose.onNodeWithText(text(R.string.brews_rated)).assertExists()
        compose.onAllNodesWithText(text(R.string.brew_rate)).assertCountEquals(1)
        click(R.string.brew_rate)
        waitFor(R.string.brew_taste_balanced)
    }

    /** The filter row lists the open bags; picking one keeps its brews only, long names included. */
    @Test
    fun filtersTheListByCoffee() {
        seedCoffeeBags()
        val long = "Fazenda Santa Clara do Caparaó lote 12 catuaí amarelo fermentado"
        runBlocking { CoffeeBagRepository(db.coffeeBags()).save(CoffeeBag("bag-3", long, weightGrams = 250.0, createdAt = 1)) }
        save("one", 0, bagId = "bag-1", recipe = "Melitta 102 da manhã com bloom longo e três despejos")
        save("two", 1, bagId = "bag-3")
        save("three", 2)
        open("press-ramp", 0)
        click(R.string.tab_brews)
        waitFor(R.string.filter_all)
        compose.onNode(hasText(text(R.string.filter_all)) and isSelected()).assertExists()
        compose.onNodeWithText("$long · V60", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText(text(R.string.brew_rate)).assertCountEquals(3)
        compose.onNode(hasText("Caparaó natural") and isSelectable()).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.brew_rate)).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("Caparaó natural · Melitta", substring = true, useUnmergedTree = true).assertIsDisplayed()
        // A bag without brews shows the one-line empty state, and All brings the list back.
        compose.onNode(hasText("Mantiqueira") and isSelectable()).performClick()
        waitFor(R.string.brews_filter_empty)
        click(R.string.filter_all)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.brew_rate)).fetchSemanticsNodes().size == 3 }
    }
}

private const val BREWED_AT = 1_791_793_800_000L

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class BrewsEnLightTest : ScreenBrewsTests("en", "light")

@Config(qualifiers = "en-w411dp-h891dp-night-xxhdpi")
class BrewsEnDarkTest : ScreenBrewsTests("en", "dark")

@Config(qualifiers = "pt-rBR-w411dp-h891dp-notnight-xxhdpi")
class BrewsPtBrLightTest : ScreenBrewsTests("pt-BR", "light")

@Config(qualifiers = "pt-rBR-w411dp-h891dp-night-xxhdpi")
class BrewsPtBrDarkTest : ScreenBrewsTests("pt-BR", "dark")

@Config(qualifiers = "en-w320dp-h640dp-notnight-xxhdpi")
class BrewsSmallEnLightTest : ScreenBrewsTests("en", "light", compactOnly = true)

@Config(qualifiers = "en-w320dp-h640dp-night-xxhdpi")
class BrewsSmallEnDarkTest : ScreenBrewsTests("en", "dark", compactOnly = true)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-notnight-xxhdpi")
class BrewsSmallPtBrLightTest : ScreenBrewsTests("pt-BR", "light", compactOnly = true)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-night-xxhdpi")
class BrewsSmallPtBrDarkTest : ScreenBrewsTests("pt-BR", "dark", compactOnly = true)
