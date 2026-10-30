package io.github.mateuxlucax.caparao.ui.brews

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.ui.components.label
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.theme.flex

/** Ratio and total time over the last brews of a recipe, oldest on the left (docs/adr/0022-compare-and-trends.md). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(viewModel: TrendsViewModel, onClose: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title ?: stringResource(state.key.mode.label)) },
                navigationIcon = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClose) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.close)) }
                },
            )
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.trends_subtitle, Comparison.TREND_SIZE),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TrendCard(stringResource(R.string.label_ratio), state.brews.map { it.ratio }, minimum = 2.0) { Format.ratio(it, locale) }
            TrendCard(stringResource(R.string.label_time), state.brews.map { it.durationMs / 1000.0 }, minimum = 30.0) { Format.timer(it.toInt()) }
        }
    }
}

/** A small line chart of [values] from 0, the newest value as the title. */
@Composable
private fun TrendCard(title: String, values: List<Double>, minimum: Double, format: (Double) -> String) {
    val axis = ChartScale.axis(values.maxOrNull() ?: 0.0, minimum)
    val color = MaterialTheme.colorScheme.primary
    val grid = chartGridColor()
    val labelStyle = chartLabelStyle()
    val measurer = rememberTextMeasurer()
    val drawIn = rememberDrawIn(values)
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            values.lastOrNull()?.let { Text(format(it), style = MaterialTheme.typography.headlineSmall.merge(flex(weight = 600))) }
            Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                val left = 36.dp.toPx()
                val right = size.width - 8.dp.toPx()
                val top = 4.dp.toPx()
                val bottom = size.height - 4.dp.toPx()
                fun x(i: Int) = if (values.size < 2) (left + right) / 2 else left + (right - left) * i / (values.size - 1)
                fun y(v: Double) = bottom - (bottom - top) * axis.fraction(v).toFloat()
                listOf(0.0, axis.max).forEach { tick ->
                    drawLine(grid, Offset(left, y(tick)), Offset(right, y(tick)), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
                    label(measurer, format(tick), Offset(0f, y(tick)), labelStyle)
                }
                val points = values.mapIndexed { i, v -> Offset(x(i), y(v)) }
                if (points.size >= 2) drawCurve(points, color, 3.5.dp.toPx(), drawIn.value)
                // Only the newest brew gets a dot: a ring in the card's colour around a solid centre.
                points.lastOrNull()?.takeIf { drawIn.value >= 1f || points.size < 2 }?.let {
                    drawCircle(surface, 6.dp.toPx(), it)
                    drawCircle(color, 4.dp.toPx(), it)
                }
            }
        }
    }
}
