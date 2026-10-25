package io.github.mateuxlucax.caparao.ui.components

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.caparao.ui.format.Format
import kotlin.math.PI
import kotlin.math.sin

private const val LOOP_MS = 5_200f
private const val DOSE = 36.0
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** Progress of the segment [from]..[to] (ms) at [t], eased. */
private fun segment(t: Float, from: Float, to: Float, easing: Easing = FastOutSlowInEasing) =
    easing.transform(((t - from) / (to - from)).coerceIn(0f, 1f))

/** One frame of the pour, all derived from the loop time so every part stays in step. */
private class PourFrame(t: Float) {
    /** How full the cup is, 0..1: fills with the pour, drains on the tare. */
    val fill = segment(t, 700f, 3_700f, Emphasized) * (1f - segment(t, 4_500f, 5_000f))

    /** The stream's head grows down from the top, then its tail follows it into the cup. */
    val streamHead = segment(t, 300f, 700f)
    val streamTail = segment(t, 3_700f, 4_100f, EmphasizedAccelerate)
    val pouring = t in 300f..4_100f
    val led = segment(t, 300f, 700f) * (1f - segment(t, 5_000f, 5_200f, LinearEasing))
    val halo = led * if (t < 4_100f) 0.12f + 0.16f * (0.5f + 0.5f * sin(2 * PI * t / 1_200).toFloat()) else 0.28f
    val wobble = 0.6f * sin(t / 80f)
    val settled = t in 4_100f..4_400f
}

/**
 * Welcome animation: a thin stream pours into the cup on the scale, the coffee rises with the weight, the scale's LED
 * glows while it measures, then the cup is tared back to zero. One 5.2 s loop that starts and ends empty. With
 * animations off (developer options or accessibility), a still frame of the full cup.
 */
@Composable
fun PourAnimation(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val still = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val time = if (still) {
        4_200f
    } else {
        val transition = rememberInfiniteTransition(label = "pour")
        transition.animateFloat(0f, LOOP_MS, infiniteRepeatable(tween(LOOP_MS.toInt(), easing = LinearEasing)), label = "t").value
    }
    val frame = PourFrame(time)
    val colors = MaterialTheme.colorScheme
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(240.dp)) {
            // Drawn in a 240-unit square.
            scale(size.width / 240f, pivot = Offset.Zero) {
                val bowl = Path().apply {
                    moveTo(78f, 110f)
                    lineTo(162f, 110f)
                    lineTo(154f, 165f)
                    quadraticTo(150f, 187f, 128f, 187f)
                    lineTo(112f, 187f)
                    quadraticTo(90f, 187f, 86f, 165f)
                    close()
                }
                val surface = 187f - frame.fill * 0.78f * 77f
                clipPath(bowl) {
                    drawRect(colors.primary, Offset(70f, surface), Size(100f, 190f - surface))
                }
                if (frame.pouring) {
                    val top = 6f + (surface - 6f) * frame.streamTail
                    val bottom = 6f + (surface - 6f) * frame.streamHead
                    if (bottom > top) {
                        drawLine(
                            colors.primary.copy(alpha = 0.9f),
                            Offset(120f + frame.wobble, top),
                            Offset(120f, bottom),
                            strokeWidth = 3f,
                            cap = StrokeCap.Round,
                        )
                    }
                    if (frame.streamHead == 1f && frame.streamTail == 0f && frame.fill > 0.02f) {
                        drawOval(colors.onPrimary.copy(alpha = 0.25f), Offset(113f, surface - 1.5f), Size(14f, 3f))
                    }
                }
                drawPath(bowl, colors.onSurface, style = Stroke(width = 2.5f))
                val handle = Path().apply {
                    moveTo(159f, 122f)
                    cubicTo(186f, 118f, 186f, 160f, 154f, 157f)
                }
                drawPath(handle, colors.onSurface, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
                drawRoundRect(colors.surfaceContainerHighest, Offset(36f, 190f), Size(168f, 22f), CornerRadius(11f))
                drawRoundRect(colors.outlineVariant, Offset(36f, 190f), Size(168f, 22f), CornerRadius(11f), style = Stroke(1.5f))
                val ledCenter = Offset(120f, 201f)
                drawCircle(colors.primary.copy(alpha = frame.halo), radius = 11f, center = ledCenter)
                drawCircle(colors.outline.copy(alpha = 0.4f), radius = 5f, center = ledCenter)
                drawCircle(colors.primary.copy(alpha = frame.led), radius = 5f, center = ledCenter)
            }
        }
        Text(
            "${Format.grams(DOSE * frame.fill, LocalConfiguration.current.locales[0])} g",
            style = MaterialTheme.typography.displaySmall.merge(TextStyle(fontFeatureSettings = "tnum")),
            color = if (frame.settled) colors.primary else colors.onSurface,
        )
    }
}
