package io.github.mateuxlucax.caparao.ui.widget

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

        assertTrue(bitmap.allocationByteCount <= BrewWidgetChart.MAX_BITMAP_BYTES)
        assertTrue(
            bitmap.width.toLong() * bitmap.height <= BrewWidgetChart.MAX_BITMAP_BYTES / 4,
        )
    }
}
