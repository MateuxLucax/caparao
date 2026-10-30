package io.github.mateuxlucax.caparao.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.TimeSource
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.mateuxlucax.caparao.watch.WatchGuide
import io.github.mateuxlucax.caparao.watch.WatchSnapshot
import io.github.mateuxlucax.caparao.watch.WatchState
import io.github.mateuxlucax.caparao.watch.WatchStepKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The watch screen in docs/screenshots/watch, rendered on the JVM; `-Pscreenshots` records (scripts/screenshots.sh). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class WatchScreenshotsTest(private val lang: String) {
    @get:Rule val compose = createComposeRule()

    @Test fun scale() = shot("scale", WatchSnapshot(WatchState.SCALE, 18.2f, 0f, 12_000, timerRunning = true))

    @Test fun pour() = shot(
        "pour",
        WatchSnapshot(
            WatchState.BREWING, 112f, 4.1f, 58_000, timerRunning = true,
            guide = WatchGuide(index = 1, count = 5, kind = WatchStepKind.POUR, targetGrams = 150f, dueInMs = null, paceMs = 2_000),
        ),
    )

    @Test fun disconnected() = shot("disconnected", WatchSnapshot(WatchState.DISCONNECTED, 0f, 0f, 0, timerRunning = false))

    private fun shot(name: String, snapshot: WatchSnapshot) {
        // Robolectric draws a square: clip to the round screen the qualifiers describe.
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.White).clip(CircleShape)) {
                WatchScreen(snapshot, onTare = {}, onTimer = {}, onReset = {}, timeSource = FixedTime)
            }
        }
        compose.onRoot().captureRoboImage("../docs/screenshots/watch/$lang/$name.png")
    }
}

@Config(qualifiers = "en-w227dp-h227dp-round-xhdpi")
class WatchScreenshotsEnTest : WatchScreenshotsTest("en")

@Config(qualifiers = "pt-rBR-w227dp-h227dp-round-xhdpi")
class WatchScreenshotsPtBrTest : WatchScreenshotsTest("pt-BR")

/** A fixed clock, so the images only change when the screen does. */
private object FixedTime : TimeSource {
    @Composable override fun currentTime(): String = "10:09"
}
