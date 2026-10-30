package io.github.mateuxlucax.caparao.ui.components

import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.theme.Motion

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** The rings are open at the bottom, a gauge like the scale's own. */
private const val ARC_START = 135f
private const val ARC_SWEEP = 270f

/** Flow that fills the inner ring; a fast espresso or a steady pour stays well under it. */
private const val RING_FULL_FLOW = 10.0

/** The live ratio against the one to stop at, for [ScaleReadout]'s rings. */
data class RatioTarget(val ratio: Double?, val target: Double, val progress: Float, val onTarget: Boolean)

/**
 * Weight with the timer under it. Shared by Home and the live brew.
 *
 * Home shows the flow under the timer, nothing around it. With a [ratio] (the brew), two open rings go around the weight:
 * the outer one fills on the way to the target ratio and turns tertiary with a glow once the ratio reads it, the inner
 * one fills with the flow. Flow and ratio then sit under the rings as two labeled numbers.
 */
@Composable
fun ScaleReadout(
    weightGrams: Double,
    flowGramsPerSecond: Double,
    timerSeconds: Int,
    timerRunning: Boolean,
    modifier: Modifier = Modifier,
    ratio: RatioTarget? = null,
    /** Overrides the timer's colour, for the espresso time window. */
    timerColor: Color? = null,
    /** Home: the weight fills the width and the timer and flow sit in labelled cards, to read from across the bench. */
    spacious: Boolean = false,
) {
    val locale = LocalConfiguration.current.locales[0]
    // Readings come at 10 Hz: glide between them so the number rolls instead of jumping.
    val weight by animateFloatAsState(weightGrams.toFloat(), tween(100, easing = LinearEasing), label = "weight")
    val flow = flowGramsPerSecond.coerceIn(0.0, 99.9)
    val flowText = "${Format.flow(flow, locale)} ${stringResource(R.string.unit_grams_per_second)}"
    val shownTimerColor by animateColorAsState(
        timerColor ?: if (timerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "timer",
    )
    val weightAndTimer = @Composable {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Format.grams(weight.toDouble(), locale),
                    style = Tabular,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.unit_grams),
                    Modifier.padding(start = 4.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Format.timer(timerSeconds), style = MaterialTheme.typography.headlineSmall.merge(Tabular), color = shownTimerColor)
        }
    }
    if (ratio == null && spacious) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Format.grams(weight.toDouble(), locale),
                    style = Tabular,
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.unit_grams),
                    Modifier.padding(start = 4.dp, bottom = 24.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(stringResource(R.string.label_time), Format.timer(timerSeconds), shownTimerColor, Modifier.weight(1f))
                StatCard(stringResource(R.string.label_flow), flowText, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            }
        }
        return
    }
    if (ratio == null) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            weightAndTimer()
            Text(
                flowText,
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.titleMedium.merge(Tabular),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val progress by animateFloatAsState(
        ratio.progress,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "progress",
    )
    val flowFill by animateFloatAsState(
        (flow / RING_FULL_FLOW).coerceIn(0.0, 1.0).toFloat(),
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "flow",
    )
    val colors = MaterialTheme.colorScheme
    val colorSpec = tween<Color>(500, easing = Motion.Standard)
    val outerColor by animateColorAsState(if (ratio.onTarget) colors.tertiary else colors.primary, colorSpec, label = "outer")
    val outerTrack by animateColorAsState(if (ratio.onTarget) colors.tertiaryContainer else colors.primaryContainer, colorSpec, label = "track")
    val ratioColor by animateColorAsState(if (ratio.onTarget) colors.tertiary else colors.onSurface, colorSpec, label = "ratio")
    // Rolls like the weight instead of jumping a tenth at a time.
    val shownRatio by animateFloatAsState((ratio.ratio ?: 0.0).toFloat(), tween(100, easing = LinearEasing), label = "ratioValue")
    // One glow out of the outer ring the moment the target is reached.
    val glow = remember { Animatable(0f) }
    LaunchedEffect(ratio.onTarget) {
        if (ratio.onTarget) {
            glow.snapTo(1f)
            glow.animateTo(0f, tween(900, easing = Motion.EmphasizedDecelerate))
        } else {
            glow.snapTo(0f)
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val outer = 14.dp.toPx()
                val o = outer / 2
                val outerSize = Size(size.width - outer, size.height - outer)
                val outerStyle = Stroke(outer, cap = StrokeCap.Round)
                if (glow.value > 0f) {
                    val wide = outer * (1f + 1.5f * (1f - glow.value))
                    drawArc(outerColor.copy(alpha = 0.35f * glow.value), ARC_START, ARC_SWEEP, false, Offset(o, o), outerSize, style = Stroke(wide))
                }
                drawArc(outerTrack, ARC_START, ARC_SWEEP, false, Offset(o, o), outerSize, style = outerStyle)
                if (progress > 0.005f) drawArc(outerColor, ARC_START, ARC_SWEEP * progress, false, Offset(o, o), outerSize, style = outerStyle)
                val stroke = 8.dp.toPx()
                val i = outer + 14.dp.toPx() + stroke / 2
                val innerSize = Size(size.width - 2 * i, size.height - 2 * i)
                val innerStyle = Stroke(stroke, cap = StrokeCap.Round)
                drawArc(colors.secondaryContainer, ARC_START, ARC_SWEEP, false, Offset(i, i), innerSize, style = innerStyle)
                if (flowFill > 0.005f) drawArc(colors.secondary, ARC_START, ARC_SWEEP * flowFill, false, Offset(i, i), innerSize, style = innerStyle)
            }
            weightAndTimer()
        }
        Row(Modifier.fillMaxWidth().padding(top = 20.dp)) {
            Stat(stringResource(R.string.label_flow), flowText, colors.secondary, null, Modifier.weight(1f))
            Stat(
                stringResource(R.string.label_ratio),
                ratio.ratio?.let { stringResource(R.string.ratio_value, Format.ratio(shownRatio.toDouble(), locale)) } ?: "–",
                ratioColor,
                stringResource(R.string.target_ratio_value, Format.ratio(ratio.target, locale)),
                Modifier.weight(1f),
            )
        }
    }
}

/** A label over a number, and an optional note under it; centered under the rings. */
@Composable
private fun Stat(label: String, value: String, color: Color, note: String?, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall.merge(Tabular), color = color)
        if (note != null) Text(note, style = MaterialTheme.typography.labelMedium.merge(Tabular), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A label over a large number on a tonal card, for the Home readout. */
@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.displaySmall.merge(Tabular), color = color, maxLines = 1)
        }
    }
}
