package io.github.mateuxlucax.caparao.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.caparao.ui.components.CupArt.drawScale
import io.github.mateuxlucax.caparao.ui.components.CupArt.drawServer

/**
 * The French press: the filter's server with a plunger, on the scale. Fills with the real weight (its color tells the
 * distance from the target, no line, as the filter server), darkens while it steeps, rises off the scale on removal and
 * then lowers its plunger (#226).
 */
object PressArt {
    /** Coffee as it steeps: pale at the first water, dark at the deadline. [progress] is 0..1 of the steep. */
    fun steepColor(progress: Float): Color = lerp(Color(0xFFD9B98A), Color(0xFF5A3A22), progress.coerceIn(0f, 1f))

    /** The plunger's plate in the server's viewBox: [lowered] 0 rests in the neck, 1 sits in the coffee. */
    fun DrawScope.drawPlunger(color: Color, lowered: Float) {
        val t = lowered.coerceIn(0f, 1f)
        val plate = PLATE_RAISED + (PLATE_LOWERED - PLATE_RAISED) * t
        val half = (12f + 8f * t)
        val knob = plate - 11f
        drawLine(color, Offset(60f, knob), Offset(60f, plate), strokeWidth = 2.5f, cap = StrokeCap.Round)
        drawLine(color, Offset(60f - half, plate), Offset(60f + half, plate), strokeWidth = 3.5f, cap = StrokeCap.Round)
        drawCircle(color, radius = 3.5f, center = Offset(60f, knob))
    }

    private const val PLATE_RAISED = 64f
    private const val PLATE_LOWERED = 88f
}

/**
 * The press on the scale. [fill] is 0..1.1 of the target, [coffee] its color, [plunger] 0 raised to 1 lowered and
 * [lift] 0 on the scale to 1 off it. Tags: `guided-cup` on the frame, `guided-server` on the carafe canvas.
 */
@Composable
fun PressOnScale(
    fill: Float,
    coffee: Color,
    modifier: Modifier = Modifier,
    plunger: Float = 0f,
    lift: Float = 0f,
    size: Dp = 160.dp,
    showScale: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.width(size).height(size).testTag("guided-cup")) {
        Canvas(Modifier.fillMaxSize().testTag("guided-server")) {
            val rise = size.toPx() * 0.16f * lift
            val room = size.toPx() * if (showScale) 0.12f else 0f
            if (showScale) {
                drawScale(
                    Rect(this.size.width * 0.1f, this.size.height - room, this.size.width * 0.9f, this.size.height),
                    colors.secondaryContainer,
                    colors.onSecondaryContainer,
                    0.5f,
                )
            }
            drawServer(
                Rect(0f, -rise, this.size.width, this.size.height - room - rise),
                body = colors.surfaceContainerHighest,
                handle = colors.outlineVariant,
                water = coffee,
                fill = fill,
                overlay = { with(PressArt) { drawPlunger(colors.outline, plunger) } },
            )
        }
    }
}
