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

/**
 * The app's screens on the JVM (Robolectric) instead of an emulator: onboarding, every tab, espresso and filter
 * brews, the guided flows and both home-screen widget sizes. The recorded sessions are played frame by frame
 * ([SessionScale]), so every flow is deterministic.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenTests(private val lang: String, private val theme: String, private val compactOnly: Boolean = false) {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val testName = TestName()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val actions = AppActions(requestBluetoothPermissions = { true }, bluetoothPermissionGranted = { true }, enableBluetooth = {}, openAppSettings = {}, requestLocalNetwork = { true })
    private val db = Room.inMemoryDatabaseBuilder(context, BrewDatabase::class.java).addCallback(SeedBuiltInRecipes).allowMainThreadQueries().build()
    private val brews = BrewRepository(db.brews())
    private val recipes = RecipeRepository(db.recipes())
    private val settings = SettingsStore(context.getSharedPreferences("screen-tests", Context.MODE_PRIVATE))
    private val diagnosticScope = MainScope()
    private val diagnostics = DiagnosticRecorder(
        context.cacheDir.resolve("diagnostic-screen-tests/$lang-$theme-${System.nanoTime()}"),
        diagnosticScope,
    )
    private val locale get() = context.resources.configuration.locales[0]

    @Before
    fun setUp() {
        if (compactOnly) assumeTrue("compact screen variant only", testName.methodName in setOf("smallScreenWeigh", "smallScreenHome", "smallScreenPortafilter", "smallScreenFilterPrepare"))
        // The animations' still frames (the full cup, the guide's pictures), as on an emulator with animations off.
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        // Brew dates and times read the same wherever this runs.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        runBlocking { diagnostics.stop() }
        diagnosticScope.cancel()
        db.close()
    }

    private fun text(id: Int) = context.getString(id)

    /** The rendered text of a TextView [id] in an applied widget's view tree. */
    private fun widgetText(root: View, id: Int): String {
        val view = root.findViewById<TextView>(id)
        assertNotNull("missing TextView $id in the applied widget view tree", view)
        return view!!.text.toString()
    }

    /** Asserts the rendered text of a TextView [id], so a blank or missing widget fails. */
    private fun assertWidgetText(root: View, id: Int, expected: String) {
        assertEquals(expected, widgetText(root, id))
    }

    /** Asserts a view [id]'s visibility in an applied widget's view tree. */
    private fun assertWidgetVisibility(root: View, id: Int, visibility: Int) {
        val view = root.findViewById<View>(id)
        assertNotNull("missing view $id in the applied widget view tree", view)
        assertEquals(visibility, view!!.visibility)
    }

    private fun waitFor(id: Int) = compose.waitUntilAtLeastOneExists(hasText(text(id)), 5_000)

    private fun click(id: Int) = compose.onNodeWithText(text(id)).performClick()

    /** A node whose whole text matches [pattern]: the readout's weight (a decimal) and timer (mm:ss). */
    private fun textMatching(pattern: Regex) =
        SemanticsMatcher("text matching $pattern") { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { pattern.matches(it.text) } == true }

    /** The live readout's weight and timer, which stay on screen through the shot. */
    private fun assertWeightAndTimerShown() {
        compose.onNode(textMatching(Regex("\\d+[.,]\\d"))).assertIsDisplayed()
        compose.onNode(textMatching(Regex("\\d\\d:\\d\\d"))).assertIsDisplayed()
    }

    private fun open(
        session: String,
        atMs: Long,
        onboardingDone: Boolean = true,
        brand: () -> BrandColor? = { BrandColor.LEAF },
    ): SessionScale {
        val scale = SessionScale(context.assets.open("sessions/$session.jsonl").bufferedReader().use { it.readLines() })
        val factory = viewModelFactory {
            initializer { HomeViewModel(scale) }
            initializer { OnboardingViewModel(scale) {} }
            initializer { ScaleStatusViewModel(scale) }
            initializer { TargetRatioViewModel(settings) }
            initializer { ExportViewModel(BrewExportFiles(context, brews, recipes)) }
            initializer { DiagnosticsViewModel(context, diagnostics) }
            initializer { RecipesViewModel(recipes) }
            initializer { CoffeeBagsViewModel(CoffeeBagRepository(db.coffeeBags())) }
            initializer { CoffeeBagDetailViewModel(requireNotNull(this[CoffeeBagDetailViewModel.BAG_ID]), CoffeeBagRepository(db.coffeeBags()), brews) }
            initializer { RecipeEditorViewModel(requireNotNull(this[RecipeEditorViewModel.TARGET]), recipes) }
            initializer { BrewsViewModel(brews, settings.syncStatus) {} }
            initializer { ServerViewModel(settings, { _, _ -> error("no server in screen tests") }, {}, {}) }
            initializer {
                BrewViewModel(
                    requireNotNull(this[BrewViewModel.MODE]), scale, brews, settings, recipes,
                    clock = { scale.nowMs }, wallClock = { BREWED_AT + scale.nowMs }, coffeeBags = CoffeeBagRepository(db.coffeeBags()),
                )
            }
            initializer { BrewDetailViewModel(requireNotNull(this[BrewDetailViewModel.BREW_ID]), brews) }
            initializer { CompareViewModel(requireNotNull(this[CompareViewModel.BREW_IDS]), brews) }
            initializer { TrendsViewModel(requireNotNull(this[TrendsViewModel.KEY]), brews) }
        }
        compose.setContent { AppTheme(brand()) { App(factory, scale, onboardingDone, actions, BuildConfig.VERSION_NAME) } }
        play(scale, atMs)
        return scale
    }

    /** Plays [scale] up to [atMs], letting the brew detector see each frame. */
    private fun play(scale: SessionScale, atMs: Long) = scale.playUntil(atMs) { shadowOf(Looper.getMainLooper()).idle() }

    /** Plays [scale] in 100 ms steps until [id]'s text shows, at most until [untilMs]. */
    private fun playUntilShown(scale: SessionScale, id: Int, untilMs: Long) {
        while (compose.onAllNodes(hasText(text(id))).fetchSemanticsNodes().isEmpty()) {
            check(scale.nowMs < untilMs) { "\"${text(id)}\" never showed" }
            play(scale, scale.nowMs + 100)
            compose.waitForIdle()
        }
    }

    private fun openDiagnosticsCard() {
        open("idle-5min", 0)
        click(R.string.tab_settings)
        compose.onNodeWithText(text(R.string.section_diagnostics)).performScrollTo()
        compose.waitForIdle()
    }

    private fun diagnosticsAction(id: Int) {
        compose.onNodeWithText(text(id)).performScrollTo().performClick()
    }

    @Test
    fun diagnosticsIdle() {
        openDiagnosticsCard()
        compose.onNodeWithText(text(R.string.diagnostics_start)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun diagnosticsRecording() {
        openDiagnosticsCard()
        diagnosticsAction(R.string.diagnostics_start)
        waitFor(R.string.diagnostics_recording)
        compose.onNodeWithText(text(R.string.diagnostics_stop)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun diagnosticsReadyToShare() {
        openDiagnosticsCard()
        diagnosticsAction(R.string.diagnostics_start)
        waitFor(R.string.diagnostics_recording)
        diagnosticsAction(R.string.diagnostics_stop)
        waitFor(R.string.diagnostics_ready)
        compose.onNodeWithText(text(R.string.diagnostics_share)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun diagnosticsIncomplete() {
        openDiagnosticsCard()
        diagnosticsAction(R.string.diagnostics_start)
        waitFor(R.string.diagnostics_recording)
        diagnostics.recordDisconnect()
        waitFor(R.string.diagnostics_incomplete)
        compose.onNodeWithText(text(R.string.diagnostics_share)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun onboardingAndEveryTab() {
        open("press-ramp", 38_300, onboardingDone = false)
        waitFor(R.string.welcome_title)
        compose.waitForIdle()
        click(R.string.get_started)
        compose.waitForIdle()
        click(R.string.skip)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.scale_connected)), 5_000)
        compose.waitForIdle()
        click(R.string.next)
        compose.waitForIdle()
        click(R.string.skip)
        Tab.entries.forEach { tab ->
            click(tab.label)
            compose.waitForIdle()
        }
        // Each tab must show its distinctive content.
        click(R.string.tab_home)
        compose.waitUntilAtLeastOneExists(hasContentDescription(text(R.string.tare)), 5_000)
        compose.onNodeWithContentDescription(text(R.string.tare)).assertIsDisplayed()
        click(R.string.tab_recipes)
        compose.waitUntilAtLeastOneExists(hasText("V60"), 5_000)
        compose.onAllNodesWithText("V60").onFirst().assertIsDisplayed()
        click(R.string.tab_brews)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.brews_empty_title)), 5_000)
        compose.onNodeWithText(text(R.string.brews_empty_title)).assertIsDisplayed()
        click(R.string.tab_settings)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.section_diagnostics)), 5_000)
        compose.onNodeWithText(text(R.string.section_diagnostics)).performScrollTo().assertIsDisplayed()
        // The built-in V60 recipe in the editor: its name, then its method on the card.
        click(R.string.tab_recipes)
        compose.onAllNodesWithText("V60").onFirst().performClick()
        // The recipe loads from the database off the main thread; wait until the form is there.
        waitFor(R.string.recipe_name)
        compose.waitForIdle()
    }

    /** Home with the Manual scale action selected and the existing controls visible. */
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
    private fun openV60Editor() {
        open("press-ramp", 38_300)
        click(R.string.tab_recipes)
        compose.waitUntilAtLeastOneExists(hasText("V60 One and Done"), 5_000)
        compose.onNodeWithText("V60 One and Done").performClick()
        waitFor(R.string.recipe_name)
    }

    /** Back in an untouched recipe editor closes at once (#181, B1). */
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
    private fun seedCoffeeBags() {
        val day = 86_400_000L
        val now = System.currentTimeMillis()
        runBlocking {
            CoffeeBagRepository(db.coffeeBags()).save(CoffeeBag("bag-1", "Caparaó natural", roastedAt = now - 12 * day, weightGrams = 250.0, createdAt = now))
            CoffeeBagRepository(db.coffeeBags()).save(CoffeeBag("bag-2", "Mantiqueira", weightGrams = 10.0, createdAt = now - day))
        }
    }

    private fun coffeeStepBeforeTheBeans(session: String, pickMode: () -> Unit) {
        seedCoffeeBags()
        open(session, 0)
        pickMode()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.milestone_coffee)), 5_000)
        click(R.string.next)
        waitFor(R.string.coffee_step_title)
        compose.onNode(hasText("Caparaó natural") and isSelected()).assertExists()
        compose.onNode(hasText("Mantiqueira") and isNotSelected()).assertExists()
        compose.onNodeWithText(text(R.string.coffee_below_dose)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.no_bag)).performClick()
        compose.onNode(hasText(text(R.string.no_bag)) and isSelected()).assertExists()
        compose.onNodeWithText("Mantiqueira").performClick()
        compose.onNode(hasText("Mantiqueira") and isSelected()).assertExists()
        compose.onNode(hasTestTag("coffee-step-next")).performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
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
    private fun openCoffeeBag(withBrew: Boolean = true) {
        val day = 86_400_000L
        val now = System.currentTimeMillis()
        runBlocking {
            CoffeeBagRepository(db.coffeeBags()).save(CoffeeBag("bag-1", "Caparaó natural", roaster = "Torra Alta", roastedAt = now - 12 * day, weightGrams = 250.0, createdAt = now))
        }
        if (withBrew) linkBrew("brew-1", "bag-1", 18.0, rating = 4)
        open("press-ramp", 38_300)
        click(R.string.tab_coffees)
        compose.waitUntilAtLeastOneExists(hasText("Caparaó natural", substring = true), 5_000)
        compose.onNodeWithText("Caparaó natural", substring = true).performClick()
        val left = if (withBrew) "232" else "250"
        compose.waitUntilAtLeastOneExists(hasText(context.getString(R.string.coffee_left, left, "250")), 5_000)
    }

    /** The last node with [id]: a dialog's button sits above the screen's, which stays in the tree. */
    private fun clickLast(id: Int) = compose.onAllNodesWithText(text(id)).onLast().performClick()

    /** The Coffees tab's add sheet (C3): Save waits for a name and a weight; More details holds origin and notes. */
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
    private fun linkBrew(id: String, bagId: String, dose: Double, rating: Int? = null) = runBlocking {
        db.brews().insertWithSamples(
            BrewEntity(
                id = id, mode = "FILTER", startedAt = System.currentTimeMillis(), durationMs = 60_000, doseGrams = dose,
                yieldGrams = 250.0, timeToFirstDropMs = null, flowAvg = 1.0, flowPeak = 2.0, interrupted = false,
                syncedAt = null, coffee = null, rating = rating, bagId = bagId,
            ),
            emptyList(),
        )
    }

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
    fun widget() {
        var latest by mutableStateOf<Brew?>(
            Brew("screen-test-widget", BrewMode.ESPRESSO, 0L, 28_000L, 18.0, 36.0, 4_000L, 1.3, 2.4, false),
        )
        // A neutral home screen behind the widget, so its rounded shape reads in both themes.
        val wallpaper = if (theme == "dark") Color(0xFF2B3038) else Color(0xFFDDE4ED)
        var rendered: View? = null
        compose.setContent {
            Box(Modifier.background(wallpaper)) {
                key(latest) {
                    AndroidView(
                        // The RemoteViews the provider builds, applied to a real view as on a home screen.
                        factory = { BrewWidgetProvider().views(context, 1, latest).apply(context, null).also { rendered = it } },
                        modifier = Modifier.size(180.dp, 176.dp).testTag("widget"),
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("widget").assertExists()

        // The saved state shows the last brew: title, method label, and the suggested action.
        val saved = rendered
        assertNotNull("the applied widget view was not created", saved)
        assertWidgetText(saved!!, R.id.widget_title, text(R.string.widget_last_brew_title))
        assertWidgetVisibility(saved, R.id.widget_last_brew, View.VISIBLE)
        assertWidgetVisibility(saved, R.id.widget_empty_content, View.GONE)
        assertWidgetText(saved, R.id.widget_method, text(R.string.mode_espresso))
        assertWidgetText(saved, R.id.widget_action_label, text(R.string.widget_brew_filter))

        latest = null
        compose.waitForIdle()
        compose.onNodeWithTag("widget").assertExists()

        // The empty state shows the empty title and the coffee action.
        val empty = rendered
        assertNotNull("the applied widget view was not created", empty)
        assertWidgetVisibility(empty!!, R.id.widget_title, View.GONE)
        assertWidgetVisibility(empty, R.id.widget_last_brew, View.GONE)
        assertWidgetVisibility(empty, R.id.widget_empty_content, View.VISIBLE)
        assertWidgetText(empty, R.id.widget_empty_title, text(R.string.widget_empty_title))
        assertWidgetText(empty, R.id.widget_action_label, text(R.string.widget_brew_coffee))
    }

    /** The expanded widget's estimates and neutral empty-week state, rendered at its 260×210 dp breakpoint. */
    @Test
    fun weeklyWidget() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 10, 18)
        val window = brewWidgetWeekWindow(today, zone)
        val days = window.days
        val atNoon = { date: LocalDate -> date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli() }
        fun savedBrew(
            id: String,
            dayIndex: Int,
            mode: BrewMode,
            dose: Double,
            yield: Double,
            interrupted: Boolean = false,
        ) = Brew(
            id = id,
            mode = mode,
            startedAt = atNoon(days[dayIndex]) - 28_000,
            durationMs = 28_000,
            doseGrams = dose,
            yieldGrams = yield,
            timeToFirstDropMs = if (mode == BrewMode.ESPRESSO) 4_000 else null,
            flowAvgGramsPerSecond = 1.3,
            flowPeakGramsPerSecond = 2.4,
            interrupted = interrupted,
        )

        val saved = listOf(
            savedBrew("filter-1", 1, BrewMode.FILTER, 15.0, 250.0),
            savedBrew("espresso-1", 2, BrewMode.ESPRESSO, 18.0, 36.0),
            savedBrew("espresso-2", 2, BrewMode.ESPRESSO, 18.0, 40.0),
            savedBrew("filter-2", 4, BrewMode.FILTER, 15.0, 250.0),
            savedBrew("filter-3", 4, BrewMode.FILTER, 15.0, 250.0),
            savedBrew("filter-4", 5, BrewMode.FILTER, 15.0, 250.0),
            savedBrew("filter-5", 5, BrewMode.FILTER, 15.0, 250.0),
            savedBrew("espresso-3", 5, BrewMode.ESPRESSO, 18.0, 36.0),
            savedBrew("interrupted-filter", 6, BrewMode.FILTER, 15.0, 100.0, interrupted = true),
        )
        var week by mutableStateOf(summarizeBrewWidgetWeek(saved, window, zone))
        val emptyWeek = summarizeBrewWidgetWeek(emptyList(), window, zone)
        val wallpaper = if (theme == "dark") Color(0xFF2B3038) else Color(0xFFDDE4ED)
        val widthDp = context.resources.getDimension(R.dimen.widget_weekly_min_width) / context.resources.displayMetrics.density
        val heightDp = context.resources.getDimension(R.dimen.widget_weekly_min_height) / context.resources.displayMetrics.density
        var rendered: View? = null
        compose.setContent {
            Box(Modifier.background(wallpaper)) {
                key(week) {
                    AndroidView(
                        // The RemoteViews the provider builds, applied to a real view as on a home screen.
                        factory = { BrewWidgetProvider().weeklyViews(context, 1, BrewMode.FILTER, week).apply(context, null).also { rendered = it } },
                        modifier = Modifier.size(widthDp.dp, heightDp.dp).testTag("weekly-widget"),
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("weekly-widget").assertExists()

        // The rendered week: title, brew count, and the estimate values as shown (not just the format strings).
        val savedView = rendered
        assertNotNull("the applied widget view was not created", savedView)
        assertWidgetText(savedView!!, R.id.widget_weekly_title, text(R.string.widget_weekly_title))
        assertWidgetVisibility(savedView, R.id.widget_weekly_content, View.VISIBLE)
        assertWidgetVisibility(savedView, R.id.widget_weekly_empty, View.GONE)
        assertWidgetText(
            savedView,
            R.id.widget_weekly_brews_value,
            io.github.mateuxlucax.caparao.ui.widget.BrewWidgetFormat.count(context, week.brewCount),
        )
        assertWidgetText(
            savedView,
            R.id.widget_weekly_beverage_value,
            io.github.mateuxlucax.caparao.ui.widget.BrewWidgetFormat.beverageVolume(context, week.estimatedBeverageMl),
        )
        assertWidgetText(
            savedView,
            R.id.widget_weekly_dose_value,
            io.github.mateuxlucax.caparao.ui.widget.BrewWidgetFormat.dose(context, week.doseGrams),
        )
        assertWidgetText(
            savedView,
            R.id.widget_weekly_caffeine_value,
            io.github.mateuxlucax.caparao.ui.widget.BrewWidgetFormat.caffeine(context, week.estimatedCaffeineMg),
        )
        // The rendered estimates: beverage and caffeine carry ≈, dose is recorded and has none.
        val beverage = widgetText(savedView, R.id.widget_weekly_beverage_value)
        val caffeine = widgetText(savedView, R.id.widget_weekly_caffeine_value)
        val dose = widgetText(savedView, R.id.widget_weekly_dose_value)
        assertTrue(beverage.startsWith("≈ "))
        assertTrue(caffeine.startsWith("≈ "))
        assertFalse(dose.startsWith("≈"))

        week = emptyWeek
        compose.waitForIdle()
        compose.onNodeWithTag("weekly-widget").assertExists()

        // The rendered empty week: the neutral message, no stats row.
        val emptyView = rendered
        assertNotNull("the applied widget view was not created", emptyView)
        assertWidgetText(emptyView!!, R.id.widget_weekly_empty, text(R.string.widget_weekly_empty))
        assertWidgetVisibility(emptyView, R.id.widget_weekly_empty, View.VISIBLE)
        assertWidgetVisibility(emptyView, R.id.widget_weekly_content, View.GONE)
    }

    /** The Coffees tab, with an open bag and a finished one (#110). */
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
    @Test
    fun brandColors() {
        var brand by mutableStateOf<BrandColor?>(null)
        open("press-ramp", 38_300, brand = { brand })
        BrandColor.entries.forEach {
            brand = it
            compose.waitForIdle()
            compose.onNodeWithContentDescription(text(R.string.tare)).assertIsDisplayed()
        }
        // The Colors section in Settings, switching from the system's colors to Caparaó's.
        brand = null
        click(R.string.tab_settings)
        // "Caparaó" is also the app's title, so the option is the clickable node with that text.
        compose.onNodeWithText(text(R.string.colors_system)).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText(text(R.string.colors_brand)) and hasClickAction()).performClick()
        brand = BrandColor.LEAF
        compose.waitForIdle()
        compose.onAllNodesWithText(text(R.string.colors_brand)).onFirst().assertIsDisplayed()
    }

    /** An espresso from the synthetic session (cup already on the scale), then the list and the brew's chart. */
    @Test
    fun espresso() {
        val scale = open("synthetic-espresso", 0)
        click(R.string.mode_espresso)
        compose.waitForIdle()
        click(R.string.next)
        compose.waitForIdle()
        // Weigh the beans: the scale shows nothing yet, so Next keeps the planned dose.
        click(R.string.next)
        click(R.string.prepare_ready)
        // The shot starts 14 s into the session and pours for about 25 s.
        play(scale, 21_000)
        compose.waitForIdle()
        playUntilShown(scale, R.string.brew_saved, 60_000)
        compose.waitForIdle()
        // By action, not touch: the summary's layout can still shift as the notes load off the main thread.
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_brews)), 5_000)
        compose.onNodeWithText(text(R.string.tab_brews)).performSemanticsAction(SemanticsActions.OnClick)
        // The list loads the brew from the database off the main thread, so wait for its row before taking it.
        compose.waitUntilAtLeastOneExists(hasText(" ${text(R.string.unit_grams)} →", substring = true), 5_000)
        // The dose is what the scale showed on the weigh step, so take the only brew in the list.
        compose.onAllNodes(hasText(" ${text(R.string.unit_grams)} →", substring = true))[0].performSemanticsAction(SemanticsActions.OnClick)
        // The brew loads from the database off the main thread.
        waitFor(R.string.delete)
        compose.waitForIdle()
    }

    /** The espresso from the synthetic session, stopped on its summary: the notes and the Done button (#181). */
    private fun espressoSummary() {
        val scale = open("synthetic-espresso", 0)
        click(R.string.mode_espresso)
        compose.waitForIdle()
        click(R.string.next)
        compose.waitForIdle()
        click(R.string.next)
        click(R.string.prepare_ready)
        play(scale, 21_000)
        compose.waitForIdle()
        playUntilShown(scale, R.string.brew_saved, 60_000)
        compose.waitForIdle()
    }

    /** The top bar's arrow, which runs the same action as Back. */
    private fun tapBack() = compose.onNodeWithContentDescription(text(R.string.back)).performClick()

    /** The recipe editor's arrow, which is still labelled Close. */
    private fun tapEditorClose() = compose.onNodeWithContentDescription(text(R.string.close)).performClick()

    /** The top bar's X, which closes the whole brew from the dose, beans and preparation steps (#185). */
    private fun tapCloseBrew() = compose.onNodeWithContentDescription(text(R.string.close_brew)).performClick()

    /** Back on the summary saves the typed notes into the saved brew and closes, with no dialog (#181, A1 → A2). */
    @Test
    fun summaryBackSavesNotesAndCloses() {
        espressoSummary()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.brew_coffee))).performTextReplacement("Kalita")
        compose.waitForIdle()
        tapBack()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_brews)), 5_000)
        compose.onNodeWithText(text(R.string.discard_brew_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.brew_saved)).assertDoesNotExist()
        assertEquals("Kalita", runBlocking { brews.latest()?.coffee })
    }

    /** Discard on the summary still asks first, and Cancel keeps the brew (#181, A1). */
    @Test
    fun summaryDiscardStillAsks() {
        espressoSummary()
        // By action, not touch: the curve pushes Discard below the fold.
        compose.onNode(hasText(text(R.string.discard)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitFor(R.string.discard_brew_title)
        click(R.string.cancel)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.discard_brew_title)).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(text(R.string.brew_saved)).assertIsDisplayed()
    }

    /** An espresso on its dose step, before anything is weighed (#185). */
    private fun espressoDose() {
        open("synthetic-espresso", 0)
        click(R.string.mode_espresso)
        compose.waitForIdle()
    }

    /** Waits until the brew has closed and the tabs are back. */
    private fun waitForBrewClosed() {
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("brew-screen")).fetchSemanticsNodes().isEmpty() }
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_home)), 5_000)
    }

    /** On the dose step, Back and the X both close the brew (#185, Dose row). */
    @Test
    fun doseBackClosesTheBrew() {
        espressoDose()
        tapBack()
        waitForBrewClosed()
    }

    @Test
    fun doseCloseBrewClosesTheBrew() {
        espressoDose()
        tapCloseBrew()
        waitForBrewClosed()
    }

    /** Back on the beans step returns to the dose (#185, Beans row); the X closes the brew. */
    @Test
    fun beansBackReturnsToTheDoseStep() {
        espressoDose()
        click(R.string.next)
        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
        tapBack()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("weigh-step-next")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(text(R.string.ratio_caption)).assertIsDisplayed()
        // Next from the dose weighs the beans again.
        click(R.string.next)
        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
    }

    @Test
    fun beansCloseBrewClosesTheBrew() {
        espressoDose()
        click(R.string.next)
        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
        tapCloseBrew()
        waitForBrewClosed()
    }

    /** Back on the espresso preparation returns to the beans, and the X closes the brew (#185, Prepare row). */
    @Test
    fun portafilterBackReturnsToTheBeansStep() {
        espressoDose()
        click(R.string.next)
        compose.onNodeWithTag("weigh-step-next").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("portafilter-ready"), 5_000)
        tapBack()
        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
        compose.onNodeWithTag("portafilter-ready").assertDoesNotExist()
    }

    @Test
    fun portafilterCloseBrewClosesTheBrew() {
        espressoDose()
        click(R.string.next)
        compose.onNodeWithTag("weigh-step-next").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("portafilter-ready"), 5_000)
        tapCloseBrew()
        waitForBrewClosed()
    }

    /** The guided espresso from the synthetic session, on its brewing shot (#287). */
    private fun espressoLive() {
        val scale = open("synthetic-espresso-guide", 0)
        click(R.string.mode_espresso)
        compose.waitUntilAtLeastOneExists(hasText("Espresso Oster") and isSelectable(), 5_000)
        compose.onNode(hasText("Espresso Oster") and isSelectable()).performClick()
        click(R.string.next)
        click(R.string.next)
        click(R.string.prepare_ready)
        compose.waitForIdle()
        playUntilShown(scale, R.string.brewing_espresso, 60_000)
        compose.waitForIdle()
    }

    /** Back in Live asks to restart the brew, and Cancel keeps the shot running (#287). */
    @Test
    fun liveBackAsksToRestartAndCancelKeepsBrewing() {
        espressoLive()
        tapBack()
        waitFor(R.string.restart_brew_title)
        compose.onNodeWithText(text(R.string.restart_brew_text)).assertIsDisplayed()
        click(R.string.cancel)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.restart_brew_title)).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(text(R.string.brewing_espresso)).assertIsDisplayed()
    }

    /** Restart drops the shot and returns to Prepare, and nothing is saved (#287). */
    @Test
    fun liveRestartReturnsToPrepareAndSavesNothing() {
        espressoLive()
        tapBack()
        waitFor(R.string.restart_brew_title)
        click(R.string.restart)
        compose.waitUntilAtLeastOneExists(hasTestTag("portafilter-ready"), 5_000)
        compose.onAllNodesWithText(text(R.string.restart_brew_title)).assertCountEquals(0)
        compose.onAllNodesWithText(text(R.string.brew_saved)).assertCountEquals(0)
        assertTrue(runBlocking { brews.latest() } == null)
    }

    /** The filter checklist keeps its marks through a step back and forward again (#185, Prepare row). */
    @Test
    fun filterPrepareMarksSurviveAStepBack() {
        settings.setLastRecipeId(BrewMode.FILTER, BuiltInRecipes.V60_ID)
        open("synthetic-filter-lift", 0)
        click(R.string.mode_filter)
        compose.waitUntilAtLeastOneExists(hasText("V60 One and Done") and isSelectable(), 5_000)
        compose.onNode(hasText("V60 One and Done") and isSelectable()).performScrollTo().performClick()
        click(R.string.next)
        click(R.string.next)
        waitFor(R.string.prepare_filter_title)
        compose.onNodeWithTag("filter-prepare-row-0").performClick()
        compose.onNodeWithTag("filter-prepare-row-0").assert(isOn())
        tapBack()
        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
        compose.onNodeWithTag("weigh-step-next").performClick()
        waitFor(R.string.prepare_filter_title)
        compose.onNodeWithTag("filter-prepare-row-0").assert(isOn())
    }

    /** The summary closes through Back, which saves the notes; it has no X (#185, Summary row). */
    @Test
    fun summaryHasNoCloseBrewButton() {
        espressoSummary()
        compose.onAllNodes(hasContentDescription(text(R.string.close_brew))).assertCountEquals(0)
    }

    /** Espresso with a recipe: a target, so its values are shown and nothing changes the dose or ratio (#189). */
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
    fun espressoFreeBrewCalculatesWater() {
        settings.setLastRecipeId(BrewMode.ESPRESSO, SettingsStore.FREE_BREW)
        open("idle-5min", 0)
        click(R.string.mode_espresso)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.free_brew)), 5_000)
        compose.waitForIdle()

        fun water(ratio: Double) = "${Format.grams(18.0 * ratio, locale)} ${text(R.string.unit_ml)}"
        compose.onNodeWithText(water(2.0)).assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithContentDescription(text(R.string.ratio_more)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(water(2.1)), 5_000)

        // The dose is still the user's to set.
        compose.onNodeWithText("${Format.grams(18.0, locale)} ${text(R.string.unit_grams)}").performClick()
        compose.onNodeWithText(text(R.string.dose_field)).assertExists()
    }

    /** The 320dp runner width from #145: keep the weigh action in the rendered viewport. */
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
    @Test
    fun espressoGuide() {
        val scale = open("synthetic-espresso-guide", 0)
        click(R.string.mode_espresso)
        compose.waitUntilAtLeastOneExists(hasText("Espresso Oster") and isSelectable(), 5_000)
        compose.onNode(hasText("Espresso Oster") and isSelectable()).performClick()
        click(R.string.next)
        // Weigh the beans: the scale shows nothing yet, so Next keeps the planned dose.
        click(R.string.next)
        waitFor(R.string.prepare_portafilter_title)
        compose.onNode(hasContentDescription(text(R.string.milestone_portafilter))).assertIsDisplayed()
        // The localized counter's own prefix ("Step " or "Etapa "), so the check means something in pt-BR too.
        val counterPrefix = context.getString(R.string.step_of_named, 1, 8, "").substringBefore("1")
        compose.onAllNodes(hasText(counterPrefix, substring = true)).assertCountEquals(0)
        compose.onNodeWithText(context.getString(R.string.prepare_dose, Format.grams(19.0, context.resources.configuration.locales[0]))).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.prepare_grind)).assertIsDisplayed()
        compose.onNodeWithTag("portafilter-grind-setting", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("10", useUnmergedTree = true).assertIsDisplayed()
        listOf(R.string.prepare_rdt, R.string.prepare_wdt, R.string.prepare_tamp, R.string.prepare_puck_screen).forEach {
            compose.onNodeWithText(text(it)).assertIsDisplayed()
        }
        compose.onNodeWithTag("portafilter-ready").assertIsDisplayed()
        click(R.string.prepare_ready)
        compose.waitForIdle()
        // The cup lands at 8 s and is tared as soon as it is still.
        playUntilShown(scale, R.string.taring, 15_000)
        compose.waitForIdle()
        playUntilShown(scale, R.string.espresso_ready, 20_000)
        compose.waitForIdle()
    }

    /**
     * With animations off, the espresso pictures are still frames: the cup, the LED and the open ring show the same
     * state as the animated ones, and the weight, timer and flow stay on screen through the shot (#194).
     */
    @Test
    fun espressoMotionStill() {
        val scale = open("synthetic-espresso-guide", 0)
        click(R.string.mode_espresso)
        compose.waitUntilAtLeastOneExists(hasText("Espresso Oster") and isSelectable(), 5_000)
        compose.onNode(hasText("Espresso Oster") and isSelectable()).performClick()
        click(R.string.next)
        click(R.string.next)
        click(R.string.prepare_ready)
        compose.waitForIdle()
        playUntilShown(scale, R.string.taring, 15_000)
        compose.waitForIdle()
        playUntilShown(scale, R.string.espresso_ready, 20_000)
        compose.waitForIdle()
        compose.onNode(hasContentDescription(text(R.string.milestone_shot))).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.label_flow)).assertIsDisplayed()
        playUntilShown(scale, R.string.brewing_espresso, 60_000)
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.label_flow)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.label_ratio)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.stop)).assertIsDisplayed()
    }

    /**
     * With animations off, the portafilter checklist still ticks: every item can be checked, and Ready stays on screen and
     * enabled, emphasized or not (#198).
     */
    @Test
    fun portafilterChecksWithoutMotion() {
        open("synthetic-espresso-guide", 0)
        click(R.string.mode_espresso)
        compose.waitUntilAtLeastOneExists(hasText("Espresso Oster") and isSelectable(), 5_000)
        compose.onNode(hasText("Espresso Oster") and isSelectable()).performClick()
        click(R.string.next)
        click(R.string.next)
        waitFor(R.string.prepare_portafilter_title)
        compose.onAllNodes(isToggleable()).assertCountEquals(5)
        // The text side of the row is part of its touch target, not just the leading mark.
        compose.onNodeWithTag("portafilter-row-0").performClick()
        compose.onAllNodes(isToggleable())[0].assert(isOn())
        compose.onNodeWithTag("portafilter-row-0").performClick()
        compose.onAllNodes(isToggleable())[0].assert(!isOn())
        repeat(5) { compose.onAllNodes(isToggleable())[it].performClick() }
        compose.waitForIdle()
        compose.onAllNodes(isToggleable()).assertAll(isOn())
        compose.onNodeWithTag("portafilter-ready").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(text(R.string.prepare_ready)).assertIsDisplayed()
    }

    /** At 640 dp tall, the picture is omitted and the five rows and Ready fit without scrolling. */
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
    fun espressoMotionAnimated() {
        val before = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            val scale = open("synthetic-espresso-guide", 0)
            click(R.string.mode_espresso)
            compose.waitUntilAtLeastOneExists(hasText("Espresso Oster") and isSelectable(), 5_000)
            compose.onNode(hasText("Espresso Oster") and isSelectable()).performClick()
            click(R.string.next)
            click(R.string.next)
            click(R.string.prepare_ready)
            compose.waitForIdle()
            playUntilShown(scale, R.string.taring, 15_000)
            compose.waitForIdle()
            // The tare rolls the weight down to zero on its 400 ms tween; the readout keeps its labels while it does.
            while (compose.onAllNodes(hasText(Format.grams(0.0, locale))).fetchSemanticsNodes().isEmpty()) {
                check(scale.nowMs < 20_000) { "the weight never rolled to zero" }
                compose.onNodeWithText(text(R.string.label_flow)).assertIsDisplayed()
                compose.onNodeWithText(text(R.string.label_ratio)).assertIsDisplayed()
                play(scale, scale.nowMs + 100)
                compose.waitForIdle()
            }
            assertWeightAndTimerShown()
            compose.onNodeWithText(text(R.string.label_flow)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.label_ratio)).assertIsDisplayed()
            playUntilShown(scale, R.string.espresso_ready, 20_000)
            compose.waitForIdle()
            compose.onNode(hasContentDescription(text(R.string.milestone_shot))).assertIsDisplayed()
            assertWeightAndTimerShown()
            compose.onNodeWithText(text(R.string.label_flow)).assertIsDisplayed()
            playUntilShown(scale, R.string.brewing_espresso, 60_000)
            compose.waitForIdle()
            compose.onNode(hasContentDescription(text(R.string.milestone_shot))).assertIsDisplayed()
            assertWeightAndTimerShown()
            compose.onNodeWithText(text(R.string.label_flow)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.label_ratio)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.stop)).assertIsDisplayed()
        } finally {
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, before)
        }
    }

    /** Retry is greyed out while the scale is disconnected, and the error stays on screen; it works again once connected. */
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
    @Test
    fun filter() {
        val scale = open("synthetic-filter-lift", 0)
        click(R.string.mode_filter)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.free_brew)), 5_000)
        compose.waitForIdle()
        click(R.string.free_brew)
        click(R.string.next)
        // Weigh the beans: the scale shows nothing yet, so Next keeps the planned dose.
        click(R.string.next)
        click(R.string.prepare_ready)
        playUntilShown(scale, R.string.taring, 2_000)
        compose.waitForIdle()
        playUntilShown(scale, R.string.filter_ready, 7_000)
        compose.waitForIdle()
        // Pours from 20 s, then the dripper lifts at 90 s and pauses the brew.
        play(scale, 60_000)
        compose.waitForIdle()
        play(scale, 90_000)
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.filter_paused)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.filter_paused_hint)).assertIsDisplayed()
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).assertIsDisplayed()
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.brew_saved)), 5_000)
        compose.waitForIdle()
        // By action, not touch: the summary's layout can still shift as the notes load off the main thread.
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_brews)), 5_000)
        compose.onNodeWithText(text(R.string.tab_brews)).performSemanticsAction(SemanticsActions.OnClick)
        // The list loads the brew from the database off the main thread, so wait for its row before taking it.
        compose.waitUntilAtLeastOneExists(hasText(" ${text(R.string.unit_grams)} →", substring = true), 5_000)
        // The dose is what the scale showed on the weigh step, so take the only brew in the list.
        compose.onAllNodes(hasText(" ${text(R.string.unit_grams)} →", substring = true))[0].performSemanticsAction(SemanticsActions.OnClick)
        waitFor(R.string.delete)
        compose.waitForIdle()
    }

    /** Filter checklist follows its recipe, and weighing alone does not tare the dripper and server. */
    @Test
    fun filterPrepareStepWaitsForReady() {
        settings.setLastRecipeId(BrewMode.FILTER, BuiltInRecipes.V60_ID)
        val scale = open("synthetic-filter-lift", 0)
        click(R.string.mode_filter)
        compose.waitUntilAtLeastOneExists(hasText("V60 One and Done") and isSelectable(), 5_000)
        compose.onNode(hasText("V60 One and Done") and isSelectable()).performScrollTo().performClick()
        compose.waitForIdle()
        click(R.string.next)
        val beanWeighTares = scale.tareCommands
        click(R.string.next)

        waitFor(R.string.prepare_filter_title)
        assertEquals("the filter is not tared during preparation", beanWeighTares, scale.tareCommands)
        compose.onNodeWithContentDescription(text(R.string.milestone_prepare)).assertIsDisplayed()
        compose.onNodeWithTag("filter-prepare-picture").assertIsDisplayed()
        compose.onNodeWithTag("filter-prepare-dose").assertIsDisplayed()
        // The single source of the filter row order: the dripper and server go on the scale before the grounds (#278).
        listOf(
            R.string.prepare_rdt,
            R.string.prepare_grind,
            R.string.prepare_rinse_filter,
            R.string.prepare_dripper_server,
            R.string.prepare_add_grounds,
        ).forEachIndexed { row, label ->
            compose.onNodeWithTag("filter-prepare-row-$row").assertIsDisplayed().assertTextContains(text(label))
        }
        compose.onNodeWithTag("filter-prepare-grind-setting", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("filter-prepare-ready").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("filter-prepare-row-0").performClick()
        compose.onNodeWithTag("filter-prepare-row-0").assert(isOn())
        (1..4).forEach { index ->
            compose.onNodeWithTag("filter-prepare-row-$index").performClick()
        }
        compose.onAllNodes(isToggleable()).assertAll(isOn())
        compose.onNodeWithTag("filter-prepare-ready").assertIsDisplayed().assertIsEnabled()

        click(R.string.prepare_ready)
        compose.waitForIdle()
        assertTrue("Ready starts the tare", scale.tareCommands > 0)
        playUntilShown(scale, R.string.taring, 2_000)
    }

    /** Filter opens free with manual controls even when the last brew used a recipe. */
    @Test
    fun filterFreeBrewKeepsItsManualControls() {
        settings.setLastRecipeId(BrewMode.FILTER, BuiltInRecipes.V60_ID)
        open("idle-5min", 0)
        click(R.string.mode_filter)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.free_brew)), 5_000)
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.ratio_caption)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.ratio_less)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.ratio_more)).assertIsDisplayed()
    }

    /** A filter recipe makes water primary, adjusts only this brew and shows scaled targets alongside their times. */
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
    fun filterGuide() {
        val recipe = Recipe(
            id = "screen-test-guide", name = "V60 · 15 g", mode = BrewMode.FILTER, method = "V60", doseGrams = 15.0, targetRatio = 17.0,
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 50.0, 0),
                RecipeStep(StepKind.WAIT, null, 10_000),
                RecipeStep(StepKind.SWIRL, null, 30_000),
                RecipeStep(StepKind.POUR, 100.0, 35_000),
                RecipeStep(StepKind.POUR, 150.0, 50_000),
                RecipeStep(StepKind.POUR, 200.0, 60_000),
            ),
            createdAt = 0, updatedAt = 0,
        )
        runBlocking { recipes.save(recipe) }
        settings.setLastRecipeId(BrewMode.FILTER, recipe.id)
        val scale = open("synthetic-filter-lift", 0)
        click(R.string.mode_filter)
        // The recipe loads off the main thread: Next only takes the dose once its chip is selected.
        compose.waitUntilAtLeastOneExists(hasText(recipe.name), 5_000)
        compose.onNode(hasText(recipe.name) and isSelectable()).performScrollTo().performClick()
        compose.waitForIdle()
        click(R.string.next)
        click(R.string.next)
        click(R.string.prepare_ready)
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text(R.string.ratio_caption))).fetchSemanticsNodes().isEmpty() }
        play(scale, 24_000)
        compose.waitUntilAtLeastOneExists(hasText(context.getString(R.string.guide_bloom, Format.grams(50.0, locale))), 5_000)
        compose.waitForIdle()
        val counterPrefix = context.getString(R.string.step_of_named, 1, recipe.steps.size, "").substringBefore("1")
        compose.onAllNodes(hasText(counterPrefix, substring = true)).assertCountEquals(0)
        compose.onNodeWithContentDescription(
            context.getString(R.string.milestone_and_step, text(R.string.milestone_brew), text(R.string.step_bloom)),
        ).assertIsDisplayed()
        // The brew segments fill as their step opens (shown at once with animations off): earlier ones full, later ones empty.
        compose.onNodeWithTag("milestone-fill-2", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("milestone-fill-3", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("milestone-fill-4", useUnmergedTree = true).assertDoesNotExist()
        play(scale, 45_000)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.guide_wait)), 5_000)
        compose.onNodeWithTag("countdown-ring").assertIsDisplayed()
        compose.waitForIdle()
        play(scale, 52_000)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.guide_swirl)), 5_000)
        compose.onNodeWithTag("countdown-ring").assertIsDisplayed()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(
            context.getString(R.string.milestone_and_step, text(R.string.milestone_brew), text(R.string.step_swirl)),
        ).assertIsDisplayed()
        compose.onNodeWithTag("milestone-fill-4", useUnmergedTree = true).assertExists()
        play(scale, 59_000)
        compose.waitUntilAtLeastOneExists(hasText(context.getString(R.string.guide_pour, Format.grams(100.0, locale))), 5_000)
        compose.waitForIdle()
        play(scale, 69_000)
        compose.waitUntilAtLeastOneExists(hasText(context.getString(R.string.guide_pour, Format.grams(150.0, locale))), 5_000)
        compose.waitForIdle()
        play(scale, 89_500)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.guide_drawdown)), 5_000)
        compose.waitForIdle()
        // The lift comes after the last pour (200 g) and the drawdown has begun, so it ends the brew at once, with no Done (#277).
        play(scale, 90_000)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.brew_saved)), 5_000)
        compose.waitForIdle()
        // The summary opens before the save is written: close it and wait for the brew in the list, so the save finishes.
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_brews)), 5_000)
        compose.onNodeWithText(text(R.string.tab_brews)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilAtLeastOneExists(hasText(" ${text(R.string.unit_grams)} →", substring = true), 5_000)
    }

    /** The guided cup and weight use the shared target bands in filter and French press; lateness stays muted text. */
    @Test
    fun guidedCupAndWeightFollowStepTargetBandsInBothModes() {
        val target = 100.0
        val cases = listOf(
            94.9 to TargetBand.BELOW,
            95.0 to TargetBand.AT,
            105.0 to TargetBand.AT,
            105.1 to TargetBand.ABOVE,
        )
        fun state(mode: BrewMode, weight: Double) = BrewUiState(
            mode = mode,
            step = BrewStep.LIVE,
            phase = BrewPhase.BREWING,
            weightGrams = weight,
            recipe = Recipe(
                id = "guided-cup-${mode.name}", name = "Cup color", mode = mode,
                method = if (mode == BrewMode.FRENCH_PRESS) "French press" else "V60",
                doseGrams = 15.0, targetRatio = 16.0,
                steps = listOf(RecipeStep(StepKind.POUR, target, 0)), createdAt = 0, updatedAt = 0,
            ),
            guide = StepState(0, (weight / target).toFloat(), null, Pace.BEHIND, 11_000, null),
        )

        var current by mutableStateOf(state(BrewMode.FILTER, 94.9))
        var expectedCup = Color.Unspecified
        var expectedWeight = Color.Unspecified
        var expectedPace = Color.Unspecified
        var expectedServerBody = Color.Unspecified
        compose.setContent {
            AppTheme {
                val scheme = MaterialTheme.colorScheme
                expectedCup = scheme.ringStroke(targetBand(current.weightGrams, target), isSystemInDarkTheme())
                expectedWeight = expectedCup.readableOn(scheme.surface)
                expectedPace = scheme.onSurfaceVariant
                expectedServerBody = scheme.surfaceContainerHighest
                GuidedStep(current) {}
            }
        }

        fun assertContainsColor(tag: String, color: Color) {
            val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            val expected = color.toArgb()
            val found = (0 until bitmap.height).any { y -> (0 until bitmap.width).any { x -> bitmap.getPixel(x, y) == expected } }
            assertTrue("$tag should render $color", found)
        }

        fun assertServerHasNoTargetMark() {
            val bitmap = compose.onNodeWithTag("guided-cup").captureToImage().asAndroidBitmap()
            val scale = bitmap.width / 80f
            val x = ((65f - 26f) * scale).toInt()
            val y = ((bitmap.height - 52f * scale) / 2f + (69.875f - 50f) * scale).toInt()
            assertEquals(expectedServerBody.toArgb(), bitmap.getPixel(x, y))
        }

        listOf(BrewMode.FILTER, BrewMode.FRENCH_PRESS).forEach { mode ->
            cases.forEach { (weight, band) ->
                current = state(mode, weight)
                compose.waitForIdle()
                assertEquals(band, targetBand(weight, target))
                compose.onNodeWithTag("guided-server").assertIsDisplayed()
                compose.onNodeWithTag("guided-cup").assertIsDisplayed()
                assertContainsColor("guided-cup", expectedCup)
                if (mode == BrewMode.FILTER && weight == 94.9) assertServerHasNoTargetMark()
                assertContainsColor("guided-weight", expectedWeight)
                assertContainsColor("guide-pace", expectedPace)
                compose.onNodeWithText(context.getString(R.string.guide_behind, 11, text(R.string.unit_seconds))).assertIsDisplayed()
            }
        }
    }

    /** The press art on each stage with animations off: still frames that carry all the information (#226). */
    @Test
    fun pressArtStagesWithAnimationsOff() = pressArtStages(animated = false)

    /** The same stages with animations on: the motion ends on the same frames and the steps stay readable (#226). */
    @Test
    fun pressArtStagesWithAnimationsOn() {
        val before = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            pressArtStages(animated = true)
        } finally {
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, before)
        }
    }

    private fun pressArtStages(animated: Boolean) {
        val recipe = Recipe(
            id = "press-art-$animated", name = "Press art", mode = BrewMode.FRENCH_PRESS, method = "French press",
            doseGrams = 20.0, targetRatio = 15.0,
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 100.0, 0),
                RecipeStep(StepKind.POUR, 300.0, 60_000),
                RecipeStep(StepKind.PRESS, null, 240_000),
            ),
            createdAt = 0, updatedAt = 0,
        )
        fun state(
            index: Int,
            weight: Double = 0.0,
            progress: Float = 0f,
            elapsedMs: Long = 0,
            removed: Boolean = false,
            dueInMs: Long? = null,
        ) = BrewUiState(
            mode = BrewMode.FRENCH_PRESS, step = BrewStep.LIVE, phase = BrewPhase.BREWING, connected = true,
            weightGrams = weight, elapsedMs = elapsedMs, recipe = recipe, frenchPressRemoved = removed,
            guide = StepState(index, progress, dueInMs, null, null, null),
        )

        var current by mutableStateOf(state(0, weight = 100.0))
        var scheme = lightColorScheme()
        var dark = false
        compose.setContent {
            AppTheme {
                scheme = MaterialTheme.colorScheme
                dark = isSystemInDarkTheme()
                GuidedStep(current) {}
            }
        }

        fun hasColor(tag: String, color: Color): Boolean {
            val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            val expected = color.toArgb()
            return (0 until bitmap.height).any { y -> (0 until bitmap.width).any { x -> bitmap.getPixel(x, y) == expected } }
        }

        /**
         * The pixel of a point in the press's 80 × 90 box (PressArt.drawPress), drawn under a [lift] of the scene and a
         * [scaleRoom] under it (0 without the scale, see PressOnScale).
         */
        fun pixelAt(tag: String, viewX: Float, viewY: Float, lift: Float, scaleRoom: Float = 0.12f): Int {
            val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            val size = bitmap.width.toFloat()
            val room = size * scaleRoom
            val rise = size * 0.16f * lift
            val boxHeight = size - room
            val scale = minOf(size / 80f, boxHeight / 90f)
            val x = (size - 80f * scale) / 2f + viewX * scale
            val y = -rise + (boxHeight - 90f * scale) / 2f + viewY * scale
            return bitmap.getPixel(x.toInt(), y.toInt())
        }

        // Fill: the carafe takes the band color of the weight against the target, with no target line.
        for ((weight, band) in listOf(60.0 to TargetBand.BELOW, 100.0 to TargetBand.AT, 120.0 to TargetBand.ABOVE)) {
            current = state(0, weight = weight)
            compose.waitForIdle()
            compose.onNodeWithTag("guided-server").assertIsDisplayed()
            assertEquals(band, targetBand(weight, 100.0))
            assertTrue("the fill follows the band", hasColor("guided-cup", scheme.ringStroke(band, dark)))
            // The lid is not on yet: the spot where it sits in the steep is empty.
            assertTrue("no lid before the steep", pixelAt("guided-cup", 24f, 19.5f, lift = 0f, scaleRoom = 0.12f) != scheme.outline.toArgb())
            compose.onNodeWithTag("guided-weight").assertIsDisplayed()
            compose.onNodeWithText("${Format.grams(weight, locale)} / ${Format.grams(100.0, locale)} g").assertIsDisplayed()
        }

        // Steep: the coffee darkens inside the smooth ring, and the countdown stays readable.
        for (progress in listOf(0f, 1f)) {
            current = state(2, weight = 300.0, progress = progress, elapsedMs = 100_000, dueInMs = 120_000)
            compose.waitForIdle()
            compose.onNodeWithTag("steep-shape").assertIsDisplayed()
            compose.onNodeWithTag("steep-press").assertIsDisplayed()
            assertTrue("steep $progress", hasColor("steep-press", PressArt.steepColor(progress, scheme.primary)))
            // The lid is on from the steep on: its bar sits above the frame.
            assertEquals("the lid $progress", scheme.outline.toArgb(), pixelAt("steep-press", 24f, 19.5f, lift = 0f, scaleRoom = 0f))
            compose.onNodeWithText(Format.timer(120)).assertIsDisplayed()
        }
        assertTrue(PressArt.steepColor(1f, scheme.primary).luminance() < PressArt.steepColor(0f, scheme.primary).luminance())

        // Deadline: the carafe at its darkest, the delay in view.
        current = state(3, weight = 300.0, elapsedMs = 255_000)
        compose.waitForIdle()
        compose.onNodeWithTag("press-deadline-art").assertIsDisplayed()
        assertTrue(hasColor("guided-cup", PressArt.steepColor(1f, scheme.primary)))
        compose.onNodeWithText(text(R.string.guide_remove_press)).assertIsDisplayed()
        compose.onNodeWithTag("press-delay").assertIsDisplayed()
        compose.onNodeWithText("+${Format.timer(15)}").assertIsDisplayed()

        // Removal: the carafe has left the scale and the plunger sits in the coffee, in both settings.
        current = state(3, weight = 0.0, elapsedMs = 270_000, removed = true)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.guide_press_serve)), 5_000)
        compose.waitForIdle()
        compose.onNodeWithTag("guided-server").assertIsDisplayed()
        assertEquals("the plunger plate", scheme.outline.toArgb(), pixelAt("guided-cup", 30f, 74f, lift = 1f))
        compose.onNodeWithText(text(R.string.guide_press_serve_text)).assertIsDisplayed()
    }

    /** Filter waits and swirls count down on the smooth ring; the French press steep has its own clock (#302). */
    @Test
    fun guideCountdownRingCoversWaitSwirlAndSteep() {
        val cases = listOf(
            Triple(BrewMode.FILTER, RecipeStep(StepKind.WAIT, null, 0), R.string.guide_wait),
            Triple(BrewMode.FILTER, RecipeStep(StepKind.SWIRL, null, 0), R.string.guide_swirl),
            Triple(BrewMode.FRENCH_PRESS, RecipeStep(StepKind.PRESS, null, 240_000), R.string.guide_steep),
        )
        fun stateFor(index: Int): BrewUiState {
            val (mode, step) = cases[index]
            val recipe = Recipe(
                id = "screen-test-countdown-$index", name = "Countdown test", mode = mode,
                method = if (mode == BrewMode.FRENCH_PRESS) "French press" else "V60", doseGrams = 15.0, targetRatio = 16.0,
                steps = listOf(step), createdAt = 0, updatedAt = 0,
            )
            return BrewUiState(
                mode = mode,
                step = BrewStep.LIVE,
                recipe = recipe,
                guide = StepState(index = 0, progress = 0.5f, dueInMs = 10_000, pace = null, paceMs = null, overshootGrams = null),
            )
        }
        var state by mutableStateOf(stateFor(0))
        compose.setContent { AppTheme { GuidedStep(state) {} } }
        cases.forEachIndexed { index, (_, _, label) ->
            if (index > 0) {
                state = stateFor(index)
                compose.waitForIdle()
            }
            compose.onAllNodesWithText(text(label), substring = true).onFirst().assertIsDisplayed()
            compose.onNodeWithTag(if (cases[index].first == BrewMode.FRENCH_PRESS) "steep-progress" else "countdown-ring").assertIsDisplayed()
        }
    }

    /** The French press steep keeps one clock (#302): the press on its shape, the countdown and the bar to the deadline, no ring and no total elapsed note. */
    @Test
    fun frenchPressSteepShowsOneClock() {
        val recipe = Recipe(
            id = "screen-test-steep-one-clock", name = "Steep one clock", mode = BrewMode.FRENCH_PRESS, method = "French press",
            doseGrams = 20.0, targetRatio = 15.0,
            steps = listOf(RecipeStep(StepKind.BLOOM, 300.0, 0), RecipeStep(StepKind.PRESS, null, 240_000)),
            createdAt = 0, updatedAt = 0,
        )
        compose.setContent {
            AppTheme {
                GuidedStep(
                    BrewUiState(
                        mode = BrewMode.FRENCH_PRESS, step = BrewStep.LIVE, phase = BrewPhase.BREWING, connected = true,
                        weightGrams = 300.0, elapsedMs = 137_000, recipe = recipe,
                        guide = StepState(index = 1, progress = 0.5f, dueInMs = 120_000, pace = null, paceMs = null, overshootGrams = null),
                    )
                ) {}
            }
        }
        compose.onNodeWithTag("steep-press").assertIsDisplayed()
        compose.onNodeWithTag("steep-shape").assertIsDisplayed()
        compose.onNodeWithTag("steep-progress").assertIsDisplayed()
        compose.onAllNodesWithTag("countdown-ring").assertCountEquals(0)
        compose.onNodeWithText(Format.timer(240)).assertIsDisplayed()
        compose.onAllNodesWithText(Format.grams(300.0, locale), substring = true).assertCountEquals(0)
        compose.onNodeWithText(Format.timer(120)).assertIsDisplayed()
        compose.onAllNodesWithText(Format.timer(137)).assertCountEquals(0)
    }

    /** The stir step counts down with the shared ring and says the spoon is not water (#219, R3b). */
    @Test
    fun guideStirShowsTheRingAndTheSpoonHint() {
        val recipe = Recipe(
            id = "screen-test-stir", name = "Stir test", mode = BrewMode.FRENCH_PRESS, method = "French press",
            doseGrams = 20.0, targetRatio = 15.0,
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 62.0, 0),
                RecipeStep(StepKind.STIR, null, 5_000),
                RecipeStep(StepKind.PRESS, null, 240_000),
            ),
            createdAt = 0, updatedAt = 0,
        )
        compose.setContent {
            AppTheme {
                GuidedStep(
                    BrewUiState(
                        mode = BrewMode.FRENCH_PRESS, step = BrewStep.LIVE, phase = BrewPhase.BREWING, connected = true,
                        weightGrams = 62.0, elapsedMs = 8_000, recipe = recipe,
                        guide = StepState(index = 1, progress = 0.5f, dueInMs = 15_000, pace = null, paceMs = null, overshootGrams = null),
                    )
                ) {}
            }
        }
        compose.onNodeWithTag("countdown-ring").assertIsDisplayed()
        compose.onAllNodesWithText(text(R.string.guide_stir)).onFirst().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.guide_stir_text)).assertIsDisplayed()
        compose.onNodeWithText(Format.timer(15)).assertIsDisplayed()
    }

    /** The ideal drain-time bar follows the timer before, within and after its range; recipes without one stay unchanged. */
    @Test
    fun filterDrawdownShowsThreeDrainTimeStatesAndOmitsTheBarWithoutARange() {
        val recipe = Recipe(
            id = "drain-time-screen", name = "Drain time", mode = BrewMode.FILTER, method = "V60",
            doseGrams = 15.0, targetRatio = 16.0,
            targetMinMs = 120_000, targetMaxMs = 150_000,
            steps = listOf(RecipeStep(StepKind.POUR, 240.0, 0)), createdAt = 0, updatedAt = 0,
        )
        val pouringRecipe = recipe.copy(
            steps = listOf(RecipeStep(StepKind.BLOOM, 40.0, 0), RecipeStep(StepKind.POUR, 240.0, 30_000)),
        )
        fun state(elapsedMs: Long, shownRecipe: Recipe = recipe, phase: BrewPhase = BrewPhase.BREWING) = BrewUiState(
            mode = shownRecipe.mode,
            step = BrewStep.LIVE,
            phase = phase,
            elapsedMs = elapsedMs,
            recipe = shownRecipe,
            guide = StepState(1, 1f, null, null, null, null),
        )

        var current by mutableStateOf(state(100_000, pouringRecipe))
        compose.setContent { AppTheme { GuidedStep(current) {} } }
        compose.onNodeWithTag("drain-time-progress").assertDoesNotExist()

        current = state(100_000)
        compose.waitForIdle()
        compose.onNodeWithTag("drain-time-before").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.drain_time_ideal_range, "02:00", "02:30")).assertIsDisplayed()

        current = state(132_000)
        compose.waitForIdle()
        compose.onNodeWithTag("drain-time-inside").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.drain_time_in_range)).assertIsDisplayed()

        current = state(132_000, phase = BrewPhase.PAUSED)
        compose.waitForIdle()
        compose.onNodeWithTag("drain-time-inside").assertIsDisplayed()
        compose.onNodeWithTag("drain-time-progress").assertIsDisplayed()

        current = state(161_000)
        compose.waitForIdle()
        compose.onNodeWithTag("drain-time-past").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.drain_time_past_range, 11, text(R.string.unit_seconds))).assertIsDisplayed()

        current = state(132_000, recipe.copy(targetMinMs = null, targetMaxMs = null))
        compose.waitForIdle()
        compose.onNodeWithTag("drain-time-progress").assertDoesNotExist()

        current = state(132_000, recipe.copy(mode = BrewMode.FRENCH_PRESS, method = "French press"))
        compose.waitForIdle()
        compose.onNodeWithTag("drain-time-progress").assertDoesNotExist()
        current = state(132_000, recipe.copy(mode = BrewMode.ESPRESSO, method = "Espresso"))
        compose.waitForIdle()
        compose.onNodeWithTag("drain-time-progress").assertDoesNotExist()
    }

    /** After a pause the readout shows its held numbers at once, with the ring colour coming back from the paused one. */
    @Test
    fun scaleReadoutResumesFromPauseWithItsNumbers() {
        var paused by mutableStateOf(true)
        compose.setContent {
            AppTheme {
                ScaleReadout(
                    weightGrams = 142.0, flowGramsPerSecond = 0.0, timerSeconds = 74, timerRunning = !paused,
                    started = true, ringAlpha = if (paused) 0.45f else 1f, initialRingAlpha = 0.45f,
                )
            }
        }
        compose.onNodeWithText("01:14").assertIsDisplayed()
        paused = false
        compose.waitForIdle()
        compose.onNodeWithText("01:14").assertIsDisplayed()
        compose.onNodeWithText(Format.grams(142.0, locale), substring = true).assertIsDisplayed()
    }

    /** Live flow stays current with animations disabled, and the optional target only adds its ideal band. */
    @Test
    fun guidedFlowChartHasAnOptionalTargetAndUpdatesLive() {
        fun state(mode: BrewMode, target: Double?, flow: Double) = BrewUiState(
            mode = mode,
            step = BrewStep.LIVE,
            flowGramsPerSecond = flow,
            recipe = Recipe(
                id = "flow-chart-${mode.name}", name = "Flow chart", mode = mode,
                method = if (mode == BrewMode.FRENCH_PRESS) "French press" else "V60",
                doseGrams = 15.0, targetRatio = 16.0,
                steps = listOf(RecipeStep(StepKind.BLOOM, 45.0, 0, targetFlowGramsPerSecond = target)),
                createdAt = 0, updatedAt = 0,
            ),
            guide = StepState(index = 0, progress = 0.5f, dueInMs = null, pace = Pace.ON_TIME, paceMs = 0, overshootGrams = null),
        )

        var current by mutableStateOf(state(BrewMode.FILTER, 5.0, 2.0))
        compose.setContent { AppTheme { GuidedStep(current) {} } }
        compose.onNodeWithTag("flow-chart-target").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.guide_flow, Format.flow(2.0, locale))).assertIsDisplayed()

        current = state(BrewMode.FILTER, null, 3.0)
        compose.waitForIdle()
        compose.onNodeWithTag("flow-chart-no-target").assertIsDisplayed()
        compose.onAllNodes(hasTestTag("flow-chart-target")).assertCountEquals(0)
        compose.onNodeWithText(context.getString(R.string.guide_flow, Format.flow(3.0, locale))).assertIsDisplayed()

        current = state(BrewMode.FRENCH_PRESS, null, 4.0)
        compose.waitForIdle()
        compose.onNodeWithTag("flow-chart-no-target").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.guide_flow, Format.flow(4.0, locale))).assertIsDisplayed()
    }

    /** French press shows live flow and its chart on bloom and pour only; wait and steep hide them (#224). */
    @Test
    fun frenchPressFlowShowsOnlyWhilePouring() {
        val recipe = Recipe(
            id = "press-flow", name = "Press flow", mode = BrewMode.FRENCH_PRESS, method = "French press", doseGrams = 20.0, targetRatio = 15.0,
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 100.0, 0),
                RecipeStep(StepKind.WAIT, null, 0),
                RecipeStep(StepKind.POUR, 300.0, 60_000),
                RecipeStep(StepKind.PRESS, null, 240_000),
            ),
            createdAt = 0, updatedAt = 0,
        )
        fun state(index: Int) = BrewUiState(
            mode = BrewMode.FRENCH_PRESS,
            step = BrewStep.LIVE,
            flowGramsPerSecond = 3.0,
            recipe = recipe,
            guide = StepState(index = index, progress = 0.5f, dueInMs = 10_000, pace = null, paceMs = null, overshootGrams = null),
        )
        var current by mutableStateOf(state(0))
        compose.setContent { AppTheme { GuidedStep(current) {} } }
        listOf(0 to true, 1 to false, 2 to true, 3 to false).forEach { (index, shown) ->
            current = state(index)
            compose.waitForIdle()
            compose.onAllNodes(hasTestTag("flow-chart-no-target")).assertCountEquals(if (shown) 1 else 0)
            compose.onAllNodesWithText(context.getString(R.string.guide_flow, Format.flow(3.0, locale))).assertCountEquals(if (shown) 1 else 0)
        }
    }

    /** The French press summary leaves out the average flow, which divides the water by the whole steep (#224). */
    @Test
    fun frenchPressSummaryHidesTheAverageFlow() {
        fun brew(mode: BrewMode) = Brew("avg-flow-${mode.name}", mode, 0L, 240_000L, 20.0, 300.0, null, 1.25, 0.0, false)
        var current by mutableStateOf(brew(BrewMode.FRENCH_PRESS))
        compose.setContent { AppTheme { BrewNumbers(current) } }
        compose.onNodeWithText(text(R.string.label_time)).assertIsDisplayed()
        compose.onAllNodesWithText(text(R.string.label_flow_avg)).assertCountEquals(0)

        current = brew(BrewMode.FILTER)
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.label_flow_avg)).assertIsDisplayed()
    }

    /** The third mode selector and its preselected built-in French press recipe. */
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
    private fun assertMilestone(name: Int) {
        compose.onNode(hasContentDescription(text(name), substring = true)).assertIsDisplayed()
    }

    /** Water is the French press recipe's control: 400 ml scales the dose and weights, and the steep stays at 4:00 (#215). */
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
    fun frenchPressGuide() {
        val scale = open("synthetic-french-press", 0)
        compose.onNodeWithContentDescription(text(R.string.mode_french_press)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("French press") and isSelectable(), 5_000)
        assertMilestone(R.string.milestone_recipe)
        click(R.string.next)
        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
        assertMilestone(R.string.milestone_beans)
        compose.onNodeWithTag("weigh-step-next").performClick()

        waitFor(R.string.prepare_press_title)
        assertMilestone(R.string.milestone_prepare_press)
        val beanWeighTares = scale.tareCommands
        compose.onNodeWithTag("press-prepare-picture").assertIsDisplayed()
        listOf(R.string.prepare_rdt, R.string.prepare_grind, R.string.prepare_add_grounds, R.string.prepare_press_scale)
            .forEach { compose.onNodeWithText(text(it)).assertIsDisplayed() }
        compose.onNodeWithTag("press-prepare-ready").assertIsDisplayed().assertIsEnabled()
        play(scale, 0) // a reading after Beans cannot start the brew; the zeros after Ready finish the tare
        compose.waitForIdle()
        waitFor(R.string.prepare_press_title)
        assertEquals("only Ready starts the tare", beanWeighTares, scale.tareCommands)
        click(R.string.prepare_ready)
        assertTrue("Ready starts the tare", scale.tareCommands > beanWeighTares)

        play(scale, 80_000)
        compose.waitForIdle()
        assertMilestone(R.string.milestone_brew)
        // The milestone bar names the step; the guide carries no "Step n of m" counter.
        compose.onAllNodes(hasText(context.getString(R.string.step_of_named, 1, 4, "").substringBefore("1"), substring = true)).assertCountEquals(0)
        play(scale, 241_100)
        waitFor(R.string.guide_remove_press)
        compose.waitForIdle()
        assertMilestone(R.string.milestone_serve)
        play(scale, 255_000)
        waitFor(R.string.guide_press_serve)
        compose.waitForIdle()
        assertMilestone(R.string.milestone_serve)
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).performClick()
        waitFor(R.string.brew_saved)
        assertMilestone(R.string.milestone_done)
    }

    /** A free French press: the Steep field on the dose screen, the pour on the ring, then the steep like a recipe's (#218). */
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
    private fun openFreePress(steep: String? = null, waterMl: String? = null): SessionScale {
        val scale = open("synthetic-french-press", 0)
        compose.onNodeWithContentDescription(text(R.string.mode_french_press)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("French press") and isSelectable(), 5_000)
        click(R.string.free_brew)
        compose.waitUntilAtLeastOneExists(hasTestTag("press-steep-field"), 5_000)
        val confirm = hasText(text(R.string.done)) and hasClickAction()
        steep?.let {
            compose.onNodeWithTag("press-steep-field").performClick()
            compose.onNodeWithTag("press-steep-input").performTextReplacement(it)
            compose.onNode(confirm).performClick()
        }
        waterMl?.let {
            compose.onNodeWithText("${Format.grams(300.0, locale)} ${text(R.string.unit_ml)}").performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement(it)
            compose.onNode(confirm).performClick()
        }
        click(R.string.next)
        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
        compose.onNodeWithTag("weigh-step-next").performClick()
        waitFor(R.string.prepare_press_title)
        click(R.string.prepare_ready)
        return scale
    }

    /** Reaching the target water starts the steep at once (#218). */
    @Test
    fun frenchPressFreeBrewSteepsOnReachingTheTargetWater() {
        val scale = openFreePress(waterMl = "90")
        play(scale, 5_000) // the scale jumps to 100 g at 1.1 s, past the 90 g target
        waitFor(R.string.guide_steep_text)
        compose.onAllNodesWithText(text(R.string.brewing_press)).assertCountEquals(0)
    }

    /** A steep that ends before the pour does goes straight to the alert (#218). */
    @Test
    fun frenchPressFreeBrewDeadlineDuringThePourGoesStraightToTheAlert() {
        val scale = openFreePress(steep = "0:10")
        play(scale, 8_000)
        waitFor(R.string.brewing_press)
        // The session's next frame comes at 60 s: the deadline, 10 s after the first water, has long passed.
        play(scale, 61_000)
        waitFor(R.string.guide_remove_press)
        compose.onAllNodesWithText(text(R.string.guide_steep_text)).assertCountEquals(0)
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).assertIsDisplayed()
    }

    private fun frenchPressSteep(session: String = "synthetic-french-press"): SessionScale {
        val scale = open(session, 0)
        compose.onNodeWithContentDescription(text(R.string.mode_french_press)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("French press") and isSelectable(), 5_000)
        click(R.string.next)
        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
        compose.onNodeWithTag("weigh-step-next").performClick()
        waitFor(R.string.prepare_press_title)
        click(R.string.prepare_ready)
        return scale
    }

    /** Before the deadline Done asks first (#222). */
    @Test
    fun frenchPressDoneAsksBeforeTheDeadline() {
        val scale = frenchPressSteep()
        play(scale, 80_000)
        compose.waitForIdle()
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).performClick()
        waitFor(R.string.finish_brew_title)
        compose.onNodeWithText(text(R.string.keep_brewing)).assertIsDisplayed()
    }

    /** After the deadline Done ends the brew directly (#222). */
    @Test
    fun frenchPressDoneEndsDirectlyAfterTheDeadline() {
        val scale = frenchPressSteep()
        play(scale, 241_100)
        waitFor(R.string.guide_remove_press)
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText(text(R.string.finish_brew_title)).assertCountEquals(0)
        compose.onAllNodesWithText(text(R.string.guide_remove_press)).assertCountEquals(0)
    }

    /** A press lifted before the deadline keeps the deadline copy and error color off while it is being confirmed (#279). */
    @Test
    fun frenchPressRemovalCandidatePastTheDeadlineShowsNoDeadlineState() {
        val scale = frenchPressSteep("synthetic-french-press-early-lift")
        play(scale, 241_500)
        compose.waitForIdle()

        compose.onAllNodesWithText(text(R.string.guide_remove_press)).assertCountEquals(0)
        compose.onAllNodesWithText(text(R.string.guide_time_up)).assertCountEquals(0)
        val bitmap = compose.onNodeWithTag("brew-screen").captureToImage().asAndroidBitmap()
        val deadlineColors = listOf(BrandColor.LEAF.light.errorContainer, BrandColor.LEAF.dark.errorContainer).map { it.toArgb() }
        assertTrue(
            "the deadline error color is off while the press is being confirmed",
            (0 until bitmap.height).none { y -> (0 until bitmap.width).any { x -> bitmap.getPixel(x, y) in deadlineColors } },
        )
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.guide_press_serve)), 5_000)
    }

    /**
     * Compare and trends (#106) on three saved V60 brews: two picked with a long press, their curves overlaid with
     * the rating row, then the recipe's trends from the newest one's detail.
     */
    @Test
    fun compareAndTrends() {
        val saved = listOf(Triple(238.0, 185_000L, 4), Triple(245.0, 200_000L, 3), Triple(250.0, 178_000L, 5))
        runBlocking {
            saved.forEachIndexed { i, (water, duration, rating) ->
                val brew = Brew(
                    "compare-$i", BrewMode.FILTER, BREWED_AT - (2 - i) * 86_400_000L, duration, 15.0, water, null, water / (duration / 1000.0), 6.0,
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
        row(250.0).performTouchInput { longClick() }
        row(245.0).performClick()
        compose.waitForIdle()
        click(R.string.compare)
        waitFor(R.string.brew_rating)
        compose.waitForIdle()
        compose.onNodeWithContentDescription(text(R.string.close)).performClick()
        // The picks may come back with the list; tapping a row then picks instead of opening.
        if (compose.onAllNodes(hasText(text(R.string.cancel))).fetchSemanticsNodes().isNotEmpty()) click(R.string.cancel)
        row(250.0).performClick()
        waitFor(R.string.delete)
        compose.onNodeWithText(text(R.string.trends)).performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.label_ratio)), 5_000)
        compose.waitForIdle()
    }

    /** An S-shaped pour to [water] over [durationMs], one sample a second. */
    private fun curve(water: Double, durationMs: Long) = (0..durationMs / 1000).map { s ->
        val f = s * 1000.0 / durationMs
        val weight = water * f * f * (3 - 2 * f)
        BrewSample(s * 1000, weight, water * 6 * f * (1 - f) / (durationMs / 1000.0))
    }

    private companion object {
        /** 12 October 2026, 08:30 UTC: the saved brews' date. */
        const val BREWED_AT = 1_791_793_800_000L

    }
}

