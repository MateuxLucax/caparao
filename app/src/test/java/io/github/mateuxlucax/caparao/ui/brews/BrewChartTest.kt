package io.github.mateuxlucax.caparao.ui.brews

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mateuxlucax.caparao.model.BrewSample
import io.github.mateuxlucax.caparao.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The cached chart drawing (#320): the path is built once and revealed by a clipRect, and animations off show the
 * whole curve at once (#183).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrewChartTest {
    @get:Rule
    val compose = createComposeRule()

    private val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver

    @Test
    fun thePathFollowsEverySample() {
        val path = curvePath(listOf(Offset(1f, 2f), Offset(30f, 40f), Offset(90f, 20f)))
        assertEquals(Rect(1f, 2f, 90f, 40f), path.getBounds())
    }

    @Test
    fun theDrawInRevealsThePathFromTheLeft() {
        val bitmap = drawCurve(progress = 0.5f)
        // The clip ends at 60 of the 10..110 path.
        assertTrue("the revealed part should show", bitmap.isRed(30, 50))
        assertTrue("the hidden part should stay clear", !bitmap.isRed(90, 50))
    }

    @Test
    fun fullProgressShowsTheWholePath() {
        val bitmap = drawCurve(progress = 1f)
        assertTrue(bitmap.isRed(30, 50))
        assertTrue(bitmap.isRed(90, 50))
    }

    @Test
    fun animationsOffDrawTheWholeCurveAtOnce() {
        val before = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            var density = 1f
            var weightColor = Color.Unspecified
            compose.setContent {
                AppTheme {
                    density = LocalDensity.current.density
                    weightColor = MaterialTheme.colorScheme.primary
                    Box(Modifier.width(300.dp).testTag("chart")) {
                        BrewChart(samples, durationMs = 20_000, weightLabel = "g", flowLabel = "g/s")
                    }
                }
            }
            compose.waitForIdle()
            val bitmap = compose.onNodeWithTag("chart").captureToImage().asAndroidBitmap()
            val argb = weightColor.toArgb()
            // The weight curve runs from 0 g at the bottom left corner of the plot to 10 g at the top right one.
            assertTrue("the start of the curve should show", bitmap.hasColorNear(36f * density, 200f * density, argb))
            assertTrue("the end of the curve should show", bitmap.hasColorNear(264f * density, 8f * density, argb))
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, before)
        }
    }

    private fun drawCurve(progress: Float): android.graphics.Bitmap {
        val path = curvePath(listOf(Offset(10f, 50f), Offset(110f, 50f)))
        val image = ImageBitmap(120, 100)
        val stroke = Stroke(10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(120f, 100f)) {
            drawCurve(path, Color.Red, stroke, left = 0f, right = 100f, progress = progress)
        }
        return image.asAndroidBitmap()
    }

    private fun android.graphics.Bitmap.isRed(x: Int, y: Int) = getPixel(x, y) == Color.Red.toArgb()

    private fun android.graphics.Bitmap.hasColorNear(x: Float, y: Float, argb: Int): Boolean {
        val cx = x.toInt()
        val cy = y.toInt()
        return (-2..2).any { dx ->
            (-2..2).any { dy ->
                val px = cx + dx
                val py = cy + dy
                px in 0 until width && py in 0 until height && getPixel(px, py) == argb
            }
        }
    }

    private companion object {
        /** A straight line from no weight to the axis maximum, so the curve touches both plot corners. */
        val samples = listOf(BrewSample(0, 0.0, 0.0), BrewSample(10_000, 5.0, 0.5), BrewSample(20_000, 10.0, 0.0))
    }
}
