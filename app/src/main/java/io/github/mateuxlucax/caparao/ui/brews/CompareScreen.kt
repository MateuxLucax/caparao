package io.github.mateuxlucax.caparao.ui.brews

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.ui.format.Format

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** One colour per brew, in pick order. */
@Composable
private fun curveColors(): List<Color> = with(MaterialTheme.colorScheme) { listOf(primary, tertiary, secondary, error) }

/** The weight curves of two to four brews on one chart, with a summary table below (docs/adr/0022-compare-and-trends.md). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(viewModel: CompareViewModel, onClose: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compare_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.close)) }
                },
            )
        },
    ) { padding ->
        if (state.loading) return@Scaffold
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CompareChart(state.brews)
            CompareTable(state.brews)
        }
    }
}

/** Weight over time, aligned at each brew's start; the axes hold the largest of all curves. */
@Composable
fun CompareChart(brews: List<ComparedBrew>, modifier: Modifier = Modifier) {
    val weightAxis = ChartScale.axis(brews.flatMap { it.samples }.maxOfOrNull { it.weightGrams } ?: 0.0, minimum = 10.0)
    val timeAxis = ChartScale.timeAxis(brews.maxOfOrNull { it.brew.durationMs } ?: 0)
    val colors = curveColors()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().height(220.dp)) {
        val left = 36.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        fun x(ms: Long) = left + (right - left) * timeAxis.fraction(ms / 1000.0).toFloat()
        fun y(g: Double) = bottom - (bottom - top) * weightAxis.fraction(g).toFloat()
        weightAxis.ticks.forEach { tick ->
            drawLine(grid, Offset(left, y(tick)), Offset(right, y(tick)), strokeWidth = 1f)
            label(measurer, tick.toInt().toString(), Offset(0f, y(tick)), labelStyle)
        }
        timeAxis.ticks.forEach { tick ->
            label(measurer, "${tick.toInt()}s", Offset(x((tick * 1000).toLong()) - 8.dp.toPx(), bottom + 12.dp.toPx()), labelStyle)
        }
        brews.forEachIndexed { i, compared ->
            if (compared.samples.size >= 2) {
                drawCurve(compared.samples.map { Offset(x(it.atMs), y(it.weightGrams)) }, colors[i % colors.size], 2.5.dp.toPx())
            }
        }
    }
}

/** One column per brew, headed by its colour and date: dose, yield, ratio, time, average flow and rating. */
@Composable
private fun CompareTable(brews: List<ComparedBrew>) {
    val locale = LocalConfiguration.current.locales[0]
    val g = stringResource(R.string.unit_grams)
    val colors = curveColors()
    val mode = brews.firstOrNull()?.brew?.mode
    val rows = listOf<Pair<String, (ComparedBrew) -> String>>(
        stringResource(R.string.label_dose) to { "${Format.grams(it.brew.doseGrams, locale)} $g" },
        stringResource(if (mode == BrewMode.FILTER) R.string.label_water else R.string.label_yield) to
            { "${Format.grams(it.brew.yieldGrams, locale)} $g" },
        stringResource(R.string.label_ratio) to { Format.ratio(it.brew.ratio, locale).let { r -> "1:$r" } },
        stringResource(R.string.label_time) to { Format.timer((it.brew.durationMs / 1000).toInt()) },
        stringResource(R.string.label_flow_avg) to { Format.flow(it.brew.flowAvgGramsPerSecond, locale) },
        stringResource(R.string.brew_rating) to { it.brew.rating?.toString() ?: "–" },
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("", Modifier.width(88.dp))
            brews.forEachIndexed { i, compared ->
                Column(Modifier.weight(1f)) {
                    Legend(colors[i % colors.size], Format.dateTime(compared.brew.startedAt, locale))
                    compared.brew.recipeName?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        rows.forEach { (name, value) ->
            Row(Modifier.fillMaxWidth()) {
                Text(name, Modifier.width(88.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                brews.forEach {
                    Text(value(it), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.merge(Tabular), textAlign = TextAlign.Start)
                }
            }
        }
    }
}
