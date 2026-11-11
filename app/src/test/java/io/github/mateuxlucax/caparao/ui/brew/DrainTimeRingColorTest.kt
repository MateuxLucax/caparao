package io.github.mateuxlucax.caparao.ui.brew

import android.content.Context
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import io.github.mateuxlucax.caparao.ui.components.TargetBand
import io.github.mateuxlucax.caparao.ui.components.contrast
import io.github.mateuxlucax.caparao.ui.components.readableOn
import io.github.mateuxlucax.caparao.ui.components.ringStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
class DrainTimeRingColorTest {
    @Test
    fun drainBarUsesRingColorsAndReadableTextInBothThemes() {
        val cases = listOf(
            BrewUiState.Window.BEFORE to TargetBand.BELOW,
            BrewUiState.Window.INSIDE to TargetBand.AT,
            BrewUiState.Window.PAST to TargetBand.ABOVE,
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        for ((scheme, dark) in listOf(dynamicLightColorScheme(context) to false, dynamicDarkColorScheme(context) to true)) {
            for ((window, band) in cases) {
                val actual = drainTimeRingColors(scheme, window, dark)
                assertEquals("$window in dark=$dark", scheme.ringStroke(band, dark), actual.stroke)
                assertEquals("$window text in dark=$dark", actual.stroke.readableOn(scheme.surface), actual.text)
                assertTrue("$window contrast in dark=$dark", contrast(actual.text, scheme.surface) >= 4.5f)
            }
        }
    }
}
