package io.github.mateuxlucax.caparao.ui.widget

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class BrewWidgetChartTest {
    @Test
    fun bitmapAllocationStaysBoundedEvenForOversizedWidgetDimensions() {
        val bitmap = BrewWidgetChart.render(
            counts = listOf(0, 1, 3, 0, 2, 4, 1),
            countLabels = listOf("0", "1", "3", "0", "2", "4", "1"),
            widthPx = 100_000,
            heightPx = 100_000,
            density = 4.0f,
        )

        assertEquals(Bitmap.Config.ALPHA_8, bitmap.config)
        assertTrue(bitmap.allocationByteCount <= BrewWidgetChart.MAX_BITMAP_BYTES)
        assertTrue(
            bitmap.width.toLong() * bitmap.height <= BrewWidgetChart.MAX_BITMAP_BYTES,
        )
    }

    @Test
    fun monochromeChartKeepsFullResolutionWhenItFitsTheBitmapBudget() {
        val bitmap = BrewWidgetChart.render(
            counts = listOf(0, 1, 3, 0, 2, 4, 1),
            countLabels = listOf("0", "1", "3", "0", "2", "4", "1"),
            widthPx = 1_152,
            heightPx = 180,
            density = 3.0f,
        )

        assertEquals(1_152, bitmap.width)
        assertEquals(180, bitmap.height)
        assertTrue(bitmap.allocationByteCount <= BrewWidgetChart.MAX_BITMAP_BYTES)
    }

    @Test
    fun multipleResponsiveChartsCanShareTheBitmapBudget() {
        val variantBudget = BrewWidgetChart.MAX_BITMAP_BYTES / 2
        val bitmap = BrewWidgetChart.render(
            counts = listOf(0, 1, 3, 0, 2, 4, 1),
            countLabels = listOf("0", "1", "3", "0", "2", "4", "1"),
            widthPx = 1_272,
            heightPx = 180,
            density = 3.0f,
            maxBitmapBytes = variantBudget,
        )

        assertTrue(bitmap.allocationByteCount <= variantBudget)
        assertTrue(bitmap.width > 700)
    }
}
