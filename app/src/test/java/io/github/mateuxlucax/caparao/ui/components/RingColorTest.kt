package io.github.mateuxlucax.caparao.ui.components

import io.github.mateuxlucax.caparao.brew.RatioBand
import android.content.Context
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.math.sqrt

/** The ring's ±5% band and the containers it wears in each theme (#192). */
@RunWith(RobolectricTestRunner::class)
class RingColorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val light = dynamicLightColorScheme(context)
    private val dark = dynamicDarkColorScheme(context)

    @Test
    fun bothEdgesOfTheBandStayPrimary() {
        assertEquals(RatioBand.AT, ratioBand(1.9, 2.0)) // −5%
        assertEquals(RatioBand.AT, ratioBand(2.1, 2.0)) // +5%
        assertEquals(RatioBand.BELOW, ratioBand(1.89, 2.0))
        assertEquals(RatioBand.ABOVE, ratioBand(2.11, 2.0))
        // The band follows the recipe's target, not a fixed ratio.
        assertEquals(RatioBand.AT, ratioBand(2.09, 2.2))
        assertEquals(RatioBand.AT, ratioBand(2.31, 2.2))
        assertEquals(RatioBand.BELOW, ratioBand(2.08, 2.2))
        assertEquals(RatioBand.ABOVE, ratioBand(2.32, 2.2))
    }

    @Test
    fun withoutARatioTheRingKeepsTheTargetsColour() {
        assertEquals(RatioBand.AT, ratioBand(null, 2.0))
    }

    @Test
    fun shortOfTheBandWearsTheMutedContainerOfEachTheme() {
        assertEquals(light.primaryContainer, light.ringStroke(RatioBand.BELOW, dark = false))
        assertEquals(dark.primaryContainer, dark.ringStroke(RatioBand.BELOW, dark = true))
        assertEquals(light.primary, light.ringStroke(RatioBand.AT, dark = false))
        assertEquals(dark.primary, dark.ringStroke(RatioBand.AT, dark = true))
    }

    @Test
    fun pastTheBandTheLightThemeDarkensThePrimaryAndTheDarkThemeLightensIt() {
        val aboveLight = light.ringStroke(RatioBand.ABOVE, dark = false)
        val aboveDark = dark.ringStroke(RatioBand.ABOVE, dark = true)
        assertTrue(aboveLight.luminance() < light.primary.luminance())
        assertTrue(aboveDark.luminance() > dark.primary.luminance())
        // About four just-noticeable steps from primary, so the band reads without the numbers.
        assertTrue("light ${deltaE(aboveLight, light.primary)}", deltaE(aboveLight, light.primary) > 9.0)
        assertTrue("dark ${deltaE(aboveDark, dark.primary)}", deltaE(aboveDark, dark.primary) > 9.0)
    }

    @Test
    fun shortOfTheBandStaysVisibleAgainstTheTrackInBothThemes() {
        // primaryContainer and surfaceContainerHighest share a tone in a dynamic scheme, so the mint and the track
        // differ in chroma rather than brightness: a perceptual distance says it better than a contrast ratio.
        val onLight = deltaE(light.ringStroke(RatioBand.BELOW, dark = false), light.surfaceContainerHighest)
        val onDark = deltaE(dark.ringStroke(RatioBand.BELOW, dark = true), dark.surfaceContainerHighest)
        assertTrue("light $onLight", onLight > 15.0)
        assertTrue("dark $onDark", onDark > 15.0)
    }

    @Test
    fun theRatioNumberReadsOnTheSurfaceInEveryBandAndTheme() {
        for ((scheme, isDark) in listOf(light to false, dark to true)) {
            for (band in RatioBand.entries) {
                val text = scheme.ringStroke(band, isDark).readableOn(scheme.surface)
                assertTrue("$band ${contrast(text, scheme.surface)}", contrast(text, scheme.surface) >= 4.5f)
            }
        }
    }

    /** CIE76 distance, where about 2.3 is one just-noticeable step. */
    private fun deltaE(first: Color, second: Color): Double {
        val a = first.convert(ColorSpaces.CieLab)
        val b = second.convert(ColorSpaces.CieLab)
        return sqrt((a.red - b.red).pow(2) + (a.green - b.green).pow(2) + (a.blue - b.blue).pow(2)).toDouble()
    }
}
