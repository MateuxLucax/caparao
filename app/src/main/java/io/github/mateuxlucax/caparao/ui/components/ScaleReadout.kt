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

/** Flow that fills the ring; a fast espresso or a steady pour stays well under it. */
private const val RING_FULL_FLOW = 10.0

/** The ring is open at the bottom, where the flow reading sits. */
private const val RING_START = 135f
private const val RING_SWEEP = 270f

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/**
 * Weight in the middle of a ring that fills with the flow; the timer under the weight, the flow in the ring's gap.
 * Shared by Home and the live brew.
 */
@Composable
fun ScaleReadout(
    weightGrams: Double,
    flowGramsPerSecond: Double,
    timerSeconds: Int,
    timerRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    // Readings come at 10 Hz: glide between them so the number rolls instead of jumping.
    val weight by animateFloatAsState(weightGrams.toFloat(), tween(100, easing = LinearEasing), label = "weight")
    val flow = flowGramsPerSecond.coerceIn(0.0, 99.9)
    val ring by animateFloatAsState(
        (flow / RING_FULL_FLOW).coerceIn(0.0, 1.0).toFloat(),
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "ring",
    )
    val timerColor by animateColorAsState(
        if (timerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "timer",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.primary
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val style = Stroke(stroke, cap = StrokeCap.Round)
            drawArc(track, RING_START, RING_SWEEP, false, Offset(inset, inset), arcSize, style = style)
            if (ring > 0.005f) drawArc(fill, RING_START, RING_SWEEP * ring, false, Offset(inset, inset), arcSize, style = style)
        }
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
        }
        Text(
            "${Format.flow(flow, locale)} ${stringResource(R.string.unit_grams_per_second)}",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            style = MaterialTheme.typography.titleMedium.merge(Tabular),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
