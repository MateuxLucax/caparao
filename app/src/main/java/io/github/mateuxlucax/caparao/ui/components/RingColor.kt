package io.github.mateuxlucax.caparao.ui.components

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import io.github.mateuxlucax.caparao.brew.RatioBand
import io.github.mateuxlucax.caparao.brew.ratioBand
import io.github.mateuxlucax.caparao.ui.theme.Motion
import kotlin.math.max
import kotlin.math.min

/** The ratio number keeps the ring's hue, so it has to stay readable on the surface. */
private const val MIN_TEXT_CONTRAST = 4.5f

/** What the ring and the ratio number wear for a [band]: [stroke] is the arc, [text] reads on the surface. */
internal data class RingColors(val stroke: Color, val text: Color)

/**
 * Light green short of the band, primary on it, a darker green past it; the dark theme already has a light primary,
 * so it reads muted → primary → lighter (#188, #192).
 */
internal fun ColorScheme.ringStroke(band: RatioBand, dark: Boolean): Color = when (band) {
    RatioBand.BELOW -> primaryContainer
    RatioBand.AT -> primary
    // The light theme has no token darker than onPrimaryContainer, and that one sits next to primary (#192).
    RatioBand.ABOVE -> lerp(primary, if (dark) Color.White else Color.Black, 0.35f)
}

/**
 * [this] mixed toward black or white until it reads as text on [background], keeping the hue; a light green ring still
 * gives a legible ratio number (#192).
 */
internal fun Color.readableOn(background: Color): Color {
    if (contrast(this, background) >= MIN_TEXT_CONTRAST) return this
    val toward = if (background.luminance() > 0.5f) Color.Black else Color.White
    var readable = this
    repeat(20) {
        readable = lerp(readable, toward, 0.2f)
        if (contrast(readable, background) >= MIN_TEXT_CONTRAST) return readable
    }
    return readable
}

/** WCAG 2 contrast ratio, from 1 (the same colour) to 21 (black on white). */
internal fun contrast(first: Color, second: Color): Float {
    val a = first.luminance()
    val b = second.luminance()
    return (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
}

/**
 * The ring and the ratio number, easing between bands as the brew moves. With animations off they just are, like the
 * rest of the app (#192).
 */
@Composable
internal fun rememberRingColors(band: RatioBand): RingColors {
    val dark = isSystemInDarkTheme()
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val still = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val stroke = colors.ringStroke(band, dark)
    val text = stroke.readableOn(colors.surface)
    val spec = tween<Color>(500, easing = Motion.Standard)
    val shownStroke by animateColorAsState(stroke, spec, label = "ring")
    val shownText by animateColorAsState(text, spec, label = "ratio")
    return if (still) RingColors(stroke, text) else RingColors(shownStroke, shownText)
}
