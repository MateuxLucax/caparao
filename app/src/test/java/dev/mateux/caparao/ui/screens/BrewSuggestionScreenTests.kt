package dev.mateux.caparao.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mateux.caparao.R
import dev.mateux.caparao.data.local.BagRecipeAdjustmentRepository
import dev.mateux.caparao.model.BagRecipeAdjustment
import dev.mateux.caparao.model.BagRecipeAdjustmentReason
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.Recipe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What follows "How was it?" for a brew with a bag and a recipe (#468). The target window is 2:30 to 3:00. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
abstract class BrewSuggestionScreenTests(lang: String, theme: String) : ScreenTestSupport(lang, theme) {
    private val adjustments = BagRecipeAdjustmentRepository(db.bagRecipeAdjustments())

    private fun adjustment() = runBlocking { adjustments.get("bag-1", "suggest") }

    /** Saves a brew of [seconds] with the seeded bag, opens it from the Brews tab and rates it with [taste]. */
    private fun rate(seconds: Int, taste: Int) {
        seedCoffeeBags()
        runBlocking {
            recipes.save(
                Recipe(
                    id = "suggest", name = "Melitta 102", mode = BrewMode.FILTER, method = "Melitta", doseGrams = 20.0, targetRatio = 15.0,
                    grind = 22, waterTempCelsius = 93, targetMinMs = 150_000, targetMaxMs = 180_000, createdAt = 0, updatedAt = 0,
                ),
            )
            brews.save(
                Brew(
                    "rated", BrewMode.FILTER, System.currentTimeMillis(), seconds * 1000L, 20.0, 300.0, null, 1.6, 4.0, interrupted = false,
                    recipeId = "suggest", recipeName = "Melitta 102", bagId = "bag-1",
                ),
                emptyList(),
            )
        }
        openBrewAndRate(taste)
    }

    /** Brews tab -> the seeded brew -> Rate -> taste -> Save rating, waiting for the rating screen to close. */
    private fun openBrewAndRate(taste: Int) {
        open("idle-5min", 0)
        click(R.string.tab_brews)
        compose.waitUntilAtLeastOneExists(hasText(" ${text(R.string.unit_grams)} →", substring = true), 5_000)
        compose.onNode(hasText(" ${text(R.string.unit_grams)} →", substring = true)).performClick()
        waitFor(R.string.delete)
        compose.onNodeWithText(text(R.string.brew_rate)).performClick()
        waitFor(R.string.brew_taste)
        compose.onNodeWithText(text(taste)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.brew_save_rating)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.brew_how_was_it)).fetchSemanticsNodes().isEmpty() }
    }

    private fun waitForSuggestionClosed() =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.suggestion_title)).fetchSemanticsNodes().isEmpty() }

    /** Long and bitter: one number coarser, with the reasons; saving stores it with a history row for the brew. */
    @Test
    fun acceptingSavesTheSuggestionForTheCoffee() {
        rate(201, R.string.brew_taste_bitter)
        waitFor(R.string.suggestion_grind_coarser)
        compose.onNodeWithText("Caparaó natural · Melitta 102").assertIsDisplayed()
        compose.onNodeWithTag("suggestion-from").assertTextEquals("22")
        compose.onNodeWithTag("suggestion-to").assertTextEquals("23")
        compose.onNodeWithText(context.getString(R.string.suggestion_time_over, "03:21", 21, "02:30", "03:00")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.suggestion_taste_bitter)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.suggestion_notes, 0, 2)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.suggestion_keeps_grind)).assertIsDisplayed()
        click(R.string.suggestion_save)
        waitForSuggestionClosed()
        assertEquals(23, adjustment()?.grind)
        val history = runBlocking { adjustments.history("bag-1", "suggest").first() }.single()
        assertEquals(BagRecipeAdjustmentReason.SUGGESTION, history.reason)
        assertEquals("rated", history.brewId)
        // Back on the brew it was rated from.
        waitFor(R.string.delete)
    }

    /** − and + move the proposed value; back at today's value there is nothing to save. */
    @Test
    fun theProposedValueCanBeChangedBeforeSaving() {
        rate(201, R.string.brew_taste_bitter)
        waitFor(R.string.suggestion_grind_coarser)
        val grind = text(R.string.recipe_grind)
        compose.onNodeWithContentDescription(context.getString(R.string.adjustment_less, grind)).performClick()
        compose.onNodeWithTag("suggestion-to").assertTextEquals("22")
        compose.onNodeWithText(text(R.string.suggestion_save)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.adjustment_more, grind)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.adjustment_more, grind)).performClick()
        compose.onNodeWithTag("suggestion-to").assertTextEquals("24")
        click(R.string.suggestion_save)
        waitForSuggestionClosed()
        assertEquals(24, adjustment()?.grind)
    }

    /** "Not now" leaves everything as it was. */
    @Test
    fun decliningChangesNothing() {
        rate(120, R.string.brew_taste_sour)
        waitFor(R.string.suggestion_grind_finer)
        compose.onNodeWithTag("suggestion-to").assertTextEquals("21")
        click(R.string.suggestion_not_now)
        waitForSuggestionClosed()
        assertNull(adjustment())
    }

    /** On target but sour: hotter water, and the grind stays. */
    @Test
    fun onTargetAndSourSuggestsHotterWater() {
        rate(165, R.string.brew_taste_sour)
        waitFor(R.string.suggestion_temp_hotter)
        val c = text(R.string.unit_celsius)
        compose.onNodeWithTag("suggestion-from").assertTextEquals("93 $c")
        compose.onNodeWithTag("suggestion-to").assertTextEquals("94 $c")
        compose.onNodeWithText(context.getString(R.string.suggestion_time_on, "02:45", "02:30", "03:00")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.suggestion_keeps_temp)).assertIsDisplayed()
        click(R.string.suggestion_save)
        waitForSuggestionClosed()
        assertEquals(94, adjustment()?.waterTempCelsius)
        assertNull(adjustment()?.grind)
    }

    /** Short but bitter: both facts, no proposed value, and the way to the adjustment screen. */
    @Test
    fun oppositeSignalsLeadToTheAdjustmentScreen() {
        rate(120, R.string.brew_taste_bitter)
        waitFor(R.string.suggestion_opposite)
        compose.onNodeWithText(context.getString(R.string.suggestion_time_under, "02:00", 30, "02:30", "03:00")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.suggestion_taste_bitter)).assertIsDisplayed()
        compose.onNodeWithTag("suggestion-to").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.suggestion_save)).assertDoesNotExist()
        click(R.string.suggestion_edit_adjustment)
        waitFor(R.string.adjustment_title)
        assertNull(adjustment())
    }

    /** On target and balanced: no screen; the adjustment is marked tuned. */
    @Test
    fun onTargetAndBalancedMarksTheAdjustmentTuned() {
        rate(165, R.string.brew_taste_balanced)
        waitFor(R.string.delete)
        compose.onNodeWithText(text(R.string.suggestion_title)).assertDoesNotExist()
        assertTrue(adjustment()?.tuned == true)
    }

    /** The brew followed the recipe despite the saved adjustment: rating it closes without suggesting or tuning it. */
    @Test
    fun ratingABrewThatFollowedTheRecipeSuggestsNothingAndNeverTunes() {
        seedCoffeeBags()
        runBlocking {
            recipes.save(
                Recipe(
                    id = "suggest", name = "Melitta 102", mode = BrewMode.FILTER, method = "Melitta", doseGrams = 20.0, targetRatio = 15.0,
                    grind = 22, waterTempCelsius = 93, targetMinMs = 150_000, targetMaxMs = 180_000, createdAt = 0, updatedAt = 0,
                ),
            )
            adjustments.save(BagRecipeAdjustment("bag-1", "suggest", grind = 21, waterTempCelsius = 92, doseGrams = 21.0), 1)
            // The brew recorded the recipe's values: the adjustment was left out for this brew (#463).
            brews.save(
                Brew(
                    "rated", BrewMode.FILTER, System.currentTimeMillis(), 165 * 1000L, 20.0, 300.0, null, 1.6, 4.0, interrupted = false,
                    recipeId = "suggest", recipeName = "Melitta 102", bagId = "bag-1", grind = "22", waterTempCelsius = 93,
                ),
                emptyList(),
            )
        }
        openBrewAndRate(R.string.brew_taste_balanced)
        // The rating closed with nothing to say: no suggestion screen, no tuned confirmation, and the unused
        // adjustment is exactly as it was.
        waitFor(R.string.delete)
        compose.onAllNodesWithText(text(R.string.suggestion_title)).assertCountEquals(0)
        compose.onAllNodesWithText(text(R.string.suggestion_tuned)).assertCountEquals(0)
        assertEquals(BagRecipeAdjustment("bag-1", "suggest", grind = 21, waterTempCelsius = 92, doseGrams = 21.0), adjustment())
    }
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class BrewSuggestionEnLightTest : BrewSuggestionScreenTests("en", "light")

@Config(qualifiers = "pt-rBR-w320dp-h640dp-night-xxhdpi")
class BrewSuggestionPtBrSmallDarkTest : BrewSuggestionScreenTests("pt-BR", "dark")
