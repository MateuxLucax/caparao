package dev.mateux.caparao.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mateux.caparao.R
import dev.mateux.caparao.data.local.BagRecipeAdjustmentRepository
import dev.mateux.caparao.data.local.BuiltInRecipes
import dev.mateux.caparao.model.BagRecipeAdjustment
import dev.mateux.caparao.ui.format.Format
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The brew flow uses the picked coffee's adjustment for the chosen recipe (#463). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
abstract class BrewAdjustmentScreenTests(lang: String, theme: String) : ScreenTestSupport(lang, theme) {
    private val adjustments = BagRecipeAdjustmentRepository(db.bagRecipeAdjustments())

    private fun weighOf(grams: Double) = context.getString(R.string.weigh_of, Format.grams(grams, locale))

    /** Espresso Oster is 19 g, grind 10 and 92 °C; "Caparaó natural" brews it at 18 g, grind 9 and 91 °C. */
    private fun espressoCoffeeStep(): SessionScale {
        seedCoffeeBags()
        runBlocking { adjustments.save(BagRecipeAdjustment("bag-1", BuiltInRecipes.ESPRESSO_ID, grind = 9, waterTempCelsius = 91, doseGrams = 18.0), 1) }
        val scale = open("synthetic-espresso", 0)
        click(R.string.mode_espresso)
        // The dose step's preselected recipe; on a 320 dp screen only the current milestone name shows, so wait for this instead.
        compose.waitUntilAtLeastOneExists(hasText("Espresso Oster") and isSelectable(), 5_000)
        click(R.string.next)
        waitFor(R.string.coffee_step_title)
        return scale
    }

    private fun toBeans() {
        compose.onNodeWithTag("coffee-step-next").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
    }

    /** The adjusted bag says so, shows its values beside the recipe's, and the brew weighs, prepares and saves with them. */
    @Test
    fun anAdjustedBagBrewsWithItsValues() {
        val scale = espressoCoffeeStep()
        compose.onNodeWithText(context.getString(R.string.coffee_adjusted_for, "Espresso Oster"), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("coffee-adjustment").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.adjustment_recipe_value, "10"), useUnmergedTree = true).assertExists()
        toBeans()
        compose.onNodeWithText(weighOf(18.0)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.weigh_dose_for, "Caparaó natural")).assertIsDisplayed()
        compose.onNodeWithTag("weigh-step-next").performClick()
        waitFor(R.string.prepare_ready)
        compose.onNodeWithTag("portafilter-dose").assertTextContains(context.getString(R.string.brew_strip_grind, "9"), substring = true)
        compose.onNodeWithTag("portafilter-dose").assertTextContains("91 ${text(R.string.unit_celsius)}", substring = true)
        click(R.string.prepare_ready)
        play(scale, 21_000)
        playUntilShown(scale, R.string.brew_saved, 60_000)
        compose.waitUntil(5_000) { runBlocking { brews.latest() != null } }
        val saved = runBlocking { brews.latest() }!!
        assertEquals("9", saved.grind)
        assertEquals(91, saved.waterTempCelsius)
        assertEquals(18.0, saved.doseGrams, 0.0)
        assertEquals("bag-1", saved.bagId)
    }

    /** "Use the recipe" leaves the adjustment out of this brew only, and can be taken back. */
    @Test
    fun useTheRecipeBrewsThisOnceAsTheRecipeSays() {
        espressoCoffeeStep()
        compose.onNodeWithText(text(R.string.adjustment_use_recipe)).performScrollTo().performClick()
        waitFor(R.string.adjustment_following_recipe)
        compose.onNodeWithText(text(R.string.adjustment_use_adjustment)).assertExists()
        toBeans()
        compose.onNodeWithText(weighOf(19.0)).assertIsDisplayed()
        compose.onNodeWithTag("weigh-dose-source").assertDoesNotExist()
        compose.onNodeWithTag("weigh-step-next").performClick()
        waitFor(R.string.prepare_ready)
        compose.onNodeWithTag("portafilter-dose").assertTextContains(context.getString(R.string.prepare_dose, Format.grams(19.0, locale)))
        // The adjustment itself is untouched.
        assertEquals(9, runBlocking { adjustments.get("bag-1", BuiltInRecipes.ESPRESSO_ID) }?.grind)
    }

    /** A bag without an adjustment, and no bag, brew as the recipe says. */
    @Test
    fun aBagWithoutAnAdjustmentAndNoBagFollowTheRecipe() {
        espressoCoffeeStep()
        compose.onNodeWithText("Mantiqueira").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("coffee-adjustment")).fetchSemanticsNodes().isEmpty() }
        toBeans()
        compose.onNodeWithText(weighOf(19.0)).assertIsDisplayed()
        compose.onNodeWithTag("weigh-dose-source").assertDoesNotExist()
        // Back to the coffee: the adjusted bag's card returns with the bag, and No bag drops it again.
        tapBack()
        waitFor(R.string.coffee_step_title)
        compose.onNodeWithText("Caparaó natural").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("coffee-adjustment"), 5_000)
        compose.onNodeWithText(text(R.string.no_bag)).performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("coffee-adjustment")).fetchSemanticsNodes().isEmpty() }
        toBeans()
        compose.onNodeWithText(weighOf(19.0)).assertIsDisplayed()
    }

    /** Filter: the adjusted dose scales the water, and the strip above the checklist names the grind. */
    @Test
    fun filterWeighsTheAdjustedDose() {
        seedCoffeeBags()
        runBlocking { adjustments.save(BagRecipeAdjustment("bag-1", BuiltInRecipes.MELITTA_ID, grind = 23, doseGrams = 21.0), 1) }
        open("synthetic-filter-lift", 0)
        click(R.string.mode_filter)
        compose.waitUntilAtLeastOneExists(hasText("Melitta 102"), 5_000)
        compose.onNodeWithText("Melitta 102").performScrollTo().performClick()
        waitFor(R.string.milestone_coffee)
        click(R.string.next)
        waitFor(R.string.coffee_step_title)
        compose.onNodeWithTag("coffee-adjustment").performScrollTo().assertIsDisplayed()
        toBeans()
        compose.onNodeWithText(weighOf(21.0)).assertIsDisplayed()
        compose.onNodeWithTag("weigh-dose-source").assertIsDisplayed()
        compose.onNodeWithTag("weigh-step-next").performClick()
        waitFor(R.string.prepare_ready)
        compose.onNodeWithTag("filter-prepare-dose").assertTextContains(context.getString(R.string.brew_strip_grind, "23"), substring = true)
    }
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class BrewAdjustmentEnLightTest : BrewAdjustmentScreenTests("en", "light")

@Config(qualifiers = "pt-rBR-w320dp-h640dp-night-xxhdpi")
class BrewAdjustmentPtBrSmallDarkTest : BrewAdjustmentScreenTests("pt-BR", "dark")
