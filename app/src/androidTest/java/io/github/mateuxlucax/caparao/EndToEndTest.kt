package io.github.mateuxlucax.caparao

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mateuxlucax.caparao.data.local.BrewDatabase
import io.github.mateuxlucax.caparao.data.local.SettingsStore
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
            .putExtra(MainActivity.EXTRA_REPLAY_AT_MS, 3_000L)
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.waitUntilAtLeastOneExists(hasText(text(R.string.scale_connected)), 10_000)
            compose.onNodeWithText(text(R.string.mode_espresso)).performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement("18")
            compose.onNodeWithText(text(R.string.next)).performClick()
            // The shot starts 11 s into this replay and ends about 30 s later.
            compose.waitUntilAtLeastOneExists(hasText(text(R.string.brew_saved)), 60_000)
            compose.onNodeWithText(text(R.string.close)).performClick()
            compose.onNodeWithText(text(R.string.tab_brews)).performClick()
            // "Synced 16:52": the server confirmed it.
            val synced = text(R.string.synced_at).substringBefore("%1\$s").trim()
            compose.waitUntilAtLeastOneExists(hasText(synced, substring = true), 60_000)
        }
    }
}
