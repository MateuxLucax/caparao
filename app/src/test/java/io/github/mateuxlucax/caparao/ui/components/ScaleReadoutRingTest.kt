package io.github.mateuxlucax.caparao.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mateuxlucax.caparao.ui.theme.AppTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The espresso ring is closed while the shot waits for its first drop and opens once brewing (#194). The ring is drawn
 * on a Canvas with no semantics, so this samples its track at the top of the ring, with animations on.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScaleReadoutRingTest {
    @get:Rule
    val compose = createComposeRule()

    private val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver

    @Test
    fun ringOpensOnceTheShotStarts() {
        val before = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            var started by mutableStateOf(false)
            var track = Color.Unspecified
            compose.setContent {
                AppTheme {
                    track = MaterialTheme.colorScheme.surfaceContainerHighest
                    // 200 dp square: the ring's 20 dp stroke centres 10 dp in from the top.
                    Box(Modifier.width(200.dp).testTag("readout")) {
                        ScaleReadout(
                            weightGrams = 0.0,
                            flowGramsPerSecond = 0.0,
                            timerSeconds = 0,
                            timerRunning = false,
                            ratio = RatioTarget(ratio = null, target = 2.0, progress = 0f, onTarget = false),
                            started = started,
                        )
                    }
                }
            }
            compose.waitForIdle()
            assertFalse("the ring is closed before the first drop", trackDrawn(track))
            started = true
            compose.waitForIdle()
            assertTrue("the ring is open once the shot has started", trackDrawn(track))
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, before)
        }
    }

    /** Whether the ring's track colour shows at the top of the ring, where a closed ring draws nothing. */
    private fun trackDrawn(track: Color): Boolean {
        val bitmap = compose.onNodeWithTag("readout").captureToImage().asAndroidBitmap()
        val argb = bitmap.getPixel(bitmap.width / 2, bitmap.width / 20)
        val pixel = Color(argb)
        return abs(pixel.red - track.red) < 0.02f && abs(pixel.green - track.green) < 0.02f && abs(pixel.blue - track.blue) < 0.02f
    }
}
