package io.github.mateuxlucax.caparao.wear

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        compose.setContent { WatchScreen(snapshot, onTare = {}, onTimer = {}, onReset = {}) }
        compose.onRoot().captureRoboImage("../docs/screenshots/watch/$lang/$name.png")
    }
}

@Config(qualifiers = "en-w227dp-h227dp-round-xhdpi")
class WatchScreenshotsEnTest : WatchScreenshotsTest("en")

@Config(qualifiers = "pt-rBR-w227dp-h227dp-round-xhdpi")
class WatchScreenshotsPtBrTest : WatchScreenshotsTest("pt-BR")
