package dev.mateux.caparao.ui

import dev.mateux.caparao.ui.components.AppActions

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.mateux.caparao.R
import dev.mateux.caparao.data.local.BrewDatabase
import dev.mateux.caparao.data.local.BrewRepository
import dev.mateux.caparao.data.local.CoffeeBagRepository
import dev.mateux.caparao.data.local.RecipeRepository
import dev.mateux.caparao.data.local.SettingsStore
import dev.mateux.caparao.data.scale.ReplayScaleSource
import dev.mateux.caparao.data.scale.ScaleSource
import dev.mateux.caparao.ui.brew.BrewViewModel
import dev.mateux.caparao.ui.brews.BrewsViewModel
import dev.mateux.caparao.ui.home.HomeViewModel
import dev.mateux.caparao.ui.onboarding.OnboardingViewModel
import dev.mateux.caparao.ui.recipes.RecipeEditorViewModel
import dev.mateux.caparao.ui.coffees.CoffeeBagDetailViewModel
import dev.mateux.caparao.ui.coffees.CoffeeBagsViewModel
import dev.mateux.caparao.ui.recipes.RecipesViewModel
import dev.mateux.caparao.ui.settings.ScaleStatusViewModel
import dev.mateux.caparao.ui.server.ServerViewModel
import dev.mateux.caparao.ui.settings.TargetRatioViewModel
import dev.mateux.caparao.ui.theme.AppTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The app on a device with a recorded session standing in for the scale: onboarding, then the tabs. */
@OptIn(ExperimentalTestApi::class)
class AppTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
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
        scale.start()
        initializer { HomeViewModel(scale) }
        initializer { OnboardingViewModel(scale, onboardingDone) }
        initializer { ScaleStatusViewModel(scale) }
        initializer { TargetRatioViewModel(settings) }
        initializer { RecipesViewModel(recipes) }
        initializer { CoffeeBagsViewModel(CoffeeBagRepository(db.coffeeBags())) }
        initializer { CoffeeBagDetailViewModel(requireNotNull(this[CoffeeBagDetailViewModel.BAG_ID]), CoffeeBagRepository(db.coffeeBags()), brews) }
        initializer { RecipeEditorViewModel(requireNotNull(this[RecipeEditorViewModel.TARGET]), recipes) }
        initializer { BrewsViewModel(brews, CoffeeBagRepository(db.coffeeBags()).bags, settings.syncStatus) {} }
        // No server is reached: Test connection fails, saving enqueues nothing.
        initializer { ServerViewModel(settings, { _, _ -> error("no server in tests") }, {}, {}) }
        initializer { BrewViewModel(requireNotNull(this[BrewViewModel.MODE]), scale, brews, settings, recipes) }
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
        compose.onNodeWithText(text(R.string.brews_empty_title)).assertExists()

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
        compose.setContent { AppTheme { App(factory(scale), scale, onboardingDone = true, actions, "1.0-test") } }
        compose.waitUntilAtLeastOneExists(hasContentDescription(text(R.string.scale_connected)), 5_000)

        compose.onNodeWithText(text(R.string.mode_espresso)).performClick()
        compose.onNodeWithText(text(R.string.ratio_caption)).assertExists()
        compose.onNodeWithText(text(R.string.next)).performClick()
        // Weigh the beans: the scale shows nothing yet, so Next keeps the planned dose.
        compose.onNodeWithText(text(R.string.next)).performClick()
        compose.onNodeWithText(text(R.string.prepare_portafilter_title)).assertExists()
        compose.onNodeWithText(text(R.string.prepare_ready)).assertExists()
        compose.onNodeWithContentDescription(text(R.string.close_brew)).performClick()
        compose.onNodeWithText(text(R.string.tab_home)).assertExists()
    }
}
