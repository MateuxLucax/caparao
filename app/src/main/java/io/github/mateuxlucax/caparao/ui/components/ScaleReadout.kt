package io.github.mateuxlucax.caparao.ui.components

import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.theme.Motion
import io.github.mateuxlucax.caparao.ui.theme.TabularFlex
import io.github.mateuxlucax.caparao.ui.theme.animatedFlex
import io.github.mateuxlucax.caparao.ui.theme.animationsOff
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

private val Tabular = TabularFlex

/** The ring is open at the bottom, a gauge like the scale's own. */
private const val ARC_START = 135f
private const val ARC_SWEEP = 270f

/** The flow, in g/s, at which the espresso flow cue is full. */
private const val FLOW_CUE_FULL = 4.0

/** The live ratio against the one to stop at, for [ScaleReadout]'s ring. */
data class RatioTarget(val ratio: Double?, val target: Double, val progress: Float, val onTarget: Boolean)

/**
 * Weight with the timer under it. Shared by Home and the live brew.
 *
 * Home shows the flow under the timer, nothing around it. With a [ratio] (the brew), an open ring goes around the weight,
 * filling on the way to the target ratio and tinted by how far the live ratio is from it: light green short of the ±5%
 * band, primary on it, a darker green past it (lighter in the dark theme), pulsing once the ratio reads the target. Flow
 * and ratio then sit under the ring as two labeled numbers, the ratio in the ring's colour.
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
    /** Home: whether the scale is connected; the weight is light until it is. */
    live: Boolean = true,
    /** Espresso waits for the first drop with the ring closed; it opens when this turns true (#194). */
    started: Boolean = true,
    /** Espresso's tare: the weight rolls down to zero on a longer tween while this is true (#194). */
    rolling: Boolean = false,
    /** Espresso's extraction: a thin ring inside the open ring thickens with the live flow (#194). */
    flowCue: Boolean = false,
    /** Filter pause: soften the ring while keeping the held weight readable. */
    ringAlpha: Float = 1f,
) {
    val locale = LocalConfiguration.current.locales[0]
    // Readings come at 10 Hz: glide between them so the number rolls instead of jumping.
    val weight by animateFloatAsState(
        weightGrams.toFloat(),
        if (rolling) tween(400, easing = Motion.EmphasizedDecelerate) else tween(100, easing = LinearEasing),
        label = "weight",
    )
    val flow = flowGramsPerSecond.coerceIn(0.0, 99.9)
    val flowText = "${Format.flow(flow, locale)} ${stringResource(R.string.unit_grams_per_second)}"
    val shownTimerColor by animateColorAsState(
        timerColor ?: if (timerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "timer",
    )
    // Roboto Flex's axes carry the state (#109): the weight is light until the scale
    // connects, heavy and wide while it counts, and pulses to the heaviest the moment the ratio is reached.
    var pulse by remember { mutableStateOf(false) }
    LaunchedEffect(ratio?.onTarget) {
        if (ratio?.onTarget == true) {
            pulse = true
            delay(350)
            pulse = false
        }
    }
    val counting = timerRunning || ratio?.onTarget == true
    val weightStyle = animatedFlex(
        weight = when {
            !live -> 300
            pulse -> 1000
            ratio?.onTarget == true -> 820
            timerRunning -> 720
            else -> 560
        },
        // Home's weight already fills the width.
        width = if (spacious) 100 else if (counting) 120 else 108,
    )
    val timerStyle = animatedFlex(if (timerRunning) 640 else 420)
    val weightAndTimer = @Composable {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Format.grams(weight.toDouble(), locale),
                    style = weightStyle,
                    fontSize = 64.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.unit_grams),
                    Modifier.padding(start = 4.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Format.timer(timerSeconds), style = MaterialTheme.typography.headlineSmall.merge(timerStyle), color = shownTimerColor)
        }
    }
    if (ratio == null && spacious) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Format.grams(weight.toDouble(), locale),
                    style = weightStyle,
                    fontSize = 120.sp,
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
    val colors = MaterialTheme.colorScheme
    val still = animationsOff()
    val shownRingAlpha by animateFloatAsState(ringAlpha, if (still) snap() else tween(300), label = "ringAlpha")
    // The ring opens from its start on the first drop, then the progress fills it; with animations off it is open at once.
    val open by animateFloatAsState(if (started) 1f else 0f, if (still) snap() else spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow), label = "open")
    // The live flow as the thickness of a thin ring inside the open one: a cue, not a number, so it stays under the numbers.
    val flowShown by animateFloatAsState(
        if (flowCue) (flow / FLOW_CUE_FULL).coerceIn(0.0, 1.0).toFloat() else 0f,
        if (still) snap() else spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow),
        label = "flowCue",
    )
    val ring = rememberRingColors(targetBand(ratio.ratio, ratio.target))
    // Rolls like the weight instead of jumping a tenth at a time.
    val shownRatio by animateFloatAsState((ratio.ratio ?: 0.0).toFloat(), tween(100, easing = LinearEasing), label = "ratioValue")
    // One glow out of the outer ring the moment the target is reached.
    val glow = remember { Animatable(0f) }
    LaunchedEffect(ratio.onTarget, still) {
        if (ratio.onTarget && !still) {
            glow.snapTo(1f)
            glow.animateTo(0f, tween(900, easing = Motion.EmphasizedDecelerate))
        } else {
            glow.snapTo(0f)
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                // One thick open ring with round ends on a neutral track (#109).
                val outer = 20.dp.toPx()
                val o = outer / 2
                val outerSize = Size(size.width - outer, size.height - outer)
                val outerStyle = Stroke(outer, cap = StrokeCap.Round)
                if (glow.value > 0f && shownRingAlpha > 0f) {
                    val wide = outer * (1f + 1.5f * (1f - glow.value))
                    drawArc(ring.stroke.copy(alpha = 0.35f * glow.value * shownRingAlpha), ARC_START, ARC_SWEEP, false, Offset(o, o), outerSize, style = Stroke(wide, cap = StrokeCap.Round))
                }
                if (open > 0.005f) drawArc(colors.surfaceContainerHighest.copy(alpha = shownRingAlpha), ARC_START, ARC_SWEEP * open, false, Offset(o, o), outerSize, style = outerStyle)
                if (progress > 0.005f) drawArc(ring.stroke.copy(alpha = shownRingAlpha), ARC_START, ARC_SWEEP * progress, false, Offset(o, o), outerSize, style = outerStyle)
                if (flowShown > 0.005f) {
                    val width = 2.dp.toPx() + 8.dp.toPx() * flowShown
                    // Centered in the gap just inside the outer ring, 4 dp clear of it.
                    val radius = size.width / 2 - outer - 4.dp.toPx() - width / 2
                    drawArc(
                        colors.secondary.copy(alpha = 0.6f * shownRingAlpha),
                        ARC_START,
                        ARC_SWEEP,
                        false,
                        Offset(size.width / 2 - radius, size.height / 2 - radius),
                        Size(radius * 2, radius * 2),
                        style = Stroke(width, cap = StrokeCap.Round),
                    )
                }
            }
            weightAndTimer()
        }
        Row(Modifier.fillMaxWidth().padding(top = 20.dp)) {
            Stat(stringResource(R.string.label_flow), flowText, colors.secondary, null, Modifier.weight(1f))
            Stat(
                stringResource(R.string.label_ratio),
                ratio.ratio?.let { stringResource(R.string.ratio_value, Format.ratio(shownRatio.toDouble(), locale)) } ?: "–",
                ring.text,
                stringResource(R.string.target_ratio_value, Format.ratio(ratio.target, locale)),
                Modifier.weight(1f),
            )
        }
    }
}

/** A label over a number, and an optional note under it; centered under the ring. */
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
