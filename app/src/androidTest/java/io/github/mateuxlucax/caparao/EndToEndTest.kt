package io.github.mateuxlucax.caparao

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.printToLog
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mateuxlucax.caparao.data.local.BrewDatabase
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.scale.ReplayScaleSource
import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.brew.StepEvent
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.ui.DestinationStores
import io.github.mateuxlucax.caparao.ui.brew.BrewStep
import io.github.mateuxlucax.caparao.ui.brew.BrewUiState
import io.github.mateuxlucax.caparao.ui.brew.BrewViewModel
import io.github.mateuxlucax.caparao.ui.brew.GuideAlert
import io.github.mateuxlucax.caparao.ui.launcher.LauncherShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Scale to server on the emulator: the synthetic espresso is replayed as the scale, recorded by the brew screen,
 * saved, and uploaded by WorkManager to the server given in the `server_url` and `token` arguments. Run by
 * scripts/e2e.sh, which starts the pinned exporter and then checks what it received; skipped otherwise.
 */
@OptIn(ExperimentalTestApi::class)
class EndToEndTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val arguments = InstrumentationRegistry.getArguments()

    private fun text(id: Int) = context.getString(id)

    /** The brew screen's ViewModel, which lives in its destination store since #310. */
    private fun MainActivity.brewViewModel(mode: String): BrewViewModel? =
        (viewModelStore.get(DestinationStores.KEY) as? DestinationStores)
            ?.viewModels("brew-$mode")
            ?.get("brew-$mode") as? BrewViewModel

    @Test
    fun anEspressoReachesTheServer() {
        val serverUrl = arguments.getString("server_url")
        val token = arguments.getString("token")
        assumeTrue("run by scripts/e2e.sh", serverUrl != null && token != null)

        val intent = Intent(context, MainActivity::class.java)
            .putExtra(DebugScaleHooks.EXTRA_REPLAY, "synthetic-espresso")
            .putExtra(DebugScaleHooks.EXTRA_REPLAY_PAUSED, true)
            .putExtra(DebugScaleHooks.EXTRA_REPLAY_SPEED, 8.0)
            .putExtra(DebugScaleHooks.EXTRA_REPLAY_REPEAT, false)
        prepareServer(serverUrl!!, token!!)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            val replay = (context.applicationContext as CaparaoApplication).container.scale as ReplayScaleSource
            fun brewState(): BrewUiState? {
                val state = AtomicReference<BrewUiState?>()
                scenario.onActivity { activity ->
                    state.set(activity.brewViewModel("ESPRESSO")?.uiState?.value)
                }
                return state.get()
            }
            fun semanticsSnapshot(): String {
                val markers = listOf(
                    R.string.ratio_caption,
                    R.string.weigh_beans_title,
                    R.string.prepare_portafilter_title,
                    R.string.prepare_ready,
                    R.string.tare,
                    R.string.cup_on,
                    R.string.brew_saved,
                ).filter { compose.onAllNodes(hasText(text(it))).fetchSemanticsNodes().isNotEmpty() }
                    .map { text(it) }
                val weighNext = compose.onAllNodes(hasTestTag("weigh-step-next")).fetchSemanticsNodes()
                return "visible=$markers, weighNextCount=${weighNext.size}, weighNextConfig=${weighNext.joinToString { it.config.toString() }}"
            }
            compose.waitUntilAtLeastOneExists(hasContentDescription(text(R.string.scale_connected)), 10_000)
            compose.onNodeWithText(text(R.string.mode_espresso)).performClick()
            compose.waitUntilAtLeastOneExists(hasText(text(R.string.free_brew)), 10_000)
            compose.onNodeWithText(text(R.string.free_brew)).performClick()
            compose.onNodeWithText(text(R.string.next)).performClick()
            compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 10_000)
            // The replay has not started, so Next keeps the planned 18 g dose.
            val beforeNext = brewState()
            val beforeSemantics = semanticsSnapshot()
            compose.onRoot(useUnmergedTree = true).printToLog("E2E-before-weigh-next")
            val weighNext = compose.onNodeWithTag("weigh-step-next").assertIsEnabled()
            val weighNextBounds = weighNext.fetchSemanticsNode().boundsInRoot
            assertTrue(
                "Weigh Next needs a visible hit target, got $weighNextBounds",
                weighNextBounds.width > 0f && weighNextBounds.height > 0f,
            )
            weighNext.assertIsDisplayed().performClick()
            val afterNext = brewState()
            val afterSemantics = semanticsSnapshot()
            compose.onRoot(useUnmergedTree = true).printToLog("E2E-after-weigh-next")
            compose.waitForIdle()
            val afterIdle = brewState()
            compose.onRoot(useUnmergedTree = true).printToLog("E2E-after-weigh-next-idle")
            if (afterIdle?.step != BrewStep.PREPARE_PORTAFILTER) {
                throw AssertionError(
                    "Weigh Next did not enter portafilter preparation: before=${describe(beforeNext)} ($beforeSemantics), " +
                        "afterClick=${describe(afterNext)} ($afterSemantics), " +
                        "afterIdle=${describe(afterIdle)} (${semanticsSnapshot()})",
                )
            }
            compose.waitUntilAtLeastOneExists(hasText(text(R.string.prepare_portafilter_title)), 10_000)
            val readyButton = compose.onNodeWithTag("portafilter-ready").assertIsEnabled().assertIsDisplayed()
            readyButton.performClick()
            compose.waitUntil(10_000) { brewState()?.step == BrewStep.LIVE }
            val dao = (context.applicationContext as CaparaoApplication).container.database.brews()
            // Don't start the 52 s fixture until the live screen and detector are mounted.
            val liveStepError = runCatching {
                compose.waitUntilAtLeastOneExists(hasText(text(R.string.cup_on)), 10_000)
            }.exceptionOrNull()
            if (liveStepError != null) {
                val visibleSteps = listOf(R.string.ratio_caption, R.string.weigh_beans_title, R.string.tare, R.string.cup_on)
                    .filter { compose.onAllNodes(hasText(text(it))).fetchSemanticsNodes().isNotEmpty() }
                    .map { text(it) }
                throw AssertionError(
                    "The live step did not appear before replay: visible=$visibleSteps, " +
                        "scale=${replay.state.value}, reading=${replay.reading.value}",
                    liveStepError,
                )
            }
            replay.resume()
            val savedError = runCatching {
                compose.waitUntilAtLeastOneExists(hasText(text(R.string.brew_saved)), 60_000)
            }.exceptionOrNull()
            if (savedError != null) {
                val savedCount = runBlocking(Dispatchers.IO) { dao.all().size }
                throw AssertionError(
                    "The replay did not reach the summary: scale=${replay.state.value}, reading=${replay.reading.value}, storedBrews=$savedCount",
                    savedError,
                )
            }

            compose.waitUntil(10_000) { runBlocking(Dispatchers.IO) { dao.all().size == 1 } }
            val (brew, sampleCount) = runBlocking(Dispatchers.IO) {
                val saved = dao.all().single()
                saved to dao.samples(saved.id).size
            }
            assertEquals(18.0, brew.doseGrams, 1e-9)
            assertEquals(36.0, brew.yieldGrams, 0.5)
            assertTrue("expected a full curve, got $sampleCount samples", sampleCount > 100)
            assertFalse("the replayed shot should finish normally", brew.interrupted)

            compose.onNode(hasText(text(R.string.done)) and hasClickAction()).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.tab_brews)).performClick()
            // "Synced 16:52": the server confirmed it.
            val synced = text(R.string.synced_at).substringBefore("%1\$s").trim()
            compose.waitUntilAtLeastOneExists(hasText(synced, substring = true), 60_000)
        }
    }

    @Test
    fun aFrenchPressShortcutStaysOpenThroughTheSteepAndSavesAfterDone() {
        val serverUrl = arguments.getString("server_url")
        val token = arguments.getString("token")
        assumeTrue("run by scripts/e2e.sh", serverUrl != null && token != null)
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .setAction(LauncherShortcut.FRENCH_PRESS.action)
            .putExtra(DebugScaleHooks.EXTRA_REPLAY, "synthetic-french-press")
            .putExtra(DebugScaleHooks.EXTRA_REPLAY_PAUSED, true)
            .putExtra(DebugScaleHooks.EXTRA_REPLAY_SPEED, 8.0)
            .putExtra(DebugScaleHooks.EXTRA_REPLAY_REPEAT, false)
        prepareServer(serverUrl!!, token!!)
        val activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent) as MainActivity
        val container = (context.applicationContext as CaparaoApplication).container
        val replay = container.scale as ReplayScaleSource
        val alerts = CopyOnWriteArrayList<GuideAlert>()
        val alertScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        val viewModel = AtomicReference<BrewViewModel?>()
        try {
            // The shortcut opens the built-in French press recipe, whose water is the control (no ratio caption).
            compose.waitUntilAtLeastOneExists(hasText("French press") and isSelectable(), 10_000)
            val brewViewModel = activity.brewViewModel("FRENCH_PRESS")
            viewModel.set(brewViewModel)
            if (brewViewModel != null) alertScope.launch { brewViewModel.alerts.collect(alerts::add) }
            assertNotNull("French press view model is created with the dose screen", viewModel.get())
            compose.onNodeWithText(text(R.string.next)).performClick()
            compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 10_000)
            compose.onNodeWithTag("weigh-step-next").assertIsEnabled().performClick()
            // Prepare waits for Ready; only then does the tare go out and the live step start.
            compose.waitUntilAtLeastOneExists(hasText(text(R.string.prepare_press_title)), 10_000)
            assertEquals(BrewStep.PREPARE_PORTAFILTER, viewModel.get()?.uiState?.value?.step)
            compose.onNodeWithTag("press-prepare-ready").assertIsEnabled().performClick()
            compose.waitUntil(10_000) { viewModel.get()?.uiState?.value?.step == BrewStep.LIVE }

            replay.resume()
            val removalGuideError = runCatching {
                compose.waitUntilAtLeastOneExists(hasText(text(R.string.guide_remove_press)), 60_000)
            }.exceptionOrNull()
            if (removalGuideError != null) {
                val state = viewModel.get()?.uiState?.value
                throw AssertionError(
                    "French press removal guide did not appear: replayMs=${replay.nowMs}, " +
                        "scale=${replay.state.value}, reading=${replay.reading.value}, " +
                        "step=${state?.step}, phase=${state?.phase}, elapsedMs=${state?.elapsedMs}, " +
                        "recipe=${state?.recipe?.name}, recipeSteps=${state?.recipe?.steps}, " +
                        "guide=${state?.guide}, saved=${state?.saved != null}",
                    removalGuideError,
                )
            }
            assertEquals(BrewPhase.BREWING, viewModel.get()?.uiState?.value?.phase)
            assertEquals(null, viewModel.get()?.uiState?.value?.saved)
            compose.waitUntilAtLeastOneExists(hasText(text(R.string.guide_press_serve)), 60_000)
            compose.waitUntil(10_000) { viewModel.get()?.uiState?.value?.frenchPressRemoved == true }
            // The synthetic lift is still present ten seconds after removal; neither timer nor lift finishes it.
            compose.waitUntil(10_000) { replay.nowMs >= 251_200L }
            assertEquals(BrewStep.LIVE, viewModel.get()?.uiState?.value?.step)
            assertEquals(BrewPhase.BREWING, viewModel.get()?.uiState?.value?.phase)
            assertEquals(300.0, viewModel.get()?.uiState?.value?.weightGrams ?: 0.0, 0.5)
            val finishAlerts = alerts.filter { it.event == StepEvent.FrenchPressDeadline }
            assertEquals(1, finishAlerts.size)
            assertTrue(finishAlerts.single().vibrate)
            assertFalse(finishAlerts.single().sound)
            assertEquals(null, viewModel.get()?.uiState?.value?.saved)

            // The progress bar also reads "Done", so target the button.
            compose.onNode(hasText(text(R.string.done)) and hasClickAction()).performClick()
            compose.waitUntilAtLeastOneExists(hasText(text(R.string.brew_saved)), 10_000)
            val dao = container.database.brews()
            compose.waitUntil(10_000) { runBlocking(Dispatchers.IO) { dao.all().size == 1 } }
            val (brew, samples) = runBlocking(Dispatchers.IO) {
                val entity = requireNotNull(dao.all().singleOrNull())
                container.brews.get(entity.id)!! to dao.samples(entity.id)
            }
            assertEquals(BrewMode.FRENCH_PRESS, brew.mode)
            assertEquals(300.0, brew.yieldGrams, 0.5)
            assertEquals(null, brew.timeToFirstDropMs)
            assertFalse(brew.interrupted)
            assertTrue("the lifted carafe is excluded from the curve", samples.all { it.weightGrams > 0.0 })
            assertEquals(300.0, samples.last().weightGrams, 0.5)

            compose.onNode(hasText(text(R.string.done)) and hasClickAction()).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.tab_brews)).performClick()
            // Both mode uploads must reach the server in the script's assertion below.
            val synced = text(R.string.synced_at).substringBefore("%1\$s").trim()
            compose.waitUntilAtLeastOneExists(hasText(synced, substring = true), 60_000)
        } finally {
            alertScope.cancel()
            InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.finishAndRemoveTask() }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }

    /** Reset the persistent Room rows and server settings before each replay journey. */
    private fun prepareServer(serverUrl: String, token: String) {
        val preferences = SettingsStore.open(context)
        preferences.edit().clear().commit()
        val settings = SettingsStore(preferences)
        settings.onboardingDone = true
        settings.saveServer(serverUrl, token)
        val database = BrewDatabase.open(context)
        runBlocking(Dispatchers.IO) {
            database.brews().all().forEach { database.brews().delete(it.id) }
        }
        database.close()
    }

    private fun describe(state: BrewUiState?): String = state?.let {
        "step=${it.step}, phase=${it.phase}, dose=${it.dose}, connected=${it.connected}, " +
            "weight=${it.weightGrams}, saved=${it.saved != null}"
    } ?: "missing"
}
