package io.github.mateuxlucax.caparao.ui.brews

import androidx.compose.foundation.Canvas
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mateuxlucax.caparao.model.BrewSample
import io.github.mateuxlucax.caparao.ui.theme.flex

/**
 * Weight (left axis) and flow (right axis) over time, drawn on a Canvas (docs/adr/0013-canvas-charts.md). The axes
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
        Canvas(Modifier.fillMaxWidth().height(220.dp)) {
            val left = 36.dp.toPx()
            val right = size.width - 36.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 20.dp.toPx()
            fun x(ms: Long) = left + (right - left) * timeAxis.fraction(ms / 1000.0).toFloat()
            fun yWeight(g: Double) = bottom - (bottom - top) * weightAxis.fraction(g).toFloat()
            fun yFlow(f: Double) = bottom - (bottom - top) * flowAxis.fraction(f).toFloat()
            weightAxis.ticks.forEach { tick ->
                val y = yWeight(tick)
                drawLine(grid, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
                label(measurer, tick.toInt().toString(), Offset(0f, y), labelStyle)
            }
            flowAxis.ticks.forEachIndexed { i, tick ->
                // Same number of gridlines rarely; label the flow axis at its own positions without lines.
                if (i > 0) label(measurer, trim(tick), Offset(right + 6.dp.toPx(), yFlow(tick)), labelStyle)
            }
            timeAxis.ticks.forEach { tick ->
                label(measurer, "${tick.toInt()}s", Offset(x((tick * 1000).toLong()) - 8.dp.toPx(), bottom + 12.dp.toPx()), labelStyle)
            }
            if (samples.size >= 2) {
                drawCurve(samples.map { Offset(x(it.atMs), yFlow(it.flowGramsPerSecond)) }, flowColor, 2.5.dp.toPx(), drawIn.value)
                drawCurve(samples.map { Offset(x(it.atMs), yWeight(it.weightGrams)) }, weightColor, 4.dp.toPx(), drawIn.value)
            }
        }
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
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessVeryLow)) }
    return progress
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.label(measurer: TextMeasurer, text: String, at: Offset, style: TextStyle) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(at.x, at.y - layout.size.height / 2f))
}

/** A thick polyline with round caps and joins, [progress] of it drawn from the first point. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCurve(points: List<Offset>, color: Color, width: Float, progress: Float = 1f) {
    var path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    if (progress < 1f) {
        val measure = PathMeasure().apply { setPath(path, false) }
        path = Path().also { measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), it, true) }
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
internal fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}
