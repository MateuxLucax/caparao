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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.caparao.model.BrewSample

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
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
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
                drawLine(grid, Offset(left, y), Offset(right, y), strokeWidth = 1f)
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
                drawCurve(samples.map { Offset(x(it.atMs), yFlow(it.flowGramsPerSecond)) }, flowColor, 2.dp.toPx())
                drawCurve(samples.map { Offset(x(it.atMs), yWeight(it.weightGrams)) }, weightColor, 3.dp.toPx())
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

private fun trim(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

private fun androidx.compose.ui.graphics.drawscope.DrawScope.label(measurer: TextMeasurer, text: String, at: Offset, style: androidx.compose.ui.text.TextStyle) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(at.x, at.y - layout.size.height / 2f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCurve(points: List<Offset>, color: Color, width: Float) {
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}
