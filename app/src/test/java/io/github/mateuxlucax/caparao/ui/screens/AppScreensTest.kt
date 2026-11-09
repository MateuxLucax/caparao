package io.github.mateuxlucax.caparao.ui.screens

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mateuxlucax.caparao.ui.App
import io.github.mateuxlucax.caparao.ui.AppActions
import io.github.mateuxlucax.caparao.ui.DestinationStores
import io.github.mateuxlucax.caparao.ui.FakeScaleSource
import io.github.mateuxlucax.caparao.model.ScaleState
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.data.export.BrewExportFiles
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticRecorder
import io.github.mateuxlucax.caparao.data.local.BrewDatabase
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.CoffeeBagRepository
import io.github.mateuxlucax.caparao.data.local.RecipeRepository
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.scale.ReplayScaleSource
import io.github.mateuxlucax.caparao.data.scale.ScaleSource
import io.github.mateuxlucax.caparao.ui.brew.BrewViewModel
import io.github.mateuxlucax.caparao.ui.brews.BrewDetailViewModel
import io.github.mateuxlucax.caparao.ui.brews.BrewsViewModel
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewSample
import io.github.mateuxlucax.caparao.model.CoffeeBag
import io.github.mateuxlucax.caparao.model.ScaleReading
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import io.github.mateuxlucax.caparao.ui.home.HomeViewModel
import io.github.mateuxlucax.caparao.ui.launcher.LauncherShortcut
import io.github.mateuxlucax.caparao.ui.onboarding.OnboardingViewModel
import io.github.mateuxlucax.caparao.ui.recipes.RecipeEditorViewModel
import io.github.mateuxlucax.caparao.ui.coffees.CoffeeBagDetailViewModel
import io.github.mateuxlucax.caparao.ui.coffees.CoffeeBagsViewModel
import io.github.mateuxlucax.caparao.ui.recipes.RecipesViewModel
import io.github.mateuxlucax.caparao.ui.settings.ScaleStatusViewModel
import io.github.mateuxlucax.caparao.ui.settings.ServerViewModel
import io.github.mateuxlucax.caparao.ui.settings.ExportViewModel
import io.github.mateuxlucax.caparao.ui.settings.DiagnosticsViewModel
import io.github.mateuxlucax.caparao.ui.settings.TargetRatioViewModel
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.theme.AppTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
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
    private val diagnostics = DiagnosticRecorder(context.cacheDir.resolve("app-screen-tests-${System.nanoTime()}"), scope)

    /** The brew screen's clock, moved by [pour] and [hold] so a whole shot runs without waiting in real time. */
    private var now = 0L

    private fun factory(
        scale: ScaleSource,
        clock: () -> Long = SystemClock::elapsedRealtime,
        onboardingDone: () -> Unit = {},
    ) = viewModelFactory {
        initializer { HomeViewModel(scale) }
        initializer { OnboardingViewModel(scale, onboardingDone) }
        initializer { ScaleStatusViewModel(scale) }
        initializer { TargetRatioViewModel(settings) }
            initializer { ExportViewModel(BrewExportFiles(context, brews, recipes)) }
            initializer { DiagnosticsViewModel(context, diagnostics) }
        initializer { RecipesViewModel(recipes) }
        initializer { CoffeeBagsViewModel(coffeeBags) }
        initializer { CoffeeBagDetailViewModel(requireNotNull(this[CoffeeBagDetailViewModel.BAG_ID]), coffeeBags, brews) }
        initializer { RecipeEditorViewModel(requireNotNull(this[RecipeEditorViewModel.TARGET]), recipes) }
        initializer { BrewsViewModel(brews, settings.syncStatus) {} }
        // No server is reached: Test connection fails, saving enqueues nothing.
        initializer { ServerViewModel(settings, { _, _ -> error("no server in tests") }, {}, {}) }
        initializer { BrewViewModel(requireNotNull(this[BrewViewModel.MODE]), scale, brews, settings, recipes, clock = clock) }
        initializer { BrewDetailViewModel(requireNotNull(this[BrewDetailViewModel.BREW_ID]), brews) }
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
        compose.onNodeWithText(text(R.string.test_connection)).assertExists()
        compose.onNodeWithText("1.0-test", substring = true).assertExists()

        compose.onNodeWithText(text(R.string.tab_home)).performClick()
        compose.onNodeWithContentDescription(text(R.string.timer_start)).performClick()
        compose.onNodeWithContentDescription(text(R.string.timer_pause)).assertExists()
    }

    @Test
    fun diagnosticsCaptureRequiresStartAndCanBeSharedAfterStop() {
        val idle = FakeScaleSource().apply { state.value = ScaleState.Connected(battery = null) }
        compose.setContent { AppTheme { App(factory(idle), idle, onboardingDone = true, actions, "1.0-test") } }
        compose.waitUntilAtLeastOneExists(hasContentDescription(text(R.string.scale_connected)), 5_000)
        compose.onNodeWithText(text(R.string.tab_settings)).performClick()
        compose.onNodeWithText(text(R.string.section_diagnostics)).performScrollTo()
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.diagnostics_start)).performScrollTo().assertExists().assertIsEnabled().performClick()
        compose.waitUntil(5_000) { diagnostics.state.value is io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticRecorderState.Recording }
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.diagnostics_recording)), 5_000)
        compose.onNodeWithText(text(R.string.diagnostics_share)).assertDoesNotExist()

        compose.onNodeWithText(text(R.string.diagnostics_stop)).performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.diagnostics_ready)), 5_000)
        compose.onNodeWithText(text(R.string.diagnostics_start)).assertExists()
        compose.onNodeWithText(text(R.string.diagnostics_share)).assertExists()
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
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.cup_on)), 5_000)

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

    /** The activity's registry, read back as the tests' only way to see which destinations still hold ViewModels. */
    private fun destinationStores(owner: ViewModelStoreOwner) =
        owner.viewModelStore.get(DestinationStores.KEY) as DestinationStores

    @Test
    fun aClosedBrewDetailIsClearedAndReopeningShowsFreshState() {
        val brew = Brew("b1", BrewMode.ESPRESSO, 1_000, 28_000, 18.0, 36.0, 4_000, 1.3, 2.0, interrupted = false)
        runBlocking { brews.save(brew, listOf(BrewSample(0, 0.0, 0.0), BrewSample(100, 0.4, 4.0))) }
        val idle = FakeScaleSource().apply { state.value = ScaleState.Connected(battery = null) }
        lateinit var owner: ViewModelStoreOwner
        compose.setContent {
            owner = checkNotNull(LocalViewModelStoreOwner.current)
            AppTheme { App(factory(idle), idle, onboardingDone = true, actions, "1.0-test") }
        }

        compose.onNodeWithText(text(R.string.tab_brews)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("1:2.0"), 5_000)
        compose.onNodeWithText("1:2.0").performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.edit_notes)), 5_000)
        val stores = destinationStores(owner)
        assertTrue(stores.holds("detail-b1"))

        // Edit the notes, then leave: the ViewModel must not outlive the screen with the form still open.
        compose.onNodeWithText(text(R.string.edit_notes)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.save)), 5_000)
        compose.onNodeWithContentDescription(text(R.string.close)).performClick()
        compose.waitUntil(5_000) { !stores.holds("detail-b1") }

        // Opened again: a fresh ViewModel, so the notes form is closed.
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
        lateinit var owner: ViewModelStoreOwner
        compose.setContent {
            owner = checkNotNull(LocalViewModelStoreOwner.current)
            AppTheme { App(factory(idle), idle, onboardingDone = true, actions, "1.0-test") }
        }

        compose.onNodeWithText(text(R.string.tab_coffees)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Ethiopia"), 5_000)
        compose.onNodeWithText("Ethiopia").performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.coffee_mark_finished)), 5_000)
        val stores = destinationStores(owner)
        assertTrue(stores.holds("coffee-bag1"))

        // A brew over the bag: the bag waits behind it and keeps its ViewModel.
        compose.onNode(hasText(text(R.string.mode_espresso), substring = true) and hasClickAction()).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.edit_notes)), 5_000)
        assertTrue(stores.holds("coffee-bag1"))
        compose.onNodeWithContentDescription(text(R.string.close)).performClick()
        compose.waitUntil(5_000) { !stores.holds("detail-b1") }
        assertTrue(stores.holds("coffee-bag1"))
        compose.onNodeWithText(text(R.string.coffee_mark_finished)).assertExists()

        compose.onNodeWithContentDescription(text(R.string.close)).performClick()
        compose.waitUntil(5_000) { !stores.holds("coffee-bag1") }
    }

    /**
     * A rotation disposes and rebuilds the content while the activity keeps its ViewModels; the restoration tester does
     * the same, so a brew in progress must come back on the same ViewModel, still listening to the scale.
     */
    @Test
    fun aStateRestorationKeepsTheRunningBrewAndItsViewModel() {
        val scale = FakeScaleSource().apply { state.value = ScaleState.Connected(battery = null) }
        lateinit var owner: ViewModelStoreOwner
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            owner = checkNotNull(LocalViewModelStoreOwner.current)
            AppTheme { App(factory(scale, clock = { now }), scale, onboardingDone = true, actions, "1.0-test") }
        }

        compose.waitUntilAtLeastOneExists(hasText(text(R.string.mode_espresso)), 5_000)
        compose.onNodeWithText(text(R.string.mode_espresso)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.ratio_caption)), 5_000)
        compose.onNodeWithText(text(R.string.next)).performClick()
        compose.onNodeWithText(text(R.string.next)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.prepare_portafilter_title)), 5_000)
        compose.onNodeWithText(text(R.string.prepare_ready)).performClick()
        runEspressoToTheFirstDrops(scale)
        val key = "brew-${BrewMode.ESPRESSO.name}"
        val stores = destinationStores(owner)
        assertTrue(stores.holds(key))

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        // Still brewing on the same ViewModel, not a fresh dose step.
        compose.onNodeWithText(text(R.string.stop)).assertExists()
        assertTrue(stores.holds(key))

        // Still listening: a reading after the restore reaches the detector.
        pour(scale, 36.0, 36.5, 0.5)
        compose.onNodeWithText(text(R.string.stop)).assertExists()
    }

    @Test
    fun settingsWarnsAboutCleartextAndSavesTheServer() {
        compose.setContent { AppTheme { App(factory(scale), scale, onboardingDone = true, actions, "1.0-test") } }
        compose.onNodeWithText(text(R.string.tab_settings)).performClick()

        compose.onNode(hasSetTextAction() and hasText(text(R.string.server_url))).performScrollTo().performTextReplacement("http://coffee.example.com")
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.url_cleartext_warning)), 5_000)
        compose.onNode(hasSetTextAction() and hasText(text(R.string.server_token))).performScrollTo().performTextReplacement("dev-token")
        compose.onNodeWithText(text(R.string.save)).performScrollTo().performClick()

        compose.waitUntil(5_000) { settings.serverConfigured }
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
        runBlocking { recipes.save(io.github.mateuxlucax.caparao.data.local.BuiltInRecipes.frenchPress(0)) }
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
        // By action, not touch: the summary's layout can still shift as the notes load off the main thread.
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
        compose.onNodeWithText(text(R.string.save)).performScrollTo().performClick()
        compose.onAllNodesWithText(text(R.string.required)).onFirst().assertExists()

        compose.onNode(hasSetTextAction() and hasText(text(R.string.recipe_name))).performTextReplacement("Morning shot")
        compose.onNode(hasSetTextAction() and hasText(text(R.string.recipe_dose))).performTextReplacement("18")
        compose.onNode(hasSetTextAction() and hasText(text(R.string.recipe_ratio))).performTextReplacement("2,4")
        compose.onNodeWithText(text(R.string.save)).performScrollTo().performClick()
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
