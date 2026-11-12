package dev.mateux.caparao.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mateux.caparao.model.BrewSample
import dev.mateux.caparao.ui.theme.animationsOff
import dev.mateux.caparao.ui.theme.flex

/**
 * Weight (left axis) and flow (right axis) over time, drawn on a Canvas (#97). The axes
 * come from [ChartScale].
 */
@Composable
fun BrewChart(samples: List<BrewSample>, durationMs: Long, weightLabel: String, flowLabel: String, modifier: Modifier = Modifier) {
    val weightAxis = ChartScale.axis(samples.maxOfOrNull { it.weightGrams } ?: 0.0, minimum = 10.0)
    val flowAxis = ChartScale.axis(samples.maxOfOrNull { it.flowGramsPerSecond } ?: 0.0, minimum = 2.0)
    val timeAxis = ChartScale.timeAxis(durationMs)
    val weightColor = MaterialTheme.colorScheme.primary
    val flowColor = MaterialTheme.colorScheme.tertiary
    val grid = chartGridColor()
    val labelStyle = chartLabelStyle()
    val measurer = rememberTextMeasurer()
    val drawIn = rememberDrawIn(samples.size)
    Column(modifier) {
        Spacer(
            Modifier.fillMaxWidth().height(220.dp).drawWithCache {
                // Everything that grows with the samples is built once per size or sample change (#320).
                val left = 36.dp.toPx()
                val right = size.width - 36.dp.toPx()
                val top = 8.dp.toPx()
                val bottom = size.height - 20.dp.toPx()
                fun x(ms: Long) = left + (right - left) * timeAxis.fraction(ms / 1000.0).toFloat()
                fun yWeight(g: Double) = bottom - (bottom - top) * weightAxis.fraction(g).toFloat()
                fun yFlow(f: Double) = bottom - (bottom - top) * flowAxis.fraction(f).toFloat()
                val gridWidth = 1.dp.toPx()
                val gridYs = weightAxis.ticks.map { yWeight(it) }
                val weightLabels = weightAxis.ticks.mapIndexed { i, tick -> axisLabel(measurer, tick.toInt().toString(), 0f, gridYs[i], labelStyle) }
                val flowLabels = flowAxis.ticks.mapIndexedNotNull { i, tick ->
                    // Same number of gridlines rarely; label the flow axis at its own positions without lines.
                    if (i > 0) axisLabel(measurer, trim(tick), right + 6.dp.toPx(), yFlow(tick), labelStyle) else null
                }
                val timeLabels = timeAxis.ticks.map { tick ->
                    axisLabel(measurer, "${tick.toInt()}s", x((tick * 1000).toLong()) - 8.dp.toPx(), bottom + 12.dp.toPx(), labelStyle)
                }
                val flowPath = if (samples.size >= 2) curvePath(samples.map { Offset(x(it.atMs), yFlow(it.flowGramsPerSecond)) }) else null
                val weightPath = if (samples.size >= 2) curvePath(samples.map { Offset(x(it.atMs), yWeight(it.weightGrams)) }) else null
                val flowStroke = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val weightStroke = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                onDrawBehind {
                    gridYs.forEach { y -> drawLine(grid, Offset(left, y), Offset(right, y), strokeWidth = gridWidth, cap = StrokeCap.Round) }
                    weightLabels.forEach { drawText(it.text, topLeft = it.at) }
                    flowLabels.forEach { drawText(it.text, topLeft = it.at) }
                    timeLabels.forEach { drawText(it.text, topLeft = it.at) }
                    val progress = drawIn.value
                    if (flowPath != null && weightPath != null) {
                        drawCurve(flowPath, flowColor, flowStroke, left, right, progress)
                        drawCurve(weightPath, weightColor, weightStroke, left, right, progress)
                    }
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Legend(weightColor, weightLabel)
            Spacer(Modifier.width(16.dp))
            Legend(flowColor, flowLabel)
        }
    }
}

internal fun trim(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

/** Axis labels in Roboto Flex, slightly condensed with tabular figures, so the numbers line up and stay quiet. */
@Composable
internal fun chartLabelStyle(): TextStyle = flex(weight = 500, width = 90).copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** A few faint horizontal lines and nothing else: the curves carry the chart. */
@Composable
internal fun chartGridColor(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

/**
 * 0 to 1 on a soft spring, restarted when [key] changes: the share of each curve drawn, so lines draw in from the left.
 * Compose jumps straight to 1 when animations are off.
 */
@Composable
internal fun rememberDrawIn(key: Any): Animatable<Float, *> {
    // With animations off the curve is drawn whole from the start (#183).
    val still = animationsOff()
    val progress = remember(key) { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(key) { if (!still) progress.animateTo(1f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessVeryLow)) }
    return progress
}

/** An axis label measured once per chart size, with the top-left corner it is drawn at. */
internal class AxisLabel(val text: TextLayoutResult, val at: Offset)

/** Measures [text] centred vertically on [y], so a cached label draws without measuring again. */
internal fun axisLabel(measurer: TextMeasurer, text: String, x: Float, y: Float, style: TextStyle): AxisLabel {
    val layout = measurer.measure(text, style)
    return AxisLabel(layout, Offset(x, y - layout.size.height / 2f))
}

/** The polyline through [points], built once and reused by every frame of the draw-in. */
internal fun curvePath(points: List<Offset>): Path = Path().apply {
    moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
}

/**
 * Draws a cached [path]; while [progress] is below 1 only the part left of `left + (right - left) * progress`
 * shows. Clipping a cached path instead of cutting a new segment keeps each draw-in frame allocation-free (#320).
 */
internal fun DrawScope.drawCurve(path: Path, color: Color, style: Stroke, left: Float, right: Float, progress: Float) {
    if (progress >= 1f) {
        drawPath(path, color, style = style)
    } else {
        clipRect(right = left + (right - left) * progress.coerceIn(0f, 1f)) {
            drawPath(path, color, style = style)
        }
    }
}

@Composable
internal fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}
