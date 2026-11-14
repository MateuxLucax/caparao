package dev.mateux.caparao.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.runtime.mutableStateOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mateux.caparao.R
import dev.mateux.caparao.data.local.CoffeeBagRepository
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.ui.brews.BrewRatingScreen
import dev.mateux.caparao.ui.theme.AppTheme
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.BrewTaste
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
abstract class BrewRatingScreenTests(lang: String, theme: String) : ScreenTestSupport(lang, theme) {
    @Test
    fun ratingReminderNotificationRouteOpensThatBrew() {
        runBlocking {
            brews.save(
                Brew(
                    id = "notification-route", mode = BrewMode.FILTER, startedAt = System.currentTimeMillis(),
                    durationMs = 60_000, doseGrams = 18.0, yieldGrams = 250.0, timeToFirstDropMs = null,
                    flowAvgGramsPerSecond = 1.0, flowPeakGramsPerSecond = 2.0, interrupted = false,
                ),
                emptyList(),
            )
        }
        launchRatingBrewId = "notification-route"
        open("idle-5min", 0)

        waitFor(R.string.brew_how_was_it)
        assertTrue(ratingRequestConsumed)
    }

    @Test
    fun deniedNotificationsPermissionTurnsReminderOffWithoutAskingAgain() {
        settings.remindToRate = true
        settings.ratingNotificationsPermissionAsked = false
        notificationsGranted = false
        notificationPermissionRequestResult = false
        espressoSummary()

        // The rationale comes first; the system prompt fires only when the user allows it.
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.rating_reminder_allow_notifications)), 5_000)
        compose.onNodeWithText(text(R.string.rating_reminder_allow_notifications)).performClick()
        compose.waitUntil(5_000) { notificationPermissionRequests == 1 }
        assertFalse(settings.remindToRate)
        assertTrue(settings.ratingNotificationsPermissionAsked)
        compose.onNodeWithTag("remind-to-rate").performClick()
        compose.waitForIdle()
        assertEquals(1, notificationPermissionRequests)
        assertFalse(settings.remindToRate)
    }

    @Test
    fun reminderPermissionRationaleShowsInTheLocaleBeforeTheSystemPrompt() {
        settings.remindToRate = true
        notificationsGranted = false
        espressoSummary()

        compose.waitUntilAtLeastOneExists(hasText(text(R.string.rating_reminder_permission_title)), 5_000)
        compose.onNodeWithText(text(R.string.rating_reminder_permission_text)).assertIsDisplayed()
        assertEquals(0, notificationPermissionRequests)
        compose.onNodeWithText(text(R.string.rating_reminder_not_now)).performClick()
        compose.waitForIdle()
        assertEquals(0, notificationPermissionRequests)
        assertFalse(settings.remindToRate)
        assertTrue(settings.ratingNotificationsPermissionAsked)
    }

    @Test
    fun allowingNotificationsKeepsTheReminderOn() {
        settings.remindToRate = true
        notificationsGranted = false
        notificationPermissionRequestResult = true
        espressoSummary()

        compose.waitUntilAtLeastOneExists(hasText(text(R.string.rating_reminder_allow_notifications)), 5_000)
        compose.onNodeWithText(text(R.string.rating_reminder_allow_notifications)).performClick()
        compose.waitUntil(5_000) { notificationPermissionRequests == 1 }
        assertTrue(settings.remindToRate)
        assertTrue(settings.ratingNotificationsPermissionAsked)
    }

    @Test
    fun turningTheReminderOnShowsTheRationaleFirst() {
        settings.remindToRate = false
        notificationsGranted = false
        notificationPermissionRequestResult = true
        espressoSummary()

        compose.onNodeWithTag("remind-to-rate").performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.rating_reminder_allow_notifications)), 5_000)
        assertEquals(0, notificationPermissionRequests)
        compose.onNodeWithText(text(R.string.rating_reminder_allow_notifications)).performClick()
        compose.waitUntil(5_000) { notificationPermissionRequests == 1 }
        assertTrue(settings.remindToRate)
    }

    @Test
    fun ratingReminderDelayIsASettingsRowWithFiveToFifteenMinuteChoices() {
        open("idle-5min", 0)
        compose.onNodeWithText(text(R.string.tab_settings)).performClick()
        compose.onNodeWithTag("rating-reminder-delay").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.rating_reminder_delay_value, 10)).assertIsDisplayed()
        compose.onNodeWithTag("rating-reminder-delay").performClick()
        compose.onNodeWithText(text(R.string.rating_reminder_delay_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.rating_reminder_delay_value, 5)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.rating_reminder_delay_value, 15)).assertIsDisplayed()
    }

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
    fun brewDetailCanRateThenEditGrindWithoutLosingTheRating() {
        seedCoffeeBags()
        linkBrew("detail-rate", "bag-1", 18.0)
        openBrewDetail()

        compose.onNodeWithText(text(R.string.brew_rate)).performClick()
        waitFor(R.string.brew_how_was_it)
        waitFor(R.string.brew_taste)
        compose.onNodeWithText(text(R.string.brew_taste_balanced)).performScrollTo().performClick()
        compose.onNodeWithText("Chocolate").performScrollTo().performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.rating_stars, 4)).performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.brew_notes)))
            .performScrollTo()
            .performTextReplacement("Peach and honey")
        compose.onNodeWithText(text(R.string.brew_save_rating)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.brew_how_was_it)).fetchSemanticsNodes().isEmpty() }

        compose.onNodeWithText(text(R.string.edit_notes)).performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.brew_coffee))).assertExists()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.recipe_grind))).assertExists()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.recipe_temp))).assertExists()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.recipe_grind)))
            .performScrollTo()
            .performTextReplacement("Fine")
        compose.onNodeWithText(text(R.string.save)).performScrollTo().performClick()

        val saved = runBlocking { brews.get("detail-rate")!! }
        assertEquals("Fine", saved.grind)
        assertEquals(BrewTaste.BALANCED, saved.taste)
        assertEquals(listOf("Chocolate"), saved.tastingNotesFound)
        assertEquals(4, saved.rating)
        assertEquals("Peach and honey", saved.notes)
        tapEditorClose()
    }

    @Test
    fun bagWithoutTastingNotesHidesThatSection() {
        seedCoffeeBags()
        linkBrew("no-bag-notes", "bag-2", 18.0)
        openBrewDetail()
        compose.onNodeWithText(text(R.string.brew_rate)).performClick()
        waitFor(R.string.brew_how_was_it)
        waitFor(R.string.brew_taste)

        compose.onNodeWithText(text(R.string.brew_tasting_notes_found)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.brew_tasting_notes_hint)).assertDoesNotExist()
        tapBack()
    }

    @Test
    fun reopeningARatedBrewShowsItsSavedValues() {
        seedCoffeeBags()
        linkBrew("rated-brew", "bag-1", 18.0, rating = 4)
        runBlocking {
            val saved = brews.get("rated-brew")!!
            brews.saveRating(
                saved.copy(taste = BrewTaste.BALANCED, tastingNotesFound = listOf("Chocolate"), notes = "Peach and honey"),
                ratedAt = saved.startedAt + 1,
            )
        }
        openBrewDetail()
        compose.onNodeWithText(text(R.string.brew_rate)).performClick()
        waitFor(R.string.brew_how_was_it)
        waitFor(R.string.brew_taste)

        compose.onNode(hasText("Chocolate") and isSelected()).assertExists()
        compose.onNode(hasSetTextAction() and hasText("Peach and honey")).assertIsDisplayed()
        tapBack()
    }

    @Test
    fun missingBrewClosesTheRatingScreen() {
        val closed = mutableStateOf(false)
        val viewModel = dev.mateux.caparao.ui.brews.BrewRatingViewModel(
            "missing",
            brews,
            CoffeeBagRepository(db.coffeeBags()),
        )
        compose.setContent { AppTheme { BrewRatingScreen(viewModel) { closed.value = true } } }

        compose.waitUntil(30_000) { closed.value }
        assertEquals(true, closed.value)
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
        compose.onNode(hasSetTextAction() and hasText(text(R.string.brew_notes))).assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.brew_coffee))).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.brew_rate_now)).assertIsDisplayed()
    }

    private fun openRatingAndSave() {
        compose.onNodeWithText(text(R.string.brew_rate_now)).performScrollTo().performClick()
        waitFor(R.string.brew_how_was_it)
        // The header names the brew in one line: coffee, recipe and when.
        compose.waitUntilAtLeastOneExists(hasText("Caparaó natural · ", substring = true), 5_000)
        compose.onNodeWithText("Caparaó natural · ", substring = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.brew_taste_sour)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.brew_taste_balanced)).performScrollTo().performClick()
        compose.onNodeWithText("Chocolate").performScrollTo().performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.rating_stars, 4)).performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.brew_notes)))
            .performScrollTo()
            .performTextReplacement("Peach and honey")
        // Save sits under the form, always in reach.
        compose.onNodeWithText(text(R.string.brew_save_rating)).performClick()
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

    private fun openBrewDetail() {
        open("idle-5min", 0)
        click(R.string.tab_brews)
        compose.waitUntilAtLeastOneExists(hasText(" ${text(R.string.unit_grams)} →", substring = true), 5_000)
        compose.onNode(hasText(" ${text(R.string.unit_grams)} →", substring = true)).performClick()
        waitFor(R.string.brew_rate)
    }
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class BrewRatingEnLightTest : BrewRatingScreenTests("en", "light")

@Config(qualifiers = "pt-rBR-w411dp-h891dp-notnight-xxhdpi")
class BrewRatingPtBrLightTest : BrewRatingScreenTests("pt-BR", "light")
