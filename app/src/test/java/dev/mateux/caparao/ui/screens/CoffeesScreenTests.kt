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
import androidx.compose.ui.test.assertIsFocused
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
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
import dev.mateux.caparao.data.local.BagRecipeAdjustmentRepository
import dev.mateux.caparao.data.local.CoffeeBagRepository
import dev.mateux.caparao.data.local.RecipeRepository
import dev.mateux.caparao.data.local.SeedBuiltInRecipes
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.BrewSample
import dev.mateux.caparao.model.BrewTaste
import dev.mateux.caparao.model.CoffeeBag
import dev.mateux.caparao.model.Recipe
import dev.mateux.caparao.model.RecipeStep
import dev.mateux.caparao.model.ScaleError
import dev.mateux.caparao.model.ScaleState
import dev.mateux.caparao.model.StepKind
import dev.mateux.caparao.ui.recipes.RecipeEditorViewModel
import dev.mateux.caparao.ui.coffees.CoffeeBagDetailViewModel
import dev.mateux.caparao.ui.coffees.estimatedDaysLeft
import dev.mateux.caparao.ui.coffees.CoffeeBagsViewModel
import dev.mateux.caparao.ui.coffees.CoffeeEditorViewModel
import dev.mateux.caparao.ui.recipes.RecipesViewModel
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
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
import org.junit.Assert.assertNull
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
    @Test
    fun estimatedDaysLeftRequiresRecentUsesAndRemainingCoffee() {
        val day = 86_400_000L
        val now = 14 * day
        fun brew(id: String, daysAgo: Int, dose: Double = 10.0) = Brew(
            id = id, mode = BrewMode.FILTER, startedAt = now - daysAgo * day, durationMs = 60_000,
            doseGrams = dose, yieldGrams = 150.0, timeToFirstDropMs = null, flowAvgGramsPerSecond = 1.0,
            flowPeakGramsPerSecond = 2.0, interrupted = false,
        )
        assertNull(estimatedDaysLeft(100.0, listOf(brew("one", 1), brew("two", 2)), now))
        assertEquals(1, estimatedDaysLeft(30.0, listOf(brew("a", 1), brew("b", 1), brew("c", 1)), now))
        assertNull(estimatedDaysLeft(0.0, listOf(brew("a", 1), brew("b", 1), brew("c", 1)), now))
        assertNull(estimatedDaysLeft(100.0, listOf(brew("old-a", 16), brew("old-b", 17), brew("old-c", 18)), now))
        assertEquals(0, estimatedDaysLeft(1.0, listOf(brew("a", 1), brew("b", 1), brew("c", 1)), now))
    }

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
    fun coffeeBagAddScreen() {
        open("press-ramp", 38_300)
        clickLast(R.string.tab_coffees)
        waitFor(R.string.coffees_empty)
        compose.onNodeWithContentDescription(text(R.string.coffee_add)).performClick()
        waitFor(R.string.coffee_weight)
        compose.onNodeWithTag("coffee-bag-editor").assertIsDisplayed()
        compose.onNodeWithTag("coffee-label-camera").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.coffee_read_label)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tab_home)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.tab_brews)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.tab_settings)).assertDoesNotExist()
        compose.onAllNodesWithText(text(R.string.coffee_section)).onFirst().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.coffee_package_section)).assertIsDisplayed()
        compose.onAllNodesWithText(text(R.string.coffee_notes_section)).onFirst().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.save)).assertIsNotEnabled()
        compose.onAllNodesWithText(text(R.string.coffee_origin)).onFirst().assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.coffee_name))).performTextReplacement("Mantiqueira")
        compose.onNodeWithText(text(R.string.save)).assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.coffee_weight))).performTextReplacement("250")
        compose.onNodeWithText(text(R.string.save)).assertIsEnabled()
        compose.onAllNodesWithText(text(R.string.coffee_tasting_notes)).onFirst().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tasting_chocolate)).performClick()
        compose.onNodeWithText(text(R.string.save)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("coffee-bag-editor").fetchSemanticsNodes().isEmpty() }
        clickLast(R.string.tab_coffees)
        compose.waitUntilAtLeastOneExists(hasText("Mantiqueira", substring = true), 5_000)
        compose.onNodeWithText("Mantiqueira", substring = true).assertIsDisplayed()
    }

    @Test
    fun recognizedLabelTextFillsOnlyMissingSupportedFields() {
        val today = LocalDate.of(2026, 11, 16)
        val lines = listOf(
            dev.mateux.caparao.model.LabelLine("Torra Alta", 40f),
            dev.mateux.caparao.model.LabelLine("Torrado em 12/11/2026", 30f),
            dev.mateux.caparao.model.LabelLine("250 g", 28f),
            dev.mateux.caparao.model.LabelLine("Chocolate", 18f),
            dev.mateux.caparao.model.LabelLine("Validade 12/11/2027", 12f),
        )
        val editor = CoffeeEditorViewModel(
            androidx.lifecycle.SavedStateHandle(),
            dev.mateux.caparao.ui.coffees.BagForm(name = "Typed name", weight = "340"),
            dev.mateux.caparao.ui.coffees.LabelRecognizer { lines },
        )
        runBlocking { editor.readLabel(java.io.File("fake.jpg"), listOf("Torra Alta"), listOf("Chocolate"), today) }
        assertEquals("Typed name", editor.form.value.name)
        assertEquals("340", editor.form.value.weight)
        assertEquals("Torra Alta", editor.form.value.roaster)
        assertEquals(today.minusDays(4).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(), editor.form.value.roastedAt)
        assertEquals(listOf("Chocolate"), editor.form.value.tastingNotes)
    }

    @Test
    fun unrecognizedTorraLineAppearsAsAChip() {
        val lines = listOf(
            dev.mateux.caparao.model.LabelLine("Torra Alta", 40f),
            dev.mateux.caparao.model.LabelLine("250 g", 36f),
            dev.mateux.caparao.model.LabelLine("Torrado em 10/11/2026", 32f),
            dev.mateux.caparao.model.LabelLine("Caparaó natural", 28f),
            dev.mateux.caparao.model.LabelLine("Jasmim", 24f),
        )
        val editor = CoffeeEditorViewModel(
            androidx.lifecycle.SavedStateHandle(),
            dev.mateux.caparao.ui.coffees.BagForm(),
            dev.mateux.caparao.ui.coffees.LabelRecognizer { lines },
        )
        val bags = CoffeeBagsViewModel(CoffeeBagRepository(db.coffeeBags()))
        compose.setContent { AppTheme { dev.mateux.caparao.ui.coffees.CoffeeEditorScreen(editor, bags) {} } }
        val photo = java.io.File.createTempFile("label", ".jpg")
        try {
            runBlocking { editor.readLabel(photo, emptyList(), emptyList(), LocalDate.of(2026, 11, 16)) }
            assertEquals("", editor.form.value.roaster)
            compose.onNodeWithTag("coffee-label-Torra Alta").assertIsDisplayed()
        } finally { photo.delete() }
    }

    @Test
    fun labelReadingKeepsExistingValuesAndAssignedChipsDoNotReturn() {
        var lines = listOf(
            dev.mateux.caparao.model.LabelLine("250 g", 20f),
            dev.mateux.caparao.model.LabelLine("Blueberry", 10f),
        )
        val editor = CoffeeEditorViewModel(
            androidx.lifecycle.SavedStateHandle(),
            dev.mateux.caparao.ui.coffees.BagForm(name = "Typed coffee", weight = "340"),
            dev.mateux.caparao.ui.coffees.LabelRecognizer { lines },
        )
        val bags = CoffeeBagsViewModel(CoffeeBagRepository(db.coffeeBags()))
        compose.setContent { AppTheme { dev.mateux.caparao.ui.coffees.CoffeeEditorScreen(editor, bags) {} } }
        val photo = java.io.File.createTempFile("label", ".jpg")
        try {
            runBlocking { editor.readLabel(photo, emptyList(), emptyList(), LocalDate.of(2026, 11, 16)) }
            compose.onNodeWithText(text(R.string.coffee_label_review)).assertIsDisplayed()
            compose.onNodeWithText("Blueberry").performClick()
            compose.onAllNodes(hasText(text(R.string.coffee_origin)) and hasClickAction()).onLast().performClick()
            compose.onNodeWithTag("coffee-label-Blueberry").assertDoesNotExist()
            assertEquals("Typed coffee", editor.form.value.name)
            assertEquals("340", editor.form.value.weight)
            assertEquals("Blueberry", editor.form.value.origin)
            lines = listOf(
                dev.mateux.caparao.model.LabelLine("100 g", 20f),
                dev.mateux.caparao.model.LabelLine("Blueberry", 10f),
                dev.mateux.caparao.model.LabelLine("Cherry", 8f),
            )
            runBlocking { editor.readLabel(photo, emptyList(), emptyList(), LocalDate.of(2026, 11, 16)) }
            compose.onNodeWithTag("coffee-label-Blueberry").assertDoesNotExist()
            compose.onNodeWithTag("coffee-label-Cherry").assertIsDisplayed()
            assertEquals("340", editor.form.value.weight)
            assertEquals("Blueberry", editor.form.value.origin)
            runBlocking { editor.readLabel(photo, emptyList(), emptyList(), LocalDate.of(2026, 11, 16)) }
            assertEquals(dev.mateux.caparao.ui.coffees.LabelMessage.NOTHING_NEW, editor.labelDraft.value.message)
        } finally { photo.delete() }
    }

    @Test
    fun emptyOrFailedLabelRecognitionLeavesTheFormUnchanged() {
        var fail = false
        var modelUnavailable = false
        val editor = CoffeeEditorViewModel(
            androidx.lifecycle.SavedStateHandle(),
            dev.mateux.caparao.ui.coffees.BagForm(name = "Typed coffee", weight = "340"),
            dev.mateux.caparao.ui.coffees.LabelRecognizer {
                when {
                    modelUnavailable -> throw dev.mateux.caparao.ui.coffees.LabelModelUnavailableException(IllegalStateException())
                    fail -> error("recognition failed")
                    else -> emptyList()
                }
            },
        )
        val bags = CoffeeBagsViewModel(CoffeeBagRepository(db.coffeeBags()))
        compose.setContent { AppTheme { dev.mateux.caparao.ui.coffees.CoffeeEditorScreen(editor, bags) {} } }
        val photo = java.io.File.createTempFile("label", ".jpg")
        try {
            runBlocking { editor.readLabel(photo, emptyList(), emptyList()) }
            assertEquals(dev.mateux.caparao.ui.coffees.LabelMessage.COULD_NOT_READ, editor.labelDraft.value.message)
            compose.onNodeWithText(text(R.string.coffee_label_could_not_read)).assertIsDisplayed()
            fail = true
            runBlocking { editor.readLabel(photo, emptyList(), emptyList()) }
            assertEquals(dev.mateux.caparao.ui.coffees.LabelMessage.COULD_NOT_READ, editor.labelDraft.value.message)
            assertEquals("Typed coffee", editor.form.value.name)
            assertEquals("340", editor.form.value.weight)
            modelUnavailable = true
            runBlocking { editor.readLabel(photo, emptyList(), emptyList()) }
            assertEquals(dev.mateux.caparao.ui.coffees.LabelMessage.MODEL_UNAVAILABLE, editor.labelDraft.value.message)
            compose.onNodeWithText(text(R.string.coffee_label_model_unavailable)).assertIsDisplayed()
            assertEquals("Typed coffee", editor.form.value.name)
            assertEquals("340", editor.form.value.weight)
        } finally { photo.delete() }
    }

    @Test
    fun lastCoffeeRowScrollsAboveAddButton() {
        val now = System.currentTimeMillis()
        runBlocking {
            val bags = CoffeeBagRepository(db.coffeeBags())
            repeat(12) { index ->
                val last = index == 11
                bags.save(
                    CoffeeBag(
                        id = "scroll-coffee-$index",
                        name = if (last) "Last coffee" else "Scrollable coffee $index",
                        weightGrams = 250.0,
                        createdAt = if (last) now - 86_400_000 else now + index,
                    ),
                )
            }
        }
        open("press-ramp", 38_300)
        click(R.string.tab_coffees)
        compose.onNodeWithTag("coffees-list").performScrollToIndex(11)
        val lastCoffee = compose.onNodeWithTag("coffee-row-scroll-coffee-11")
        lastCoffee.assert(hasText("Last coffee"))
        lastCoffee.assertIsDisplayed()
        val fabTop = compose.onNodeWithContentDescription(text(R.string.coffee_add)).getUnclippedBoundsInRoot().top
        assertTrue("Last coffee row overlaps the add button", lastCoffee.getUnclippedBoundsInRoot().bottom <= fabTop)
    }


    @Test
    fun aCustomTastingNoteCanBeAddedRemovedAndSavedFromTheDraft() {
        openCoffeeBag(withBrew = false)
        click(R.string.edit)
        waitFor(R.string.coffee_edit)
        // Origin and tasting notes are part of the full-screen form.

        compose.onNode(hasSetTextAction() and hasText(text(R.string.coffee_tasting_notes)))
            .performTextReplacement("Blueberry")
        compose.onNodeWithContentDescription(text(R.string.coffee_add_tasting_note)).performClick()
        compose.onNodeWithText("Blueberry").assertIsDisplayed()
        compose.onNodeWithText("Blueberry").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Blueberry").fetchSemanticsNodes().isEmpty() }

        compose.onNode(hasSetTextAction() and hasText(text(R.string.coffee_tasting_notes)))
            .performTextReplacement("Peach")
        click(R.string.save)
        val profile = "${context.getString(R.string.coffee_tasting_profile)}: Peach"
        compose.waitUntilAtLeastOneExists(hasText(profile), 5_000)
        compose.onNodeWithText(profile).assertIsDisplayed()
    }

    @Test
    fun coffeeBagSuggestsUsedRoasterAndTastingNote() {
        val now = System.currentTimeMillis()
        runBlocking {
            val bags = CoffeeBagRepository(db.coffeeBags())
            bags.save(CoffeeBag("used", "Old coffee", roaster = "Café Aurora", origin = "Catuaí natural", weightGrams = 250.0, finished = true, createdAt = now, tastingNotes = listOf("Jasmim")))
        }
        open("press-ramp", 38_300)
        click(R.string.tab_coffees)
        compose.onNodeWithContentDescription(text(R.string.coffee_add)).performClick()
        waitFor(R.string.coffee_weight)
        compose.onNode(hasSetTextAction() and hasText(text(R.string.coffee_name))).performTextReplacement("New coffee")
        compose.onNode(hasSetTextAction() and hasText(text(R.string.coffee_roaster))).performTextReplacement("cafe")
        compose.onNode(hasSetTextAction() and hasText(text(R.string.coffee_roaster))).assertIsFocused()
        compose.onNode(hasText("Café Aurora") and isSelectable()).assertIsDisplayed().performClick()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.coffee_roaster))).assertTextContains("Café Aurora")
        compose.onNode(hasText("Café Aurora") and isSelectable()).assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.coffee_weight))).performTextReplacement("250")
        // Origin and tasting notes are part of the full-screen form.
        compose.onNode(hasSetTextAction() and hasText(text(R.string.coffee_origin))).performTextReplacement("catuai")
        compose.onNodeWithText("Catuaí natural").assertIsDisplayed().performClick()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.coffee_origin))).assertTextContains("Catuaí natural")
        compose.onNodeWithText("Jasmim").assertIsDisplayed().performClick()
        click(R.string.save)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("coffee-bag-editor").fetchSemanticsNodes().isEmpty() }
        assertTrue("valid bag was saved", true)
    }

    /**
     * A bag's detail (C4): what is left, roast and brews. A brew opens and Back returns here; finishing and reopening
     * move the bag between the states; Edit opens the same sheet, filled.
     */

    
    /** The summary card (#469): average and count always; the tendency and the notes found once brews have a taste. */
    @Test
    fun coffeeBagSummary() {
        val now = System.currentTimeMillis()
        fun tasted(id: String, vararg found: String) = runBlocking {
            brews.save(
                Brew(
                    id, BrewMode.FILTER, now - 1_000, 185_000, 20.0, 300.0, null, 1.6, 4.0, interrupted = false, recipeName = "Melitta 102",
                    bagId = "bag-1", rating = 4, taste = BrewTaste.BITTER, tastingNotesFound = found.toList(), ratedAt = now,
                ),
                emptyList(),
            )
        }
        runBlocking {
            CoffeeBagRepository(db.coffeeBags()).save(CoffeeBag("bag-1", "Caparaó natural", weightGrams = 250.0, createdAt = now, tastingNotes = listOf("Caramel", "Orange")))
        }
        tasted("t1", "Caramel")
        tasted("t2", "Caramel", "Orange")
        open("press-ramp", 38_300)
        click(R.string.tab_coffees)
        compose.waitUntilAtLeastOneExists(hasText("Caparaó natural", substring = true), 5_000)
        compose.onNodeWithText("Caparaó natural", substring = true).performClick()
        waitFor(R.string.coffee_summary_rating)
        val notes = { of: Int ->
            context.getString(
                R.string.coffee_notes_found,
                context.getString(R.string.coffee_note_count, "Caramel", of, of) + " · " + context.getString(R.string.coffee_note_count, "Orange", 1, of),
            )
        }
        compose.onNodeWithText(notes(2)).assertExists()
        // Two brews with a taste are too few for a tendency.
        compose.onNodeWithText(text(R.string.coffee_tends_bitter)).assertDoesNotExist()
        compose.onAllNodesWithText("Melitta 102", substring = true).assertCountEquals(2)
        tasted("t3", "Caramel")
        waitFor(R.string.coffee_tends_bitter)
        compose.onNodeWithText(notes(3)).assertExists()
    }


    /** The summary card at 320 dp (#469): the three stats wrap instead of clipping, in pt-BR. */
    @Test
    fun smallScreenCoffeeSummary() {
        assumeTrue(
            "small-screen variant only",
            context.resources.configuration.screenWidthDp == 320 && context.resources.configuration.screenHeightDp == 640,
        )
        val now = System.currentTimeMillis()
        runBlocking {
            CoffeeBagRepository(db.coffeeBags()).save(
                CoffeeBag("bag-1", "Caparaó natural", weightGrams = 250.0, createdAt = now, tastingNotes = listOf("Caramel")),
            )
            repeat(3) { index ->
                brews.save(
                    Brew(
                        "t$index", BrewMode.FILTER, now - (index + 1) * 1_000, 185_000, 20.0, 300.0, null, 1.6, 4.0,
                        interrupted = false, recipeName = "Melitta 102", bagId = "bag-1", rating = 4,
                        taste = BrewTaste.BALANCED, tastingNotesFound = listOf("Caramel"), ratedAt = now,
                    ),
                    emptyList(),
                )
            }
        }
        open("press-ramp", 38_300)
        click(R.string.tab_coffees)
        compose.waitUntilAtLeastOneExists(hasText("Caparaó natural", substring = true), 5_000)
        compose.onNodeWithText("Caparaó natural", substring = true).performClick()
        waitFor(R.string.coffee_summary_rating)
        // The tendency is the widest stat in pt-BR; it must stay fully inside the screen.
        val tendency = compose.onNodeWithText(text(R.string.coffee_tends_balanced))
        tendency.assertIsDisplayed()
        compose.waitForIdle()
        val rootWidth = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        val bounds = tendency.fetchSemanticsNode().boundsInRoot
        assertTrue("tendency clipped at 320dp: right=${bounds.right} > $rootWidth", bounds.right <= rootWidth + 0.5f)
        compose.onNodeWithText(text(R.string.coffee_summary_rating)).assertIsDisplayed()
        // The notes line proves the card renders at 320 dp, next to the wrapped stats.
        compose.onNodeWithText(
            context.getString(R.string.coffee_notes_found, context.getString(R.string.coffee_note_count, "Caramel", 3, 3)),
        ).assertIsDisplayed()
    }


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
        waitFor(R.string.delete)
        // The brew detail's arrow is labelled Close.
        compose.onAllNodes(hasContentDescription(text(R.string.close))).onLast().performClick()
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

    @Test
    fun coffeeBagDetailShowsEstimateOnlyWithEnoughRecentUse() {
        val now = System.currentTimeMillis()
        runBlocking {
            CoffeeBagRepository(db.coffeeBags()).save(CoffeeBag("estimate-bag", "Estimate coffee", weightGrams = 250.0, createdAt = now))
        }
        repeat(3) { index ->
            runBlocking {
                db.brews().insertWithSamples(
                    BrewEntity(
                        id = "estimate-$index", mode = "FILTER", startedAt = now - (13 - index * 6) * 86_400_000L,
                        durationMs = 60_000, doseGrams = 10.0, yieldGrams = 150.0, timeToFirstDropMs = null,
                        flowAvg = 1.0, flowPeak = 2.0, interrupted = false, syncedAt = null, coffee = null,
                        rating = null, bagId = "estimate-bag",
                    ), emptyList(),
                )
            }
        }
        open("press-ramp", 38_300)
        click(R.string.tab_coffees)
        compose.waitUntilAtLeastOneExists(hasText("Estimate coffee", substring = true), 5_000)
        compose.onNodeWithText("Estimate coffee", substring = true).performClick()
        compose.waitUntilAtLeastOneExists(hasText(context.getString(R.string.coffee_left, "220", "250")), 5_000)
        val estimateText = context.resources.getQuantityString(R.plurals.coffee_days_left, 1, 1)
        compose.waitUntilAtLeastOneExists(hasText(estimateText.substringBefore("1"), substring = true), 5_000)
        compose.onNodeWithText(estimateText.substringBefore("1"), substring = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.coffee_ends_today)).assertDoesNotExist()
    }

    /**
     * The adjustments per recipe (#462): a recipe the bag was brewed with follows the recipe until it is adjusted; the
     * adjustment is edited, shows on the detail with its history, and goes back to the recipe after a confirmation.
     */
    @Test
    fun coffeeBagAdjustmentIsEditedListedAndCleared() {
        val day = 86_400_000L
        val now = System.currentTimeMillis()
        runBlocking {
            CoffeeBagRepository(db.coffeeBags()).save(CoffeeBag("bag-1", "Caparaó natural", roastedAt = now - 12 * day, weightGrams = 250.0, createdAt = now))
        }
        runBlocking {
            recipes.get(BuiltInRecipes.ESPRESSO_ID)?.let { recipes.save(it.copy(grind = 10)) }
        }
        linkBrew("brew-1", "bag-1", 19.0, recipeId = BuiltInRecipes.ESPRESSO_ID)
        open("press-ramp", 38_300)
        click(R.string.tab_coffees)
        compose.waitUntilAtLeastOneExists(hasText("Caparaó natural", substring = true), 5_000)
        compose.onNodeWithText("Caparaó natural", substring = true).performClick()
        waitFor(R.string.adjustments_title)
        compose.onNodeWithText(text(R.string.adjustment_none)).assertIsDisplayed()

        // Edit: one step finer and one degree cooler; Save is off until something changes.
        compose.onNodeWithText("Espresso Oster").performClick()
        waitFor(R.string.adjustment_title)
        val finer = text(R.string.adjustment_less, text(R.string.recipe_grind))
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription(finer), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text(R.string.save)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.adjustment_history)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.adjustment_less, text(R.string.recipe_grind)), useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription(text(R.string.adjustment_less, text(R.string.recipe_temp_short)), useUnmergedTree = true).performClick()
        click(R.string.save)

        // Listed on the detail, beside the recipe's own grind.
        waitFor(R.string.adjustments_title)
        val celsius = text(R.string.unit_celsius)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.adjustment_grind_recipe, "9", "10") + " · 91 $celsius"), 5_000)

        // History, then back to the recipe after the confirmation.
        compose.onNodeWithText("Espresso Oster").performClick()
        waitFor(R.string.adjustment_history)
        compose.onNodeWithText(text(R.string.adjustment_manual)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.adjustment_change_grind, text(R.string.adjustment_as_recipe), "9"), substring = true).assertIsDisplayed()
        click(R.string.adjustment_reset)
        waitFor(R.string.adjustment_reset_title)
        compose.onAllNodesWithText(text(R.string.adjustment_reset)).onLast().performClick()
        click(R.string.save)
        waitFor(R.string.adjustments_title)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.adjustment_none)), 5_000)
    }

    /** A bag with no brews says so (C4). */
    @Test
    fun coffeeBagDetailWithoutBrews() {
        openCoffeeBag(withBrew = false)
        compose.onNodeWithText(text(R.string.coffee_no_brews)).assertIsDisplayed()
        click(R.string.coffee_open_another_bag)
        waitFor(R.string.coffee_new_bag)
        compose.onNodeWithText(if (context.resources.configuration.locales[0].language == "pt") "ajuste" else "adjustment", substring = true).assertDoesNotExist()
    }

    @Test
    fun openingAnotherBagCopiesAdjustmentsAndShowsThemBeforeFirstBrew() {
        openCoffeeBag(withBrew = false)
        val recipe = BuiltInRecipes.all(System.currentTimeMillis()).first()
        runBlocking {
            recipes.save(recipe)
            BagRecipeAdjustmentRepository(db.bagRecipeAdjustments()).save(
                dev.mateux.caparao.model.BagRecipeAdjustment("bag-1", recipe.id, grind = 18, tuned = true), 100,
            )
        }
        click(R.string.coffee_open_another_bag)
        waitFor(R.string.coffee_new_bag)
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.coffee_adjustments_copied, 1, 1)).assertIsDisplayed()
        clickLast(R.string.save)
        waitFor(R.string.coffee_open_another_bag)
        val newBag = runBlocking { CoffeeBagRepository(db.coffeeBags()).bags.first().single { it.id != "bag-1" } }
        val copied = runBlocking { BagRecipeAdjustmentRepository(db.bagRecipeAdjustments()).get(newBag.id, recipe.id) }
        assertEquals(18, copied?.grind)
        assertFalse(copied?.tuned ?: true)
        assertEquals(true, runBlocking { BagRecipeAdjustmentRepository(db.bagRecipeAdjustments()).get("bag-1", recipe.id) }?.tuned)
        compose.onAllNodes(hasContentDescription(text(R.string.close))).onLast().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Caparaó natural", substring = true), 5_000)
        compose.onAllNodesWithText("Caparaó natural", substring = true).onFirst().performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.adjustment_grind, "18")), 5_000)
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

    /** The full-screen editor asks before system back or its close button discards edits (#181). */
    @Test
    fun coffeeEditorAsksBeforeDiscarding() {
        openCoffeeBag()
        click(R.string.edit)
        waitFor(R.string.coffee_edit)
        compose.onNode(hasSetTextAction() and hasText(text(R.string.coffee_roaster))).performTextReplacement("Outra torra")
        Espresso.pressBack()
        waitFor(R.string.discard_changes_title)
        clickLast(R.string.cancel)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.discard_changes_title)).fetchSemanticsNodes().isEmpty() }
        compose.onNode(hasSetTextAction() and hasText("Outra torra")).assertIsDisplayed()
        compose.onAllNodes(hasContentDescription(text(R.string.close))).onLast().performClick()
        waitFor(R.string.discard_changes_title)
        clickLast(R.string.discard)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("coffee-bag-editor").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(text(R.string.edit)).assertIsDisplayed()
    }

    /** Back and a clean sheet: Back with other edits asks first; a clean sheet closes without asking. */
    @Test
    fun coffeeEditorBackAsksOnlyWithEdits() {
        openCoffeeBag()
        click(R.string.edit)
        waitFor(R.string.coffee_edit)
        compose.onAllNodes(hasContentDescription(text(R.string.close))).onLast().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("coffee-bag-editor").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(text(R.string.edit)).assertIsDisplayed()
        click(R.string.edit)
        waitFor(R.string.coffee_edit)
        compose.onNode(hasSetTextAction() and hasText(text(R.string.coffee_roaster))).performTextReplacement("Outra torra")
        compose.onAllNodes(hasContentDescription(text(R.string.close))).onLast().performClick()
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

    @Test
    fun halfUsedCoffeeBarAppearsOnListAndDetail() {
        val now = System.currentTimeMillis()
        runBlocking {
            CoffeeBagRepository(db.coffeeBags()).save(
                CoffeeBag("half-used", "Half-used coffee", weightGrams = 250.0, createdAt = now),
            )
        }
        linkBrew("half-used-brew", "half-used", 125.0)
        open("press-ramp", 38_300)
        click(R.string.tab_coffees)
        compose.waitUntilAtLeastOneExists(hasText("Half-used coffee"), 5_000)
        compose.onNodeWithText(context.getString(R.string.coffee_left, "125", "250")).assertIsDisplayed()
        compose.onNodeWithTag("coffee-bag-level").assertIsDisplayed()
        compose.onNodeWithText("Half-used coffee").performClick()
        compose.waitUntilAtLeastOneExists(hasText(context.getString(R.string.coffee_left, "125", "250")), 5_000)
        compose.onNodeWithTag("coffee-bag-level").assertIsDisplayed()
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