/** A phone-sized screen at xxhdpi, as in [AppScreensTest]; the language and theme are the only differences between these. */
private const val SIZE = "w411dp-h891dp"

@Config(qualifiers = "en-$SIZE-notnight-xxhdpi")
class ScreenEnLightTest : ScreenTests("en", "light")

@Config(qualifiers = "en-$SIZE-night-xxhdpi")
class ScreenEnDarkTest : ScreenTests("en", "dark")

@Config(qualifiers = "pt-rBR-$SIZE-notnight-xxhdpi")
class ScreenPtBrLightTest : ScreenTests("pt-BR", "light")

@Config(qualifiers = "pt-rBR-$SIZE-night-xxhdpi")
class ScreenPtBrDarkTest : ScreenTests("pt-BR", "dark")

@Config(qualifiers = "en-w320dp-h640dp-notnight-xxhdpi")
class ScreenSmallEnLightTest : ScreenTests("en", "light", compactOnly = true)

@Config(qualifiers = "en-w320dp-h640dp-night-xxhdpi")
class ScreenSmallEnDarkTest : ScreenTests("en", "dark", compactOnly = true)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-notnight-xxhdpi")
class ScreenSmallPtBrLightTest : ScreenTests("pt-BR", "light", compactOnly = true)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-night-xxhdpi")
class ScreenSmallPtBrDarkTest : ScreenTests("pt-BR", "dark", compactOnly = true)
