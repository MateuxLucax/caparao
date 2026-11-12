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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
import io.github.mateuxlucax.caparao.data.diagnostics.CaptureWriter
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
import io.github.mateuxlucax.caparao.model.SyncError
import io.github.mateuxlucax.caparao.ui.format.message
import io.github.mateuxlucax.caparao.model.StepKind
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.ui.App
import io.github.mateuxlucax.caparao.ui.components.AppActions
import io.github.mateuxlucax.caparao.ui.Tab
import io.github.mateuxlucax.caparao.brew.StepState
import io.github.mateuxlucax.caparao.brew.Pace
import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.ui.components.BrewNumbers
import io.github.mateuxlucax.caparao.ui.brew.BrewStep
import io.github.mateuxlucax.caparao.ui.brew.BrewUiState
import io.github.mateuxlucax.caparao.ui.brew.BrewViewModel
import io.github.mateuxlucax.caparao.ui.brew.FlowChart
import io.github.mateuxlucax.caparao.ui.brew.FlowPoint
import io.github.mateuxlucax.caparao.ui.brew.GuidedStep
import io.github.mateuxlucax.caparao.ui.brews.BrewDetailViewModel
import io.github.mateuxlucax.caparao.ui.brews.BrewsViewModel
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.home.HomeViewModel
import io.github.mateuxlucax.caparao.ui.onboarding.OnboardingViewModel
import io.github.mateuxlucax.caparao.ui.settings.ScaleStatusViewModel
import io.github.mateuxlucax.caparao.ui.server.ServerViewModel
import io.github.mateuxlucax.caparao.ui.settings.CaptureViewModel
import io.github.mateuxlucax.caparao.ui.settings.TargetRatioViewModel
import io.github.mateuxlucax.caparao.ui.theme.AppTheme
import io.github.mateuxlucax.caparao.ui.components.ScaleReadout
import io.github.mateuxlucax.caparao.ui.components.readableOn
import io.github.mateuxlucax.caparao.ui.components.ringStroke
import io.github.mateuxlucax.caparao.brew.ratioBand
import io.github.mateuxlucax.caparao.brew.RatioBand
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

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenSettingsTests(lang: String, theme: String, compactOnly: Boolean = false) : ScreenTestSupport(lang, theme, compactOnly) {
    @Test
    fun settingsKeepsSimpleControlsOnTheList() {
        open("idle-5min", 0)
        click(R.string.tab_settings)
        compose.onNodeWithText(text(R.string.guide_vibration)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.guide_sound)).assertIsDisplayed()
        assertTrue(compose.onNodeWithText(text(R.string.guide_sound)).fetchSemanticsNode().boundsInRoot.top <
            compose.onNodeWithText(text(R.string.guide_vibration)).fetchSemanticsNode().boundsInRoot.top)
        assertTrue(compose.onNodeWithText(text(R.string.guide_vibration)).fetchSemanticsNode().boundsInRoot.top <
            compose.onNodeWithText(text(R.string.vibrate_on_both)).fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithText(text(R.string.server_url)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.capture_start)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.settings_only_phone)).assertExists()
        compose.onNodeWithText(context.getString(R.string.settings_about, BuildConfig.VERSION_NAME)).assertExists()
        compose.onAllNodesWithText(text(R.string.tab_settings)).assertCountEquals(2)
    }

    @Test
    fun connectedScaleOpensWithReconnect() {
        open("idle-5min", 0)
        click(R.string.tab_settings)
        click(R.string.section_scale)
        compose.onNodeWithText(text(R.string.scale_connected)).assertExists()
        compose.onNodeWithText(text(R.string.reconnect)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.allow_bluetooth)).assertDoesNotExist()
    }

    @Test
    fun vibrationChoiceHidesWhenVibrationIsOff() {
        open("idle-5min", 0)
        click(R.string.tab_settings)
        compose.onNodeWithText(text(R.string.vibrate_on_both)).assertExists()
        click(R.string.guide_vibration)
        compose.onNodeWithText(text(R.string.vibrate_on_both)).assertDoesNotExist()
        assertFalse(settings.guideVibration)
    }

    @Test
    fun serverOpensAsADetailAndBackReturnsToSettings() {
        open("idle-5min", 0)
        click(R.string.tab_settings)
        click(R.string.section_server)
        compose.onNodeWithText(text(R.string.server_detail_text)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.server_url)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        compose.onNodeWithText(text(R.string.guide_vibration)).assertIsDisplayed()
    }

    @Test
    fun serverRowShowsLastUploadError() {
        settings.saveServer("https://coffee.example.com", "dev-token")
        settings.setLastSyncError(SyncError(SyncError.Kind.UNAUTHORIZED, null), null)
        open("idle-5min", 0)
        click(R.string.tab_settings)
        compose.onNodeWithText(text(SyncError.Kind.UNAUTHORIZED.message)).assertExists()
    }

    @Test
    fun oldSyncStatusIncludesItsDateWithoutShowingCredentials() {
        val oldSync = 1_700_000_000_000L
        settings.saveServer("https://coffee.example.com", "dev-token")
        settings.lastSyncMs = oldSync
        open("idle-5min", 0)
        click(R.string.tab_settings)
        compose.onNodeWithText(context.getString(R.string.last_sync, Format.dateTime(oldSync, locale))).assertIsDisplayed()
        compose.onNodeWithText("https://coffee.example.com").assertDoesNotExist()
        compose.onNodeWithText("dev-token").assertDoesNotExist()
    }

    @Test
    fun revokedBluetoothPermissionAppearsInScaleDetail() {
        bluetoothGranted = false
        open("idle-5min", 0)
        click(R.string.tab_settings)
        compose.onNodeWithText(text(R.string.settings_allow_nearby)).assertExists()
        click(R.string.section_scale)
        compose.onNodeWithText(text(R.string.allow_bluetooth)).assertIsDisplayed()
    }

    @Test
    fun scaleFailureShowsItsTitleAndPairingHintInDetail() {
        val scale = open("idle-5min", 0)
        click(R.string.tab_settings)
        scale.state.value = ScaleState.Failed(ScaleError.PAIRED_ELSEWHERE, retrying = false)
        compose.onNodeWithText(text(R.string.error_paired_elsewhere)).assertExists()
        click(R.string.section_scale)
        compose.onNodeWithText(text(R.string.hint_paired_elsewhere)).assertIsDisplayed()
    }

    @Test
    fun diagnosticsRowShowsCaptureStatusAfterReturning() {
        openDiagnosticsCard()
        diagnosticsAction(R.string.capture_start)
        waitFor(R.string.capture_running)
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        compose.onNodeWithText(text(R.string.capture_running)).assertExists()
    }

    @Test
    fun openingDiagnosticsAgainDoesNotResendPendingCapture() {
        openDiagnosticsCard()
        diagnosticsAction(R.string.capture_start)
        waitFor(R.string.capture_running)
        diagnosticsAction(R.string.capture_stop)
        waitFor(R.string.capture_pending)
        assertEquals(1, diagnosticsSendAttempts.get())
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        click(R.string.section_diagnostics)
        compose.waitForIdle()
        assertEquals(1, diagnosticsSendAttempts.get())
    }

    @Test
    fun diagnosticsOptInControlsCaptureSection() {
        openDiagnosticsCard(optedIn = false)
        compose.onNodeWithTag("diagnostics-opt-in").performScrollTo().assert(isOn().not())
        compose.onNodeWithText(text(R.string.capture_start)).assertDoesNotExist()

        compose.onNodeWithTag("diagnostics-opt-in").performClick()
        compose.waitUntil(5_000) { settings.remoteDiagnostics }
        waitFor(R.string.capture_start)
        compose.onNodeWithTag("diagnostics-opt-in").assert(isOn())

        compose.onNodeWithTag("diagnostics-opt-in").performClick()
        compose.onNodeWithText(text(R.string.capture_start)).assertDoesNotExist()
    }

    @Test
    fun diagnosticsSectionIsHiddenWhenUnavailable() {
        openDiagnosticsCard(optedIn = true, available = false)
        compose.onNodeWithText(text(R.string.section_diagnostics)).assertDoesNotExist()
    }

    @Test
    fun diagnosticsCopyNamesUploadedData() {
        openDiagnosticsCard(optedIn = false)
        compose.onNodeWithText(text(R.string.diagnostics_text)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.diagnostics_off_text)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.diagnostics_on_text)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.section_diagnostics)).assertIsDisplayed()
        compose.onNodeWithTag("diagnostics-opt-in").performClick()
        waitFor(R.string.capture_start)
        compose.onNodeWithText(text(R.string.diagnostics_on_text)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.diagnostics_off_text)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.capture_start)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun diagnosticsIdle() {
        openDiagnosticsCard()
        compose.onNodeWithText(text(R.string.capture_start)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun diagnosticsRecording() {
        openDiagnosticsCard()
        diagnosticsAction(R.string.capture_start)
        waitFor(R.string.capture_running)
        compose.onNodeWithText(context.getString(R.string.capture_elapsed, 0, 0)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.capture_stop)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun diagnosticsPendingUpload() {
        openDiagnosticsCard()
        diagnosticsAction(R.string.capture_start)
        waitFor(R.string.capture_running)
        diagnosticsAction(R.string.capture_stop)
        waitFor(R.string.capture_pending)
        compose.onNodeWithText(text(R.string.capture_start)).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun diagnosticsSent() {
        diagnosticsSendSucceeds = true
        openDiagnosticsCard()
        diagnosticsAction(R.string.capture_start)
        waitFor(R.string.capture_running)
        diagnosticsAction(R.string.capture_stop)
        waitFor(R.string.capture_sent)
    }

    @Test
    fun diagnosticsIncomplete() {
        openDiagnosticsCard()
        diagnosticsAction(R.string.capture_start)
        waitFor(R.string.capture_running)
        diagnostics.recordDisconnect()
        waitFor(R.string.diagnostics_incomplete)
        compose.onNodeWithText(text(R.string.diagnostics_reason_disconnected)).assertIsDisplayed()
    }

    @Test
    fun serverRefusesPublicHttp() {
        localServer = false
        open("idle-5min", 0)
        click(R.string.tab_settings)
        click(R.string.section_server)
        serverField(R.string.server_url, "http://coffee.example.com")
        serverField(R.string.server_token, "dev-token")
        compose.onNodeWithText(text(R.string.save)).performScrollTo().performClick()
        waitFor(R.string.url_http_not_local)
        compose.onNodeWithText(text(R.string.save)).assertIsNotEnabled()
        assertFalse(settings.serverConfigured)

        compose.onNodeWithText(text(R.string.test_connection)).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue("the token is never sent to a refused http server", serverChecks.isEmpty())
        compose.onNodeWithText(text(R.string.url_http_not_local)).assertExists()

        // Editing the URL clears the error.
        serverField(R.string.server_url, "https://coffee.example.com")
        compose.onNodeWithText(text(R.string.url_http_not_local)).assertDoesNotExist()
    }

    /** The same refusal on the onboarding server step, before the first save. */
    @Test
    fun onboardingRefusesPublicHttp() {
        localServer = false
        open("press-ramp", 38_300, onboardingDone = false)
        waitFor(R.string.welcome_title)
        click(R.string.get_started)
        click(R.string.skip)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.scale_connected)), 5_000)
        click(R.string.next)
        waitFor(R.string.server_title)
        serverField(R.string.server_url, "http://coffee.example.com")
        serverField(R.string.server_token, "dev-token")
        compose.onNodeWithText(text(R.string.finish)).performClick()
        waitFor(R.string.url_http_not_local)
        compose.onNodeWithText(text(R.string.finish)).assertIsNotEnabled()
        assertFalse(settings.serverConfigured)
        assertTrue("the token is never sent to a refused http server", serverChecks.isEmpty())
    }

    /** H2: the Save button shows Checking… and is disabled until the host resolves. */
    @Test
    fun serverShowsCheckingWhileTheHostResolves() {
        localServer = true
        localServerGate = CompletableDeferred()
        open("idle-5min", 0)
        click(R.string.tab_settings)
        click(R.string.section_server)
        serverField(R.string.server_url, "http://nas:8080")
        serverField(R.string.server_token, "dev-token")
        compose.onNodeWithText(text(R.string.save)).performScrollTo().performClick()
        waitFor(R.string.server_checking)
        compose.onNodeWithText(text(R.string.server_checking)).assertIsNotEnabled()

        localServerGate!!.complete(Unit)
        compose.waitUntil(5_000) { settings.serverConfigured }
    }

    /** H3: plain http to a local host saves, with no warning. */
    @Test
    fun serverSavesLocalHttpWithoutAWarning() {
        localServer = true
        open("idle-5min", 0)
        click(R.string.tab_settings)
        click(R.string.section_server)
        serverField(R.string.server_url, "http://nas.local:8080")
        serverField(R.string.server_token, "dev-token")
        compose.onNodeWithText(text(R.string.save)).performScrollTo().performClick()
        compose.waitUntil(5_000) { settings.serverConfigured }
        assertEquals("http://nas.local:8080", settings.serverUrl)
        compose.onNodeWithText(text(R.string.url_http_not_local)).assertDoesNotExist()
    }

    @Test
    fun settingsHasNoColorsOption() {
        open("idle-5min", 0)
        click(R.string.tab_settings)
        val removedColorLabels = if (lang == "pt-BR") listOf("Cores", "Sistema") else listOf("Colors", "System")
        removedColorLabels.forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }

    /** An espresso from the synthetic session (cup already on the scale), then the list and the brew's chart. */
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class SettingsEnLightTest : ScreenSettingsTests("en", "light")

@Config(qualifiers = "en-w411dp-h891dp-night-xxhdpi")
class SettingsEnDarkTest : ScreenSettingsTests("en", "dark")

@Config(qualifiers = "pt-rBR-w411dp-h891dp-notnight-xxhdpi")
class SettingsPtBrLightTest : ScreenSettingsTests("pt-BR", "light")

@Config(qualifiers = "pt-rBR-w411dp-h891dp-night-xxhdpi")
class SettingsPtBrDarkTest : ScreenSettingsTests("pt-BR", "dark")

@Config(qualifiers = "en-w320dp-h640dp-notnight-xxhdpi")
class SettingsSmallEnLightTest : ScreenSettingsTests("en", "light", compactOnly = true)

@Config(qualifiers = "en-w320dp-h640dp-night-xxhdpi")
class SettingsSmallEnDarkTest : ScreenSettingsTests("en", "dark", compactOnly = true)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-notnight-xxhdpi")
class SettingsSmallPtBrLightTest : ScreenSettingsTests("pt-BR", "light", compactOnly = true)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-night-xxhdpi")
class SettingsSmallPtBrDarkTest : ScreenSettingsTests("pt-BR", "dark", compactOnly = true)
