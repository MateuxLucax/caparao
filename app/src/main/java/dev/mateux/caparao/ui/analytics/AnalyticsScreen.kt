package dev.mateux.caparao.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mateux.caparao.R
import dev.mateux.caparao.ui.theme.SectionHeading

@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.analytics_this_week), style = MaterialTheme.typography.titleMedium.merge(SectionHeading))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric(R.string.analytics_brews, state.thisWeek.brews.toString())
                Metric(R.string.analytics_coffee, "${state.thisWeek.grams.toInt()} g")
                Metric(R.string.analytics_caffeine, stringResource(R.string.analytics_about_mg, state.thisWeek.caffeineMg))
                Metric(R.string.analytics_today, stringResource(R.string.analytics_about_mg, state.todayCaffeineMg))
            }
        }
        Text(stringResource(R.string.analytics_today), style = MaterialTheme.typography.titleMedium.merge(SectionHeading))
        if (state.caffeine.todayBrews == 0) {
            Text(stringResource(R.string.analytics_nothing_today))
        } else {
            val caffeine = state.caffeine
            val lastCoffee = caffeine.lastCoffeeByMinutes?.let { stringResource(R.string.analytics_last_coffee, "%02d:%02d".format(Locale.ROOT, it / 60, it % 60)) }
                ?: stringResource(R.string.analytics_over_50)
            Text(stringResource(R.string.analytics_caffeine_left, caffeine.bedtimeMg))
            Text(lastCoffee)
            val chartDescription = stringResource(R.string.analytics_chart_description, caffeine.bedtimeMg, lastCoffee) + " " + stringResource(R.string.analytics_chart_reference)
            Box(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = chartDescription }) {
                CaffeineChart(caffeine, Modifier.fillMaxSize())
            }
        }
        Text(stringResource(R.string.analytics_estimate_note), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.analytics_last_week), style = MaterialTheme.typography.titleMedium.merge(SectionHeading))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric(R.string.analytics_brews, state.lastWeek.brews.toString())
                Metric(R.string.analytics_coffee, "${state.lastWeek.grams.toInt()} g")
                Metric(R.string.analytics_caffeine, stringResource(R.string.analytics_about_mg, state.lastWeek.caffeineMg))
            }
        }
    }
}

@Composable
private fun CaffeineChart(estimate: CaffeineEstimate, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val reference = MaterialTheme.colorScheme.outline
    Canvas(modifier.clip(RoundedCornerShape(12.dp))) {
        val maxHour = estimate.points.lastOrNull()?.hour?.coerceAtLeast(23.0) ?: 23.0
        val minHour = 6.0
        val maxMg = (estimate.points.maxOfOrNull { it.mg } ?: 100.0).coerceAtLeast(100.0)
        fun x(hour: Double) = size.width * ((hour - minHour) / (maxHour - minHour)).toFloat()
        fun y(mg: Double) = size.height * (1f - (mg / maxMg).toFloat())
        drawLine(reference, androidx.compose.ui.geometry.Offset(0f, y(50.0)), androidx.compose.ui.geometry.Offset(size.width, y(50.0)), 1.dp.toPx())
        val path = Path()
        estimate.points.forEachIndexed { index, point ->
            if (index == 0) path.moveTo(x(point.hour), y(point.mg)) else path.lineTo(x(point.hour), y(point.mg))
        }
        if (estimate.points.size > 1) drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        estimate.brewHours.forEach { hour -> drawLine(color, androidx.compose.ui.geometry.Offset(x(hour), size.height - 7.dp.toPx()), androidx.compose.ui.geometry.Offset(x(hour), size.height), 2.dp.toPx()) }
    }
}

@Composable
private fun Metric(label: Int, value: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(stringResource(label))
    Text(value)
}
