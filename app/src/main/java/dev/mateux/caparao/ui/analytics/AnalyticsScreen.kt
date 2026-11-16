package dev.mateux.caparao.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
private fun Metric(label: Int, value: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(stringResource(label))
    Text(value)
}
