package io.github.mateuxlucax.caparao.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** Flow that fills the inner ring; a fast espresso or a steady pour stays well under it. */
private const val RING_FULL_FLOW = 10.0

/** The live ratio against the one to stop at, for [ScaleReadout]'s rings. */
data class RatioTarget(val ratio: Double?, val target: Double, val progress: Float, val onTarget: Boolean)

/**
 * Weight with the timer and the flow under it. Shared by Home and the live brew.
 *
 * With a [ratio], two rings go around it: the outer one fills on the way to the target ratio, the inner one fills with
 * the flow. Both turn tertiary once the ratio reads the target, so the user knows when to stop. Home has none.
 */
@Composable
fun ScaleReadout(
    weightGrams: Double,
    flowGramsPerSecond: Double,
    timerSeconds: Int,
    timerRunning: Boolean,
    modifier: Modifier = Modifier,
    ratio: RatioTarget? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    // Readings come at 10 Hz: glide between them so the number rolls instead of jumping.
    val weight by animateFloatAsState(weightGrams.toFloat(), tween(100, easing = LinearEasing), label = "weight")
    val flow = flowGramsPerSecond.coerceIn(0.0, 99.9)
    val timerColor by animateColorAsState(
        if (timerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "timer",
    )
    val numbers = @Composable {
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
            Text(Format.timer(timerSeconds), style = MaterialTheme.typography.headlineSmall.merge(Tabular), color = timerColor)
            Text(
                "${Format.flow(flow, locale)} ${stringResource(R.string.unit_grams_per_second)}",
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.titleMedium.merge(Tabular),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ratio != null) RatioText(ratio)
        }
    }
    if (ratio == null) {
        Box(modifier, contentAlignment = Alignment.Center) { numbers() }
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
    val outerColor by animateColorAsState(if (ratio.onTarget) colors.tertiary else colors.primary, label = "outer")
    val innerColor by animateColorAsState(if (ratio.onTarget) colors.tertiary else colors.secondary, label = "inner")
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val outer = 14.dp.toPx()
            val o = outer / 2
            val outerSize = Size(size.width - outer, size.height - outer)
            val outerStyle = Stroke(outer, cap = StrokeCap.Round)
            drawArc(colors.surfaceContainerHighest, 0f, 360f, false, Offset(o, o), outerSize, style = outerStyle)
            if (progress > 0.005f) drawArc(outerColor, -90f, 360f * progress, false, Offset(o, o), outerSize, style = outerStyle)
            val stroke = 6.dp.toPx()
            val i = outer + 8.dp.toPx() + stroke / 2
            val innerSize = Size(size.width - 2 * i, size.height - 2 * i)
            val innerStyle = Stroke(stroke, cap = StrokeCap.Round)
            drawArc(colors.surfaceContainerHigh, 0f, 360f, false, Offset(i, i), innerSize, style = innerStyle)
            if (flowFill > 0.005f) drawArc(innerColor, -90f, 360f * flowFill, false, Offset(i, i), innerSize, style = innerStyle)
        }
        numbers()
    }
}

/** "1:1.8 · Target 1:2.0", the ratio in tertiary once it reaches the target. */
@Composable
private fun RatioText(ratio: RatioTarget) {
    val locale = LocalConfiguration.current.locales[0]
    val color by animateColorAsState(
        if (ratio.onTarget) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
        label = "ratio",
    )
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            ratio.ratio?.let { stringResource(R.string.ratio_value, Format.ratio(it, locale)) } ?: "–",
            style = MaterialTheme.typography.titleLarge.merge(Tabular),
            color = color,
        )
        Text(
            stringResource(R.string.target_ratio_value, Format.ratio(ratio.target, locale)),
            Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.labelLarge.merge(Tabular),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
