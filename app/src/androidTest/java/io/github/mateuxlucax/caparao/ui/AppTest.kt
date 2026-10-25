package io.github.mateuxlucax.caparao.ui

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
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
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.data.local.BrewDatabase
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.scale.ReplayScaleSource
import io.github.mateuxlucax.caparao.data.scale.ScaleSource
import io.github.mateuxlucax.caparao.ui.brew.BrewViewModel
import io.github.mateuxlucax.caparao.ui.brews.BrewsViewModel
import io.github.mateuxlucax.caparao.ui.home.HomeViewModel
import io.github.mateuxlucax.caparao.ui.onboarding.OnboardingViewModel
import io.github.mateuxlucax.caparao.ui.settings.ServerViewModel
import io.github.mateuxlucax.caparao.ui.theme.AppTheme
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
    private val actions = AppActions(requestBluetoothPermissions = { true }, enableBluetooth = {}, openAppSettings = {}, requestLocalNetwork = { true })

    /** The press-ramp recording from the moment there is weight on the scale. */
    private val scale = ReplayScaleSource(
        scope,
        context.assets.open("sessions/press-ramp.jsonl").bufferedReader().use { it.readLines() },
        startAtMs = 38_300,
    )

    private fun text(id: Int) = context.getString(id)

    private val db = Room.inMemoryDatabaseBuilder(context, BrewDatabase::class.java).build()
    private val brews = BrewRepository(db.brews())
    private val settings = SettingsStore(context.getSharedPreferences("app-test", Context.MODE_PRIVATE))

    private fun factory(scale: ScaleSource, onboardingDone: () -> Unit = {}) = viewModelFactory {
        initializer { HomeViewModel(scale) }
        initializer { OnboardingViewModel(scale, onboardingDone) }
        initializer { BrewsViewModel(brews, settings.syncStatus) {} }
        // No server is reached: Test connection fails, saving enqueues nothing.
        initializer { ServerViewModel(settings, { _, _ -> error("no server in tests") }, {}, {}) }
        initializer { BrewViewModel(requireNotNull(this[BrewViewModel.MODE]), scale, brews, settings) }
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

        compose.waitUntilAtLeastOneExists(hasText(text(R.string.scale_connected)), 5_000)
        compose.onNodeWithContentDescription(text(R.string.tare)).assertIsEnabled()
        compose.onNodeWithText(text(R.string.mode_espresso)).assertExists()

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
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.scale_connected)), 5_000)

        compose.onNodeWithText(text(R.string.mode_espresso)).performClick()
        compose.onNodeWithText(text(R.string.dose_title)).assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("18")
        compose.onNodeWithText(text(R.string.next)).performClick()
        compose.onNodeWithText(text(R.string.espresso_place_cup)).assertExists()
        compose.onNodeWithContentDescription(text(R.string.close)).performClick()
        compose.onNodeWithText(text(R.string.nothing_recorded)).assertExists()
        compose.onNodeWithText(text(R.string.close)).performClick()
        compose.onNodeWithText(text(R.string.tab_home)).assertExists()
    }
}
