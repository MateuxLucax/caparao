package io.github.mateuxlucax.caparao

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mateuxlucax.caparao.data.local.BrewDatabase
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.scale.ReplayScaleSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Scale to server on the emulator: the synthetic espresso is replayed as the scale, recorded by the brew screen,
 * saved, and uploaded by WorkManager to the server given in the `server_url` and `token` arguments. Run by
 * scripts/e2e.sh, which starts the stub and then checks what it received; skipped otherwise.
 */
@OptIn(ExperimentalTestApi::class)
class EndToEndTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val arguments = InstrumentationRegistry.getArguments()

    private fun text(id: Int) = context.getString(id)

    @Test
    fun anEspressoReachesTheServer() {
        val serverUrl = arguments.getString("server_url")
        val token = arguments.getString("token")
        assumeTrue("run by scripts/e2e.sh", serverUrl != null && token != null)

        context.deleteDatabase(BrewDatabase.NAME)
        SettingsStore.open(context).edit().clear().commit()
        val settings = (context.applicationContext as CaparaoApplication).container.settings
        settings.onboardingDone = true
        settings.saveServer(serverUrl!!, token!!)

        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_REPLAY, "synthetic-espresso")
            .putExtra(MainActivity.EXTRA_REPLAY_PAUSED, true)
        ActivityScenario.launch<MainActivity>(intent).use {
            val replay = (context.applicationContext as CaparaoApplication).container.scale as ReplayScaleSource
            compose.waitUntilAtLeastOneExists(hasContentDescription(text(R.string.scale_connected)), 10_000)
            compose.onNodeWithText(text(R.string.mode_espresso)).performClick()
            compose.waitUntilAtLeastOneExists(hasText(text(R.string.free_brew)), 10_000)
            compose.onNodeWithText(text(R.string.free_brew)).performClick()
            compose.onNodeWithText(text(R.string.next)).performClick()
            compose.waitUntilAtLeastOneExists(hasText(text(R.string.tare)), 10_000)
            compose.waitForIdle()
            // AnimatedContent can keep the outgoing DoseStep's Next semantics during its transition.
            compose.waitUntilDoesNotExist(hasText(text(R.string.ratio_caption)), 10_000)
            compose.waitForIdle()
            // The replay has not started, so Next keeps the planned 18 g dose.
            compose.onNodeWithText(text(R.string.next)).performClick()
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

            compose.onNodeWithText(text(R.string.save)).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.tab_brews)).performClick()
            // "Synced 16:52": the server confirmed it.
            val synced = text(R.string.synced_at).substringBefore("%1\$s").trim()
            compose.waitUntilAtLeastOneExists(hasText(synced, substring = true), 60_000)
        }
    }
}
