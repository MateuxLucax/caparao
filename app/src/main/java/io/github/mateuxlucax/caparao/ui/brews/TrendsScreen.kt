package io.github.mateuxlucax.caparao.ui.brews

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.ui.components.label
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.theme.flex

/** Ratio and total time over the last brews of a recipe, oldest on the left (#106). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(viewModel: TrendsViewModel, onClose: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title ?: stringResource(state.key.mode.label), style = MaterialTheme.typography.titleLargeEmphasized) },
                // A recipe's trends keep the mode in view under its name.
                subtitle = { if (state.title != null) Text(stringResource(state.key.mode.label)) },
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
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            values.lastOrNull()?.let { Text(format(it), style = MaterialTheme.typography.headlineSmall.merge(flex(weight = 600))) }
            Spacer(
                Modifier.fillMaxWidth().height(96.dp).drawWithCache {
                    // Everything that grows with the values is built once per size or value change (#320).
                    val left = 36.dp.toPx()
                    val right = size.width - 8.dp.toPx()
                    val top = 4.dp.toPx()
                    val bottom = size.height - 4.dp.toPx()
                    fun x(i: Int) = if (values.size < 2) (left + right) / 2 else left + (right - left) * i / (values.size - 1)
                    fun y(v: Double) = bottom - (bottom - top) * axis.fraction(v).toFloat()
                    val gridWidth = 1.dp.toPx()
                    val gridYs = listOf(y(0.0), y(axis.max))
                    val labels = listOf(0.0, axis.max).mapIndexed { i, tick -> axisLabel(measurer, format(tick), 0f, gridYs[i], labelStyle) }
                    val points = values.mapIndexed { i, v -> Offset(x(i), y(v)) }
                    val path = if (points.size >= 2) curvePath(points) else null
                    val dot = points.lastOrNull()
                    val stroke = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    onDrawBehind {
                        gridYs.forEach { y -> drawLine(grid, Offset(left, y), Offset(right, y), strokeWidth = gridWidth, cap = StrokeCap.Round) }
                        labels.forEach { drawText(it.text, topLeft = it.at) }
                        val progress = drawIn.value
                        if (path != null) drawCurve(path, color, stroke, left, right, progress)
                        // Only the newest brew gets a dot: a ring in the card's colour around a solid centre.
                        if (dot != null && (progress >= 1f || points.size < 2)) {
                            drawCircle(surface, 6.dp.toPx(), dot)
                            drawCircle(color, 4.dp.toPx(), dot)
                        }
                    }
                },
            )
        }
    }
}
