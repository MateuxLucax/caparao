package io.github.mateuxlucax.caparao.ui.brew

import io.github.mateuxlucax.caparao.ui.components.TargetBand
import io.github.mateuxlucax.caparao.ui.components.contrast
import io.github.mateuxlucax.caparao.ui.components.readableOn
import io.github.mateuxlucax.caparao.ui.components.ringStroke
import io.github.mateuxlucax.caparao.ui.theme.BrandColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrainTimeRingColorTest {
    @Test
    fun drainBarUsesRingColorsAndReadableTextInBothThemes() {
        val cases = listOf(
            BrewUiState.Window.BEFORE to TargetBand.BELOW,
            BrewUiState.Window.INSIDE to TargetBand.AT,
            BrewUiState.Window.PAST to TargetBand.ABOVE,
        )
        for ((scheme, dark) in listOf(BrandColor.LEAF.light to false, BrandColor.LEAF.dark to true)) {
            for ((window, band) in cases) {
                val actual = drainTimeRingColors(scheme, window, dark)
                assertEquals("$window in dark=$dark", scheme.ringStroke(band, dark), actual.stroke)
                assertEquals("$window text in dark=$dark", actual.stroke.readableOn(scheme.surface), actual.text)
                assertTrue("$window contrast in dark=$dark", contrast(actual.text, scheme.surface) >= 4.5f)
            }
        }
    }
}
