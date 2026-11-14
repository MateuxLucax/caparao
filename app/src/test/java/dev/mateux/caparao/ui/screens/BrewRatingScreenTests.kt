package dev.mateux.caparao.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mateux.caparao.R
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.BrewTaste
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
abstract class BrewRatingScreenTests(lang: String, theme: String) : ScreenTestSupport(lang, theme) {
    @Test
    fun espressoCanBeRatedFromItsSavedScreen() {
        espressoToSaved()
        assertSavedScreenHasNoRatingForm()
        openRatingAndSave()
        assertSavedRating(BrewMode.ESPRESSO)
    }

    @Test
    fun filterCanBeRatedFromItsSavedScreen() {
        filterToSaved()
        assertSavedScreenHasNoRatingForm()
        openRatingAndSave()
        assertSavedRating(BrewMode.FILTER)
    }

    @Test
    fun frenchPressCanBeRatedFromItsSavedScreen() {
        frenchPressToSaved()
        assertSavedScreenHasNoRatingForm()
        openRatingAndSave()
        assertSavedRating(BrewMode.FRENCH_PRESS)
    }

    @Test
    fun brewDetailKeepsCoffeeGrindAndTemperatureEditingAndCanOpenRating() {
        seedCoffeeBags()
        linkBrew("detail-rate", "bag-1", 18.0)
        open("idle-5min", 0)
        click(R.string.tab_brews)
        compose.waitUntilAtLeastOneExists(hasText(" ${text(R.string.unit_grams)} →", substring = true), 5_000)
        compose.onNode(hasText(" ${text(R.string.unit_grams)} →", substring = true)).performClick()
        waitFor(R.string.edit_notes)

        compose.onNodeWithText(text(R.string.edit_notes)).performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.brew_coffee))).assertExists()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.recipe_grind))).assertExists()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.recipe_temp))).assertExists()
        compose.onNodeWithText(text(R.string.brew_rate)).performClick()
        waitFor(R.string.brew_how_was_it)
        waitFor(R.string.brew_taste)
    }

    private fun espressoToSaved() {
        seedCoffeeBags()
        val scale = open("synthetic-espresso", 0)
        click(R.string.mode_espresso)
        waitFor(R.string.milestone_coffee)
        click(R.string.next)
        waitFor(R.string.coffee_step_title)
        selectSeededCoffee()
        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasTestTag("weigh-step-next"), 5_000)
        compose.onNodeWithTag("weigh-step-next").performClick()
        click(R.string.prepare_ready)
        play(scale, 21_000)
        playUntilShown(scale, R.string.brew_saved, 60_000)
        waitForSavedBrewInDatabase()
    }

    private fun filterToSaved() {
        seedCoffeeBags()
        val scale = open("synthetic-filter-lift", 0)
        click(R.string.mode_filter)
        waitFor(R.string.free_brew)
        click(R.string.free_brew)
        waitFor(R.string.milestone_coffee)
        click(R.string.next)
        waitFor(R.string.coffee_step_title)
        selectSeededCoffee()
        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasTestTag("weigh-step-next"), 5_000)
        compose.onNodeWithTag("weigh-step-next").performClick()
        click(R.string.prepare_ready)
        playUntilShown(scale, R.string.taring, 2_000)
        playUntilShown(scale, R.string.filter_ready, 7_000)
        play(scale, 60_000)
        play(scale, 90_000)
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).performClick()
        waitFor(R.string.brew_saved)
        waitForSavedBrewInDatabase()
    }

    private fun frenchPressToSaved() {
        seedCoffeeBags()
        val scale = open("synthetic-french-press", 0)
        compose.onNodeWithContentDescription(text(R.string.mode_french_press)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("French press") and isSelectable(), 5_000)
        waitFor(R.string.milestone_coffee)
        click(R.string.next)
        waitFor(R.string.coffee_step_title)
        selectSeededCoffee()
        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasTestTag("weigh-step-next"), 5_000)
        compose.onNodeWithTag("weigh-step-next").performClick()
        waitFor(R.string.prepare_press_title)
        click(R.string.prepare_ready)
        play(scale, 80_000)
        play(scale, 241_100)
        play(scale, 255_000)
        waitFor(R.string.guide_press_serve)
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).performClick()
        waitFor(R.string.brew_saved)
        waitForSavedBrewInDatabase()
    }

    private fun selectSeededCoffee() {
        compose.onNodeWithText("Caparaó natural").performClick()
        compose.onNodeWithTag("coffee-step-next").performClick()
    }

    private fun assertSavedScreenHasNoRatingForm() {
        compose.onNodeWithText(text(R.string.brew_saved)).assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.brew_rating))).assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.recipe_notes))).assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.brew_coffee))).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.brew_rate_now)).assertIsDisplayed()
    }

    private fun openRatingAndSave() {
        compose.onNodeWithText(text(R.string.brew_rate_now)).performScrollTo().performClick()
        waitFor(R.string.brew_how_was_it)
        compose.waitUntilAtLeastOneExists(hasText("Caparaó natural"), 5_000)
        compose.onNodeWithText("Caparaó natural").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.brew_taste_sour)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.brew_taste_balanced)).performScrollTo().performClick()
        compose.onNodeWithText("Chocolate").performScrollTo().performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.rating_stars, 4)).performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.recipe_notes)))
            .performScrollTo()
            .performTextReplacement("Peach and honey")
        compose.onNodeWithText(text(R.string.brew_save_rating)).performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.brew_how_was_it)).fetchSemanticsNodes().isEmpty() }
    }

    private fun assertSavedRating(mode: BrewMode) {
        val saved = runBlocking { brews.latest() }!!
        assertEquals(mode, saved.mode)
        assertEquals(BrewTaste.BALANCED, saved.taste)
        assertEquals(listOf("Chocolate"), saved.tastingNotesFound)
        assertEquals(4, saved.rating)
        assertEquals("Peach and honey", saved.notes)
        assertNotNull(saved.ratedAt)
    }

    private fun waitForSavedBrewInDatabase() {
        compose.waitUntil(5_000) { runBlocking { brews.latest() != null } }
    }
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class BrewRatingEnLightTest : BrewRatingScreenTests("en", "light")

@Config(qualifiers = "pt-rBR-w411dp-h891dp-notnight-xxhdpi")
class BrewRatingPtBrLightTest : BrewRatingScreenTests("pt-BR", "light")
