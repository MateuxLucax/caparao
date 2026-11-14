package dev.mateux.caparao.ui.screens

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mateux.caparao.ui.App
import dev.mateux.caparao.ui.components.AppActions
import dev.mateux.caparao.ui.FakeScaleSource
import dev.mateux.caparao.model.ScaleState
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import dev.mateux.caparao.R
import dev.mateux.caparao.data.diagnostics.CaptureWriter
import dev.mateux.caparao.data.local.BrewDatabase
import dev.mateux.caparao.data.local.BrewRepository
import dev.mateux.caparao.data.local.CoffeeBagRepository
import dev.mateux.caparao.data.local.RecipeRepository
import dev.mateux.caparao.data.local.SettingsStore
import dev.mateux.caparao.data.scale.ReplayScaleSource
import dev.mateux.caparao.data.scale.ScaleSource
import dev.mateux.caparao.ui.brew.BrewViewModel
import dev.mateux.caparao.ui.brews.BrewDetailViewModel
import dev.mateux.caparao.ui.brews.BrewRatingViewModel
import dev.mateux.caparao.ui.brews.BrewsViewModel
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.BrewSample
import dev.mateux.caparao.model.CoffeeBag
import dev.mateux.caparao.model.ScaleReading
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import dev.mateux.caparao.ui.home.HomeViewModel
import dev.mateux.caparao.ui.components.LauncherShortcut
import dev.mateux.caparao.ui.onboarding.OnboardingViewModel
import dev.mateux.caparao.ui.recipes.RecipeEditorViewModel
import dev.mateux.caparao.ui.coffees.CoffeeBagDetailViewModel
import dev.mateux.caparao.ui.coffees.CoffeeBagsViewModel
import dev.mateux.caparao.ui.recipes.RecipesViewModel
import dev.mateux.caparao.ui.settings.ScaleStatusViewModel
import dev.mateux.caparao.ui.server.ServerViewModel
import dev.mateux.caparao.ui.settings.CaptureViewModel
import dev.mateux.caparao.ui.settings.TargetRatioViewModel
import dev.mateux.caparao.ui.format.Format
import dev.mateux.caparao.ui.theme.AppTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The app on the JVM (Robolectric) with a recorded session standing in for the scale: onboarding, then the tabs. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
// A phone-sized screen: Robolectric's default (320x470 px) leaves the guide's buttons off screen.
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = MainScope()
    private val actions = AppActions(requestBluetoothPermissions = { true }, bluetoothPermissionGranted = { true }, enableBluetooth = {}, openAppSettings = {}, requestLocalNetwork = { true })

    /** The press-ramp recording from the moment there is weight on the scale. */
    private val scale = ReplayScaleSource(
        scope,
        context.assets.open("sessions/press-ramp.jsonl").bufferedReader().use { it.readLines() },
        startAtMs = 38_300,
    )

    private fun text(id: Int) = context.getString(id)

    private val db = Room.inMemoryDatabaseBuilder(context, BrewDatabase::class.java).build()
    private val brews = BrewRepository(db.brews())
    private val recipes = RecipeRepository(db.recipes())
    private val coffeeBags = CoffeeBagRepository(db.coffeeBags())
    private val settings = SettingsStore(context.getSharedPreferences("app-test", Context.MODE_PRIVATE))

    /** Whether the fake resolver treats the server as local, and an optional gate for the checking state. */
    private var localServer = false
    private var localServerGate: CompletableDeferred<Unit>? = null
    private val checked = mutableListOf<Pair<String, String>>()
    private val diagnostics = CaptureWriter(context.cacheDir.resolve("app-screen-tests-${System.nanoTime()}"), scope)

    /** The brew screen's clock, moved by [pour] and [hold] so a whole shot runs without waiting in real time. */
    private var now = 0L

    private fun factory(
        scale: ScaleSource,
        clock: () -> Long = SystemClock::elapsedRealtime,
        onboardingDone: () -> Unit = {},
    ): ViewModelProvider.Factory {
        // These Compose tests host App directly, so mirror MainActivity.onResume().
        scale.start()
        return viewModelFactory {
            initializer { HomeViewModel(scale) }
            initializer { OnboardingViewModel(scale, onboardingDone) }
            initializer { ScaleStatusViewModel(scale) }
            initializer { TargetRatioViewModel(settings) }
            initializer { CaptureViewModel(diagnostics, settings, available = true) }
            initializer { RecipesViewModel(recipes) }
            initializer { CoffeeBagsViewModel(coffeeBags) }
            initializer { CoffeeBagDetailViewModel(requireNotNull(this[CoffeeBagDetailViewModel.BAG_ID]), coffeeBags, brews) }
            initializer { RecipeEditorViewModel(requireNotNull(this[RecipeEditorViewModel.TARGET]), recipes) }
            initializer { BrewsViewModel(brews, settings.syncStatus) {} }
            // No server is reached: Test connection fails, saving enqueues nothing.
            initializer {
                ServerViewModel(
                    settings,
                    { url, token -> checked += url to token; error("no server in tests") },
                    {}, {},
                    localNetwork = { localServerGate?.await(); localServer },
                )
            }
            initializer { BrewViewModel(requireNotNull(this[BrewViewModel.MODE]), scale, brews, settings, recipes, clock = clock) }
            initializer { BrewDetailViewModel(requireNotNull(this[BrewDetailViewModel.BREW_ID]), brews) }
            initializer { BrewRatingViewModel(requireNotNull(this[BrewRatingViewModel.BREW_ID]), brews, coffeeBags) }
        }
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    /** [seconds] of readings at 10 Hz moving from [from] to [to] grams; the brew clock moves with them. */
    private fun pour(scale: FakeScaleSource, from: Double, to: Double, seconds: Double) {
        val steps = (seconds * 10).toInt()
        repeat(steps) { i ->
            now += 100
            val weight = Math.round((from + (to - from) * (i + 1) / steps) * 10) / 10.0
            scale.send(ScaleReading(weight, 0.0, 0, overload = false))
        }
        compose.waitForIdle()
    }

    private fun hold(scale: FakeScaleSource, weight: Double, seconds: Double) = pour(scale, weight, weight, seconds)

    /** The cup, the tare, the first drops: an espresso running with [scale] driven by hand. */
    private fun runEspressoToTheFirstDrops(scale: FakeScaleSource) {
        pour(scale, 0.0, 150.0, 0.5) // the cup goes on
        hold(scale, 150.0, 1.5) // it settles, the app tares
        hold(scale, 0.0, 1.0) // the scale zeroed
        hold(scale, 0.0, 3.0) // pump on, no drops yet
        pour(scale, 0.0, 36.0, 5.0)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.stop)), 5_000)
    }

    @Test
    fun onboardingConnectsToTheScaleAndOpensHome() {
        var done = false
        compose.setContent { AppTheme { App(factory(scale) { done = true }, scale, onboardingDone = false, actions, "1.0-test") } }

        compose.onNodeWithText(text(R.string.welcome_title)).assertExists()
        compose.onNodeWithText(text(R.string.get_started)).performClick()
        compose.onNodeWithText(text(R.string.permissions_title)).assertExists()
        compose.onNodeWithText(text(R.string.allow_bluetooth)).performClick()
        compose.onNodeWithText(text(R.string.connect_scale_title)).assertExists()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.scale_connected)), 5_000)
        compose.onNodeWithText(text(R.string.next)).performClick()
        compose.onNodeWithText(text(R.string.server_title)).assertExists()
        compose.onNodeWithText(text(R.string.skip)).performClick()

        assertTrue(done)
        compose.onNodeWithText(text(R.string.tab_home)).assertExists()
    }

    @Test
    fun tabsSwitchScreensAndHomeIsLive() {
        compose.setContent { AppTheme { App(factory(scale), scale, onboardingDone = true, actions, "1.0-test") } }

        compose.waitUntilAtLeastOneExists(hasContentDescription(text(R.string.scale_connected)), 5_000)
        compose.onNodeWithContentDescription(text(R.string.tare)).assertIsEnabled()
        compose.onNodeWithText(text(R.string.mode_manual)).assertIsSelected()
        compose.onNodeWithText(text(R.string.mode_espresso)).assertIsEnabled()

        compose.onNodeWithText(text(R.string.tab_brews)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.brews_empty_title)), 5_000)

        compose.onNodeWithText(text(R.string.tab_settings)).performClick()
        compose.onNodeWithText(text(R.string.section_scale)).assertExists()
        compose.onNodeWithText(text(R.string.section_server)).assertExists()
        compose.onNodeWithText("1.0-test", substring = true).assertExists()

        compose.onNodeWithText(text(R.string.tab_home)).performClick()
        compose.onNodeWithContentDescription(text(R.string.timer_start)).performClick()
        compose.onNodeWithContentDescription(text(R.string.timer_pause)).assertExists()
    }

    @Test
    fun diagnosticsCaptureIsOptInAndRetainsFailedUploads() {
        settings.remoteDiagnostics = true
        val idle = FakeScaleSource().apply { state.value = ScaleState.Connected(battery = null) }
        compose.setContent { AppTheme { App(factory(idle), idle, onboardingDone = true, actions, "1.0-test") } }
        compose.waitUntilAtLeastOneExists(hasContentDescription(text(R.string.scale_connected)), 5_000)
        compose.onNodeWithText(text(R.string.tab_settings)).performClick()
        compose.onNodeWithText(text(R.string.section_diagnostics)).performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.capture_start)).performScrollTo().assertExists().assertIsEnabled().performClick()
        compose.waitUntil(5_000) { diagnostics.state.value is dev.mateux.caparao.data.diagnostics.CaptureWriterState.Recording }
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.capture_running)), 5_000)

        compose.onNodeWithText(text(R.string.capture_stop)).performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.capture_pending)), 5_000)
        compose.onNodeWithText(text(R.string.capture_start)).assertExists()
    }

    @Test
    fun espressoOpensTheDoseStepAndBackCloses() {
        // A connected scale that sends nothing: the replay would brew and save before Close on the JVM's fast clock.
        val idle = FakeScaleSource().apply { state.value = ScaleState.Connected(battery = null) }
        compose.setContent { AppTheme { App(factory(idle), idle, onboardingDone = true, actions, "1.0-test") } }
        compose.waitUntilAtLeastOneExists(hasContentDescription(text(R.string.scale_connected)), 5_000)

        compose.onNodeWithText(text(R.string.mode_espresso)).performClick()
        compose.onNodeWithText(text(R.string.ratio_caption)).assertExists()
        compose.onNodeWithText(text(R.string.next)).performClick()
        // Weigh the beans: the scale shows nothing yet, so Next keeps the planned dose.
        compose.onNodeWithTag("weigh-step-next").performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.prepare_portafilter_title)), 5_000)
        compose.onNodeWithTag("portafilter-ready").assertIsEnabled()
        // Closed before the cup went on: nothing started, so straight back home without a summary.
        compose.onNodeWithContentDescription(text(R.string.close_brew)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_home)), 5_000)
    }

    @Test
    fun systemBackReturnsFromBrewToTheTabs() {
        val idle = FakeScaleSource().apply { state.value = ScaleState.Connected(battery = null) }
        compose.setContent { AppTheme { App(factory(idle), idle, onboardingDone = true, actions, "1.0-test") } }
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.mode_espresso)), 5_000)
        compose.onNodeWithText(text(R.string.mode_espresso)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.ratio_caption)), 5_000)

        androidx.test.espresso.Espresso.pressBack()

        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_home)), 5_000)
        compose.onNodeWithText(text(R.string.ratio_caption)).assertDoesNotExist()
    }

    @Test
    fun aPausedEspressoReplayCanAdvanceFromWeighToLive() {
        val replay = ReplayScaleSource(
            scope,
            context.assets.open("sessions/synthetic-espresso.jsonl").bufferedReader().use { it.readLines() },
            startPaused = true,
        )
        compose.setContent { AppTheme { App(factory(replay), replay, onboardingDone = true, actions, "1.0-test") } }

        compose.waitUntilAtLeastOneExists(hasContentDescription(text(R.string.scale_connected)), 5_000)
        compose.onNodeWithText(text(R.string.mode_espresso)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.ratio_caption)), 5_000)
        compose.onNodeWithText(text(R.string.next)).performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
        compose.onNodeWithTag("weigh-step-next").performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.prepare_portafilter_title)), 5_000)
        compose.onNodeWithText(text(R.string.prepare_ready)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.espresso_place_cup)), 5_000)

        assertEquals(null, replay.reading.value)
        replay.resume()
        replay.stop()
    }

    @Test
    fun aSavedBrewOpensFromTheListAndDeleteRemovesIt() {
        val brew = Brew("b1", BrewMode.ESPRESSO, 1_000, 28_000, 18.0, 36.0, 4_000, 1.3, 2.0, interrupted = false)
        runBlocking { brews.save(brew, listOf(BrewSample(0, 0.0, 0.0), BrewSample(100, 0.4, 4.0))) }
        compose.setContent { AppTheme { App(factory(scale), scale, onboardingDone = true, actions, "1.0-test") } }

        compose.onNodeWithText(text(R.string.tab_brews)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("1:2.0"), 5_000)
        compose.onNodeWithText(text(R.string.local_only), substring = true).assertExists()
        compose.onNodeWithText("1:2.0").performClick()

        compose.waitUntilAtLeastOneExists(hasText(text(R.string.delete)), 5_000)
        compose.onNodeWithText(text(R.string.delete)).performClick()
        compose.onNodeWithText(text(R.string.delete_brew_title)).assertExists()
        // The dialog's Delete, after the top bar's.
        compose.onAllNodesWithText(text(R.string.delete)).onLast().performClick()

        // Back on the Brews tab it was opened from, now empty.
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.brews_empty_title)), 5_000)
        assertTrue(runBlocking { brews.get("b1") } == null)
    }

    @Test
    fun aClosedBrewDetailIsClearedAndReopeningShowsFreshState() {
        val brew = Brew("b1", BrewMode.ESPRESSO, 1_000, 28_000, 18.0, 36.0, 4_000, 1.3, 2.0, interrupted = false)
        runBlocking { brews.save(brew, listOf(BrewSample(0, 0.0, 0.0), BrewSample(100, 0.4, 4.0))) }
        val idle = FakeScaleSource().apply { state.value = ScaleState.Connected(battery = null) }
        compose.setContent { AppTheme { App(factory(idle), idle, onboardingDone = true, actions, "1.0-test") } }

        compose.onNodeWithText(text(R.string.tab_brews)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("1:2.0"), 5_000)
        compose.onNodeWithText("1:2.0").performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.edit_notes)), 5_000)
        // Edit the notes, then leave: the ViewModel must not outlive the screen with the form still open.
        compose.onNodeWithText(text(R.string.edit_notes)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.save)), 5_000)
        compose.onNodeWithContentDescription(text(R.string.close)).performClick()

        // Opened again: a fresh entry ViewModel, so the notes form is closed.
        compose.waitForIdle()
        compose.onNodeWithText("1:2.0").performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.edit_notes)), 5_000)
        compose.onNodeWithText(text(R.string.save)).assertDoesNotExist()
    }

    @Test
    fun aBrewOpenedFromACoffeeBagComesBackToTheSameBag() {
        runBlocking {
            coffeeBags.save(CoffeeBag(id = "bag1", name = "Ethiopia", weightGrams = 250.0, createdAt = 0))
            brews.save(
                Brew("b1", BrewMode.ESPRESSO, 1_000, 28_000, 18.0, 36.0, 4_000, 1.3, 2.0, interrupted = false, bagId = "bag1"),
                emptyList(),
            )
        }
        val idle = FakeScaleSource().apply { state.value = ScaleState.Connected(battery = null) }
        compose.setContent { AppTheme { App(factory(idle), idle, onboardingDone = true, actions, "1.0-test") } }

        compose.onNodeWithText(text(R.string.tab_coffees)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Ethiopia"), 5_000)
        compose.onNodeWithText("Ethiopia").performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.coffee_mark_finished)), 5_000)
        // A brew over the bag: the bag waits behind it and keeps its ViewModel.
        compose.onNode(hasText(text(R.string.mode_espresso), substring = true) and hasClickAction()).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.edit_notes)), 5_000)
        compose.onNodeWithContentDescription(text(R.string.close)).performClick()
        compose.onNodeWithText(text(R.string.coffee_mark_finished)).assertExists()

        compose.onNodeWithContentDescription(text(R.string.close)).performClick()
        compose.onNodeWithText(text(R.string.tab_coffees)).assertExists()
    }

    /**
     * A rotation disposes and rebuilds the content while the activity keeps its ViewModels; the restoration tester does
     * the same, so a brew in progress must come back on the same ViewModel, still listening to the scale.
     */
    @Test
    fun aStateRestorationKeepsTheRunningBrewAndItsViewModel() {
        val scale = FakeScaleSource().apply { state.value = ScaleState.Connected(battery = null) }
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val parentOwner = checkNotNull(LocalViewModelStoreOwner.current)
            val entryStores = remember(parentOwner) { ViewModelStoreProvider(parentOwner, "navigation-entries") }
            AppTheme {
                App(factory(scale, clock = { now }), scale, onboardingDone = true, actions, "1.0-test", entryStoreProvider = entryStores)
            }
        }

        compose.waitUntilAtLeastOneExists(hasText(text(R.string.mode_espresso)), 5_000)
        compose.onNodeWithText(text(R.string.mode_espresso)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.ratio_caption)), 5_000)
        compose.onNodeWithText(text(R.string.next)).performClick()
        compose.onNodeWithText(text(R.string.next)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.prepare_portafilter_title)), 5_000)
        compose.onNodeWithText(text(R.string.prepare_ready)).performClick()
        runEspressoToTheFirstDrops(scale)
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        // Still brewing on the same ViewModel, not a fresh dose step.
        compose.onNodeWithText(text(R.string.stop)).assertExists()

        // Still listening: a reading after the restore reaches the detector.
        pour(scale, 36.0, 36.5, 0.5)
        compose.onNodeWithText(text(R.string.stop)).assertExists()
    }

    /**
     * Rotation and process death restore the whole typed stack: a coffee bag with a saved brew's detail on top keeps
     * both entries and the detail's ViewModel state, and back walks the stack one entry at a time (#352).
     */
    @Test
    fun aStateRestorationKeepsAMultiEntryStackAndItsDetailViewModel() {
        runBlocking {
            coffeeBags.save(CoffeeBag(id = "bag1", name = "Ethiopia", weightGrams = 250.0, createdAt = 0))
            brews.save(
                Brew("b1", BrewMode.ESPRESSO, 1_000, 28_000, 18.0, 36.0, 4_000, 1.3, 2.0, interrupted = false, bagId = "bag1"),
                listOf(BrewSample(0, 0.0, 0.0), BrewSample(100, 0.4, 4.0)),
            )
        }
        val idle = FakeScaleSource().apply { state.value = ScaleState.Connected(battery = null) }
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val parentOwner = checkNotNull(LocalViewModelStoreOwner.current)
            val entryStores = remember(parentOwner) { ViewModelStoreProvider(parentOwner, "navigation-entries") }
            AppTheme {
                App(factory(idle), idle, onboardingDone = true, actions, "1.0-test", entryStoreProvider = entryStores)
            }
        }

        compose.onNodeWithText(text(R.string.tab_coffees)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Ethiopia"), 5_000)
        compose.onNodeWithText("Ethiopia").performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.coffee_mark_finished)), 5_000)
        compose.onNodeWithText("18.0", substring = true).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.edit_notes)), 5_000)
        // Leave the notes form open: it lives in the detail's entry ViewModel, so only a retained store restores it.
        compose.onNodeWithText(text(R.string.edit_notes)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.save)), 5_000)

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.save)).assertExists()
        // Back pops only the detail, revealing the bag it was opened from, then the tabs.
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.coffee_mark_finished)), 5_000)
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_coffees)), 5_000)
    }

    /** The riskiest typed key survives restore: [RecipeEditorViewModel.Target] is a nested sealed type (#352). */
    @Test
    fun aRecipeEditorSurvivesStateRestorationWithItsTypedKey() {
        val idle = FakeScaleSource().apply { state.value = ScaleState.Connected(battery = null) }
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val parentOwner = checkNotNull(LocalViewModelStoreOwner.current)
            val entryStores = remember(parentOwner) { ViewModelStoreProvider(parentOwner, "navigation-entries") }
            AppTheme {
                App(factory(idle), idle, onboardingDone = true, actions, "1.0-test", entryStoreProvider = entryStores)
            }
        }

        compose.onNodeWithText(text(R.string.tab_recipes)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.no_recipes_title)), 5_000)
        compose.onNodeWithText(text(R.string.mode_espresso)).performClick()
        compose.onNodeWithText(text(R.string.new_recipe), useUnmergedTree = true).performClick()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.recipe_name))).assertExists()

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        // Still the new-espresso editor, not the recipe list.
        compose.onNode(hasSetTextAction() and hasText(text(R.string.recipe_name))).assertExists()
        compose.onNodeWithText(text(R.string.no_recipes_title)).assertDoesNotExist()
    }

    @Test
    fun settingsRefusesPublicHttpAndSavesALocalServer() {
        compose.setContent { AppTheme { App(factory(scale), scale, onboardingDone = true, actions, "1.0-test") } }
        compose.onNodeWithText(text(R.string.tab_settings)).performClick()
        compose.onNodeWithText(text(R.string.section_server)).performClick()

        compose.onNode(hasSetTextAction() and hasText(text(R.string.server_url))).performScrollTo().performTextReplacement("http://coffee.example.com")
        compose.onNode(hasSetTextAction() and hasText(text(R.string.server_token))).performScrollTo().performTextReplacement("dev-token")
        compose.onNodeWithText(text(R.string.save)).performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.url_http_not_local)), 5_000)
        compose.onNodeWithText(text(R.string.save)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.test_connection)).performClick()
        compose.waitForIdle()
        assertTrue(checked.isEmpty())
        assertFalse(settings.serverConfigured)

        // A local name saves, with no warning.
        localServer = true
        compose.onNode(hasSetTextAction() and hasText(text(R.string.server_url))).performScrollTo().performTextReplacement("http://nas:8080")
        compose.onNodeWithText(text(R.string.url_http_not_local)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.save)).performScrollTo().performClick()
        compose.waitUntil(5_000) { settings.serverConfigured }
        assertEquals("http://nas:8080", settings.serverUrl)
    }

    @Test
    fun anEspressoShortcutOpensTheDoseAndIsConsumedOnce() {
        val idle = FakeScaleSource().apply { state.value = ScaleState.Connected(battery = null) }
        val shortcut = mutableStateOf<LauncherShortcut?>(LauncherShortcut.ESPRESSO)
        var consumed = 0
        compose.setContent {
            AppTheme {
                App(
                    factory(idle), idle, onboardingDone = true, actions, "1.0-test",
                    shortcut = shortcut.value,
                    onShortcutConsumed = { consumed++; shortcut.value = null },
                )
            }
        }

        // The dose step of a free brew, before anything is weighed or started.
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.ratio_caption)), 5_000)
        compose.onNodeWithText(text(R.string.next)).assertExists()
        assertEquals(1, consumed)

        // Closed: back on the tabs, and the consumed shortcut does not open the brew again.
        compose.onNodeWithContentDescription(text(R.string.close_brew)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_home)), 5_000)
        compose.onNodeWithText(text(R.string.ratio_caption)).assertDoesNotExist()
    }

    @Test
    fun aFilterShortcutWaitsForOnboardingAndThenOpensItsDose() {
        var done = false
        compose.setContent {
            AppTheme {
                App(
                    factory(scale) { done = true }, scale, onboardingDone = false, actions, "1.0-test",
                    shortcut = LauncherShortcut.FILTER,
                    onShortcutConsumed = {},
                )
            }
        }

        compose.onNodeWithText(text(R.string.welcome_title)).assertExists()
        compose.onNodeWithText(text(R.string.get_started)).performClick()
        compose.onNodeWithText(text(R.string.permissions_title)).assertExists()
        compose.onNodeWithText(text(R.string.allow_bluetooth)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.scale_connected)), 5_000)
        compose.onNodeWithText(text(R.string.next)).performClick()
        compose.onNodeWithText(text(R.string.server_title)).assertExists()
        compose.onNodeWithText(text(R.string.skip)).performClick()

        assertTrue(done)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.ratio_caption)), 5_000)
        compose.onNodeWithText(text(R.string.mode_filter)).assertExists()
    }

    @Test
    fun aFrenchPressShortcutOpensItsPreselectedRecipeAtTheDoseStep() {
        runBlocking { recipes.save(dev.mateux.caparao.data.local.BuiltInRecipes.frenchPress(0)) }
        val idle = FakeScaleSource().apply { state.value = ScaleState.Connected(battery = null) }
        val shortcut = mutableStateOf<LauncherShortcut?>(LauncherShortcut.FRENCH_PRESS)
        var consumed = 0
        compose.setContent {
            AppTheme {
                App(
                    factory(idle), idle, onboardingDone = true, actions, "1.0-test",
                    shortcut = shortcut.value,
                    onShortcutConsumed = { consumed++; shortcut.value = null },
                )
            }
        }

        // Room emits the recipe list before the view model applies its preselected recipe.
        val selectedFrenchPressRecipe = hasText("French press") and isSelectable() and isSelected()
        compose.waitUntilAtLeastOneExists(selectedFrenchPressRecipe, 5_000)
        compose.onNode(selectedFrenchPressRecipe).assertIsSelected()
        compose.onNodeWithText(text(R.string.next)).assertExists()
        assertEquals(1, consumed)
    }

    @Test
    fun aBrewsShortcutOpensTheSavedBrewsTab() {
        val shortcut = mutableStateOf<LauncherShortcut?>(LauncherShortcut.BREWS)
        compose.setContent {
            AppTheme {
                App(
                    factory(scale), scale, onboardingDone = true, actions, "1.0-test",
                    shortcut = shortcut.value,
                    onShortcutConsumed = { shortcut.value = null },
                )
            }
        }

        compose.waitUntilAtLeastOneExists(hasText(text(R.string.brews_empty_title)), 5_000)
        compose.onNodeWithText(text(R.string.tab_brews)).assertIsSelected()
    }

    @Test
    fun aHomeShortcutOpensTheHomeTabFromAnotherTab() {
        val shortcut = mutableStateOf<LauncherShortcut?>(null)
        var consumed = 0
        compose.setContent {
            AppTheme {
                App(
                    factory(scale), scale, onboardingDone = true, actions, "1.0-test",
                    shortcut = shortcut.value,
                    onShortcutConsumed = { consumed++; shortcut.value = null },
                )
            }
        }
        compose.onNodeWithText(text(R.string.tab_brews)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.brews_empty_title)), 5_000)

        shortcut.value = LauncherShortcut.WIDGET_HOME
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.mode_espresso)), 5_000)
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.tab_home)).assertIsSelected()
        assertEquals(1, consumed)
    }

    @Test
    fun aSuggestedModeShortcutDuringARunningBrewKeepsItAndItsReadings() {
        val scale = FakeScaleSource().apply { state.value = ScaleState.Connected(battery = null) }
        val shortcut = mutableStateOf<LauncherShortcut?>(null)
        compose.setContent {
            AppTheme {
                App(
                    factory(scale, clock = { now }), scale, onboardingDone = true, actions, "1.0-test",
                    shortcut = shortcut.value,
                    onShortcutConsumed = { shortcut.value = null },
                )
            }
        }
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.mode_espresso)), 5_000)
        compose.onNodeWithText(text(R.string.mode_espresso)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.ratio_caption)), 5_000)
        compose.onNodeWithText(text(R.string.next)).performClick()
        compose.onNodeWithText(text(R.string.next)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.prepare_portafilter_title)), 5_000)
        compose.onNodeWithText(text(R.string.prepare_ready)).performClick()
        runEspressoToTheFirstDrops(scale)

        // The widget suggests Filter after this Espresso, but a running brew is never interrupted.
        shortcut.value = LauncherShortcut.FILTER
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.tab_brews)).assertDoesNotExist()
        // The X keeps the stop dialog; Back in Live asks to restart instead (#287).
        compose.onNodeWithContentDescription(text(R.string.close_brew)).performClick()
        compose.onNodeWithText(text(R.string.stop_brew_title)).assertExists()
        compose.onNodeWithText(text(R.string.keep_brewing)).performClick()

        // The readings after the shortcut still reach the detector: the shot is saved and listed.
        hold(scale, 36.0, 6.0)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.brew_saved)), 5_000)
        // The curve on the Done screen pushes Done below the fold.
        // By action, not touch: the saved summary can still shift while the flow transition settles.
        compose.onNode(hasText(text(R.string.done)) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_brews)), 5_000)
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.tab_brews)).performClick()
        compose.waitUntilDoesNotExist(hasText(text(R.string.brews_empty_title)), 5_000)
    }

    /** A shot's Done screen with animations on: the numbers count up to their saved values and the curve is drawn (#198). */
    @Test
    fun theDoneScreenCountsTheShotUpAndDrawsItsCurve() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            val scale = FakeScaleSource().apply { state.value = ScaleState.Connected(battery = null) }
            compose.setContent { AppTheme { App(factory(scale, clock = { now }), scale, onboardingDone = true, actions, "1.0-test") } }
            compose.waitUntilAtLeastOneExists(hasText(text(R.string.mode_espresso)), 5_000)
            compose.onNodeWithText(text(R.string.mode_espresso)).performClick()
            compose.waitUntilAtLeastOneExists(hasText(text(R.string.ratio_caption)), 5_000)
            compose.onNodeWithText(text(R.string.next)).performClick()
            compose.onNodeWithText(text(R.string.next)).performClick()
            compose.waitUntilAtLeastOneExists(hasText(text(R.string.prepare_portafilter_title)), 5_000)
            compose.onNodeWithText(text(R.string.prepare_ready)).performClick()
            runEspressoToTheFirstDrops(scale)
            hold(scale, 36.0, 6.0)
            compose.waitUntilAtLeastOneExists(hasText(text(R.string.brew_saved)), 5_000)
            val yieldText = "${Format.grams(36.0, context.resources.configuration.locales[0])} ${text(R.string.unit_grams)}"
            compose.waitUntilAtLeastOneExists(hasText(yieldText), 5_000)
            compose.waitForIdle()
            compose.onNodeWithText(yieldText).assertExists()
            compose.onNodeWithTag("brew-curve").performScrollTo().assertExists()
        } finally {
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    @Test
    fun aShortcutForTheOpenModeKeepsTheWeighStepAndTheScaleListener() {
        val scale = FakeScaleSource().apply { state.value = ScaleState.Connected(battery = null) }
        val shortcut = mutableStateOf<LauncherShortcut?>(null)
        compose.setContent {
            AppTheme {
                App(
                    factory(scale, clock = { now }), scale, onboardingDone = true, actions, "1.0-test",
                    shortcut = shortcut.value,
                    onShortcutConsumed = { shortcut.value = null },
                )
            }
        }
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.mode_espresso)), 5_000)
        compose.onNodeWithText(text(R.string.mode_espresso)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.ratio_caption)), 5_000)
        compose.onNodeWithText(text(R.string.next)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.weigh_beans_title)), 5_000)

        shortcut.value = LauncherShortcut.ESPRESSO
        compose.waitForIdle()

        // Same screen and state, not reset to the dose.
        compose.onNodeWithText(text(R.string.weigh_beans_title)).assertExists()
        compose.onNodeWithText(text(R.string.ratio_caption)).assertDoesNotExist()

        // The same ViewModel still listens: the rest of the flow runs to the first drops.
        compose.onNodeWithText(text(R.string.next)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.prepare_portafilter_title)), 5_000)
        compose.onNodeWithText(text(R.string.prepare_ready)).performClick()
        runEspressoToTheFirstDrops(scale)
    }

    @Test
    fun aShortcutForTheOpenModeOnTheDoseKeepsItListening() {
        val scale = FakeScaleSource().apply { state.value = ScaleState.Connected(battery = null) }
        val shortcut = mutableStateOf<LauncherShortcut?>(null)
        compose.setContent {
            AppTheme {
                App(
                    factory(scale, clock = { now }), scale, onboardingDone = true, actions, "1.0-test",
                    shortcut = shortcut.value,
                    onShortcutConsumed = { shortcut.value = null },
                )
            }
        }
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.mode_espresso)), 5_000)
        compose.onNodeWithText(text(R.string.mode_espresso)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.ratio_caption)), 5_000)

        shortcut.value = LauncherShortcut.ESPRESSO
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.ratio_caption)).assertExists()

        // The ViewModel was not ended: the shot still runs from the dose to the first drops.
        compose.onNodeWithText(text(R.string.next)).performClick()
        compose.onNodeWithText(text(R.string.next)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.prepare_portafilter_title)), 5_000)
        compose.onNodeWithText(text(R.string.prepare_ready)).performClick()
        runEspressoToTheFirstDrops(scale)
    }

    @Test
    fun aShortcutForAnotherModeEndsTheDoseAndBrewsLeavesOneThatHasNotStarted() {
        val scale = FakeScaleSource().apply { state.value = ScaleState.Connected(battery = null) }
        val shortcut = mutableStateOf<LauncherShortcut?>(null)
        compose.setContent {
            AppTheme {
                App(
                    factory(scale, clock = { now }), scale, onboardingDone = true, actions, "1.0-test",
                    shortcut = shortcut.value,
                    onShortcutConsumed = { shortcut.value = null },
                )
            }
        }
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.mode_espresso)), 5_000)
        compose.onNodeWithText(text(R.string.mode_espresso)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.ratio_caption)), 5_000)

        shortcut.value = LauncherShortcut.FILTER
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.mode_filter)), 5_000)
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.ratio_caption)).assertExists()

        // Nothing had started, so Brews leaves the open Filter dose.
        shortcut.value = LauncherShortcut.BREWS
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.brews_empty_title)), 5_000)
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.tab_brews)).assertIsSelected()
    }

    @Test
    fun aRecipeCreatedInTheRecipesTabIsPickedForTheNextBrew() {
        val idle = FakeScaleSource().apply { state.value = ScaleState.Connected(battery = null) }
        compose.setContent { AppTheme { App(factory(idle), idle, onboardingDone = true, actions, "1.0-test") } }

        compose.onNodeWithText(text(R.string.tab_recipes)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.no_recipes_title)), 5_000)
        compose.onNodeWithText(text(R.string.mode_espresso)).performClick()
        compose.onNodeWithText(text(R.string.new_recipe), useUnmergedTree = true).performClick()
        compose.onNodeWithText(text(R.string.save)).performClick()
        compose.onAllNodesWithText(text(R.string.required)).onFirst().assertExists()

        compose.onNode(hasSetTextAction() and hasText(text(R.string.recipe_name))).performTextReplacement("Morning shot")
        compose.onNode(hasSetTextAction() and hasText(text(R.string.recipe_dose))).performTextReplacement("18")
        compose.onNode(hasSetTextAction() and hasText(text(R.string.recipe_ratio))).performTextReplacement("2,4")
        compose.onNodeWithText(text(R.string.save)).performClick()
        // Back on the list, which now shows it.
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_home)), 5_000)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Morning shot", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Morning shot", useUnmergedTree = true).assertExists()

        compose.onNodeWithText(text(R.string.tab_home)).performClick()
        compose.onNodeWithText(text(R.string.mode_espresso)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Morning shot"), 5_000)
        compose.onNode(hasText("Morning shot") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("18.0 g").assertExists()
    }
}
