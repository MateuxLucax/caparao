package io.github.mateuxlucax.caparao.ui.screens

import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.mateuxlucax.caparao.BuildConfig
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.data.local.BrewDatabase
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.ui.App
import io.github.mateuxlucax.caparao.ui.AppActions
import io.github.mateuxlucax.caparao.ui.Tab
import io.github.mateuxlucax.caparao.ui.brew.BrewViewModel
import io.github.mateuxlucax.caparao.ui.brews.BrewDetailViewModel
import io.github.mateuxlucax.caparao.ui.brews.BrewsViewModel
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.home.HomeViewModel
import io.github.mateuxlucax.caparao.ui.onboarding.OnboardingViewModel
import io.github.mateuxlucax.caparao.ui.settings.ScaleStatusViewModel
import io.github.mateuxlucax.caparao.ui.settings.ServerViewModel
import io.github.mateuxlucax.caparao.ui.settings.TargetRatioViewModel
import io.github.mateuxlucax.caparao.ui.theme.AppTheme
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/**
 * The images in docs/screenshots, rendered on the JVM (Robolectric and Roborazzi) instead of an emulator: the
 * onboarding, every tab, an espresso, the espresso guide and a filter brew, in one language and theme per subclass.
 * The recorded sessions are played frame by frame ([SessionScale]), so every image comes from the same point of the
 * session on every run. Runs only from scripts/screenshots.sh (`-Pscreenshots`); a plain test run skips it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotsTest(private val lang: String, private val theme: String) {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val actions = AppActions(requestBluetoothPermissions = { true }, bluetoothPermissionGranted = { true }, enableBluetooth = {}, openAppSettings = {}, requestLocalNetwork = { true })
    private val db = Room.inMemoryDatabaseBuilder(context, BrewDatabase::class.java).allowMainThreadQueries().build()
    private val brews = BrewRepository(db.brews())
    private val settings = SettingsStore(context.getSharedPreferences("screenshots", Context.MODE_PRIVATE))
    private val locale get() = context.resources.configuration.locales[0]

    @Before
    fun setUp() {
        assumeTrue("screenshots run only from scripts/screenshots.sh", System.getProperty("roborazzi.test.record") == "true")
        // The animations' still frames (the full cup, the guide's pictures), as on an emulator with animations off.
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        // Brew dates and times read the same wherever this runs.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() = db.close()

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../docs/screenshots/$lang/$name-$theme.png", OPTIONS)
    }

    private fun text(id: Int) = context.getString(id)

    private fun click(id: Int) = compose.onNodeWithText(text(id)).performClick()

    private fun open(session: String, atMs: Long, onboardingDone: Boolean = true): SessionScale {
        val scale = SessionScale(context.assets.open("sessions/$session.jsonl").bufferedReader().use { it.readLines() })
        val factory = viewModelFactory {
            initializer { HomeViewModel(scale) }
            initializer { OnboardingViewModel(scale) {} }
            initializer { ScaleStatusViewModel(scale) }
            initializer { TargetRatioViewModel(settings) }
            initializer { BrewsViewModel(brews, settings.syncStatus) {} }
            initializer { ServerViewModel(settings, { _, _ -> error("no server in screenshots") }, {}, {}) }
            initializer {
                BrewViewModel(
                    requireNotNull(this[BrewViewModel.MODE]), scale, brews, settings,
                    clock = { scale.nowMs }, wallClock = { BREWED_AT + scale.nowMs },
                )
            }
            initializer { BrewDetailViewModel(requireNotNull(this[BrewDetailViewModel.BREW_ID]), brews) }
        }
        compose.setContent { AppTheme { App(factory, scale, onboardingDone, actions, BuildConfig.VERSION_NAME) } }
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

    @Test
    fun onboardingAndEveryTab() {
        open("press-ramp", 38_300, onboardingDone = false)
        capture("onboarding-welcome")
        click(R.string.get_started)
        capture("onboarding-permissions")
        click(R.string.skip)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.scale_connected)), 5_000)
        capture("onboarding-scale")
        click(R.string.next)
        capture("onboarding-server")
        click(R.string.skip)
        Tab.entries.forEach { tab ->
            click(tab.label)
            capture(tab.name.lowercase())
        }
    }

    /** An espresso from the synthetic session (cup already on the scale), then the list and the brew's chart. */
    @Test
    fun espresso() {
        val scale = open("synthetic-espresso", 3_000)
        click(R.string.mode_espresso)
        capture("brew-dose")
        compose.onNode(hasSetTextAction()).performTextReplacement("18")
        click(R.string.next)
        // The shot starts 14 s into the session and pours for about 25 s.
        play(scale, 21_000)
        capture("brew-live")
        playUntilShown(scale, R.string.brew_saved, 60_000)
        capture("brew-summary")
        click(R.string.close)
        click(R.string.tab_brews)
        capture("brews-list")
        compose.onNodeWithText(context.getString(R.string.ratio_value, Format.ratio(2.0, locale)), substring = true).performClick()
        capture("brew-detail")
    }

    /** The espresso guide's steps before the shot: cup, taring, waiting for the first drop. */
    @Test
    fun espressoGuide() {
        val scale = open("synthetic-espresso-guide", 0)
        click(R.string.mode_espresso)
        compose.onNode(hasSetTextAction()).performTextReplacement("18")
        click(R.string.next)
        capture("brew-step-cup")
        // The cup lands at 8 s and is tared as soon as it is still.
        playUntilShown(scale, R.string.taring, 15_000)
        capture("brew-step-taring")
        playUntilShown(scale, R.string.espresso_ready, 20_000)
        capture("brew-step-first-drop")
    }

    /** A filter brew: taring, pouring, brewing, then the server lifted and Done; its summary and chart. */
    @Test
    fun filter() {
        val scale = open("synthetic-filter-lift", 0)
        click(R.string.mode_filter)
        compose.onNode(hasSetTextAction()).performTextReplacement("15")
        click(R.string.next)
        playUntilShown(scale, R.string.taring, 2_000)
        capture("brew-filter-taring")
        playUntilShown(scale, R.string.filter_ready, 7_000)
        capture("brew-filter-pour")
        // Pours from 20 s, the last one ends at 88 s and everything leaves the scale at 90 s.
        play(scale, 60_000)
        capture("brew-filter-live")
        play(scale, 93_000)
        click(R.string.done)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.brew_saved)), 5_000)
        capture("brew-filter-summary")
        click(R.string.close)
        click(R.string.tab_brews)
        compose.onNodeWithText("${Format.grams(15.0, locale)} ${text(R.string.unit_grams)} →", substring = true).performClick()
        capture("brew-filter-detail")
    }

    private companion object {
        /** 12 October 2026, 08:30 UTC: the saved brews' date. */
        const val BREWED_AT = 1_791_793_800_000L

        /** A 411 dp phone at xxhdpi is 1233 px wide; about half keeps the repository small, as before. */
        val OPTIONS = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.45))
    }
}

/** A phone-sized screen at xxhdpi, as in [AppScreensTest]; the language and theme are the only differences between these. */
private const val SIZE = "w411dp-h891dp"

@Config(qualifiers = "en-$SIZE-notnight-xxhdpi")
class ScreenshotsEnLightTest : ScreenshotsTest("en", "light")

@Config(qualifiers = "en-$SIZE-night-xxhdpi")
class ScreenshotsEnDarkTest : ScreenshotsTest("en", "dark")

@Config(qualifiers = "pt-rBR-$SIZE-notnight-xxhdpi")
class ScreenshotsPtBrLightTest : ScreenshotsTest("pt-BR", "light")

@Config(qualifiers = "pt-rBR-$SIZE-night-xxhdpi")
class ScreenshotsPtBrDarkTest : ScreenshotsTest("pt-BR", "dark")
