package io.github.mateuxlucax.caparao.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.TimeSource
import io.github.mateuxlucax.caparao.watch.WatchGuide
import io.github.mateuxlucax.caparao.watch.WatchSnapshot
import io.github.mateuxlucax.caparao.watch.WatchState
import io.github.mateuxlucax.caparao.watch.WatchStepKind
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The watch screen on the JVM: each state renders and keeps the weight in the round screen's safe width. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class WatchTests(private val lang: String) {
    val localeTestName: String get() = lang // locale coverage is via @Config below
    @get:Rule val compose = createComposeRule()

    @Test fun scale() = shot(WatchSnapshot(WatchState.SCALE, 18.2f, 0f, 12_000, timerRunning = true))

    @Test fun pour() = shot(
        WatchSnapshot(
            WatchState.BREWING, 112f, 4.1f, 58_000, timerRunning = true,
            guide = WatchGuide(index = 1, count = 5, kind = WatchStepKind.POUR, targetGrams = 150f, dueInMs = null, paceMs = 2_000),
        ),
    )

    @Test fun frenchPressRemove() = shot(
        WatchSnapshot(
            WatchState.BREWING, 300f, 0f, 240_000, timerRunning = false,
            guide = WatchGuide(index = 4, count = 4, kind = WatchStepKind.WAIT, targetGrams = null, dueInMs = null, paceMs = null),
        ),
    )

    @Test fun frenchPressServe() = shot(
        WatchSnapshot(
            WatchState.BREWING, 300f, 0f, 250_000, timerRunning = false,
            guide = WatchGuide(index = 4, count = 4, kind = WatchStepKind.WAIT, targetGrams = null, dueInMs = null, paceMs = 0),
        ),
    )

    @Test fun disconnected() {
        val snapshot = WatchSnapshot(WatchState.DISCONNECTED, 0f, 0f, 0, timerRunning = false)
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.White).clip(CircleShape)) {
                WatchScreen(snapshot, onTare = {}, onTimer = {}, onReset = {}, timeSource = FixedTime)
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .getString(R.string.scale_disconnected),
        ).assertIsDisplayed()
    }

    private fun shot(snapshot: WatchSnapshot) {
        // Robolectric draws a square: clip to the round screen the qualifiers describe.
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.White).clip(CircleShape)) {
                WatchScreen(snapshot, onTare = {}, onTimer = {}, onReset = {}, timeSource = FixedTime)
            }
        }
        if (snapshot.state != WatchState.DISCONNECTED) {
            val weightNode = compose.onNodeWithContentDescription(WatchFormat.grams(snapshot.weightGrams))
            weightNode.assertExists()
            assertTrue(
                "weight number and unit stay within the round screen safe width",
                weightNode.fetchSemanticsNode().boundsInRoot.width <= with(compose.density) { 171.dp.toPx() },
            )
        }
        compose.waitForIdle()
    }
}

@Config(qualifiers = "en-w227dp-h227dp-round-xhdpi")
class WatchTestsEnTest : WatchTests("en")

@Config(qualifiers = "pt-rBR-w227dp-h227dp-round-xhdpi")
class WatchTestsPtBrTest : WatchTests("pt-BR")

/** A fixed clock, so the screen renders deterministically. */
private object FixedTime : TimeSource {
    @Composable override fun currentTime(): String = "10:09"
}
