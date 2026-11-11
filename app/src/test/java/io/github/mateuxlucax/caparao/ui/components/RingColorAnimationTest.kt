package io.github.mateuxlucax.caparao.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mateuxlucax.caparao.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The ring turns with the band, and snaps instead of easing when the system turns animations off (#192). */
@RunWith(AndroidJUnit4::class)
class RingColorAnimationTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val light = dynamicLightColorScheme(context)

    @Test
    fun coloursChangeWithoutTheTransitionWhenAnimationsAreOff() {
        animations(0f)
        val (band, shown) = render()
        assertEquals(light.primaryContainer, shown.value!!.stroke)
        assertEquals(light.primaryContainer.readableOn(light.surface), shown.value!!.text)
        compose.mainClock.autoAdvance = false
        band.value = TargetBand.ABOVE
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(64)
        assertEquals(light.ringStroke(TargetBand.ABOVE, dark = false), shown.value!!.stroke)
        assertEquals(light.ringStroke(TargetBand.ABOVE, dark = false).readableOn(light.surface), shown.value!!.text)
    }

    @Test
    fun coloursEaseBetweenBandsWhenAnimationsAreOn() {
        animations(1f)
        val (band, shown) = render()
        compose.mainClock.autoAdvance = false
        band.value = TargetBand.ABOVE
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(64)
        assertNotEquals(light.ringStroke(TargetBand.ABOVE, dark = false), shown.value!!.stroke)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(light.ringStroke(TargetBand.ABOVE, dark = false), shown.value!!.stroke)
    }

    private fun animations(scale: Float) {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun render(): Pair<MutableState<TargetBand>, MutableState<RingColors?>> {
        val band = mutableStateOf(TargetBand.BELOW)
        val shown = mutableStateOf<RingColors?>(null)
        compose.setContent {
            AppTheme {
                val colors = rememberRingColors(band.value)
                SideEffect { shown.value = colors }
            }
        }
        compose.waitForIdle()
        return band to shown
    }
}
