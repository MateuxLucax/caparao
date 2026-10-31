package io.github.mateuxlucax.caparao.ui.screens

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mateuxlucax.caparao.ui.App
import io.github.mateuxlucax.caparao.ui.AppActions
import io.github.mateuxlucax.caparao.ui.FakeScaleSource
import io.github.mateuxlucax.caparao.model.ScaleState
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import io.github.mateuxlucax.caparao.R
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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import io.github.mateuxlucax.caparao.ui.home.HomeViewModel
import io.github.mateuxlucax.caparao.ui.onboarding.OnboardingViewModel
import io.github.mateuxlucax.caparao.ui.recipes.RecipeEditorViewModel
import io.github.mateuxlucax.caparao.ui.coffees.CoffeeBagsViewModel
import io.github.mateuxlucax.caparao.ui.recipes.RecipesViewModel
import io.github.mateuxlucax.caparao.ui.settings.ScaleStatusViewModel
import io.github.mateuxlucax.caparao.ui.settings.ServerViewModel
import io.github.mateuxlucax.caparao.ui.settings.TargetRatioViewModel
import io.github.mateuxlucax.caparao.ui.theme.AppTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.After
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
    private val settings = SettingsStore(context.getSharedPreferences("app-test", Context.MODE_PRIVATE))

    private fun factory(scale: ScaleSource, onboardingDone: () -> Unit = {}) = viewModelFactory {
        initializer { HomeViewModel(scale) }
        initializer { OnboardingViewModel(scale, onboardingDone) }
        initializer { ScaleStatusViewModel(scale) }
        initializer { TargetRatioViewModel(settings) }
        initializer { RecipesViewModel(recipes) }
        initializer { CoffeeBagsViewModel(CoffeeBagRepository(db.coffeeBags())) }
        initializer { RecipeEditorViewModel(requireNotNull(this[RecipeEditorViewModel.TARGET]), recipes) }
        initializer { BrewsViewModel(brews, settings.syncStatus) {} }
        // No server is reached: Test connection fails, saving enqueues nothing.
        initializer { ServerViewModel(settings, { _, _ -> error("no server in tests") }, {}, {}) }
        initializer { BrewViewModel(requireNotNull(this[BrewViewModel.MODE]), scale, brews, settings, recipes) }
        initializer { BrewDetailViewModel(requireNotNull(this[BrewDetailViewModel.BREW_ID]), brews) }
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
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
    fun espressoOpensTheDoseStepAndBackCloses() {
        // A connected scale that sends nothing: the replay would brew and save before Close on the JVM's fast clock.
        val idle = FakeScaleSource().apply { state.value = ScaleState.Connected(battery = null) }
        compose.setContent { AppTheme { App(factory(idle), idle, onboardingDone = true, actions, "1.0-test") } }
        compose.waitUntilAtLeastOneExists(hasContentDescription(text(R.string.scale_connected)), 5_000)

        compose.onNodeWithText(text(R.string.mode_espresso)).performClick()
        compose.onNodeWithText(text(R.string.ratio_caption)).assertExists()
        compose.onNodeWithText(text(R.string.next)).performClick()
        // Weigh the beans: the scale shows nothing yet, so Next keeps the planned dose.
        compose.onNodeWithText(text(R.string.next)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.espresso_place_cup)), 5_000)
        // Closed before the cup went on: nothing started, so straight back home without a summary.
        compose.onNodeWithContentDescription(text(R.string.close)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_home)), 5_000)
        compose.onNodeWithText(text(R.string.nothing_recorded)).assertDoesNotExist()
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
        compose.onNodeWithText("Morning shot").assertExists()

        compose.onNodeWithText(text(R.string.tab_home)).performClick()
        compose.onNodeWithText(text(R.string.mode_espresso)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Morning shot"), 5_000)
        compose.onNode(hasText("Morning shot") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("18.0 g").assertExists()
    }
}
