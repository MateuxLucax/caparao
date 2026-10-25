package io.github.mateuxlucax.caparao.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.ui.AppActions
import io.github.mateuxlucax.caparao.ui.components.ScaleErrorCard
import io.github.mateuxlucax.caparao.ui.components.ScaleStatusRow
import io.github.mateuxlucax.caparao.ui.components.icon
import io.github.mateuxlucax.caparao.ui.components.label
import io.github.mateuxlucax.caparao.ui.format.Format

/** Live scale readout and the two brew modes (Home wireframe). The modes open in stage 3. */
@Composable
fun HomeScreen(viewModel: HomeViewModel, actions: AppActions, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val connected = state.scale is ScaleState.Connected
    val reading = state.reading
    Column(
        modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScaleStatusRow(state.scale)
        (state.scale as? ScaleState.Failed)?.let { ScaleErrorCard(it.error, it.retrying, actions, viewModel::retry) }
        Text(
            "${Format.flow(reading?.flowGramsPerSecond?.coerceIn(0.0, 99.9) ?: 0.0, locale)} ${stringResource(R.string.unit_grams_per_second)}",
            modifier = Modifier.align(Alignment.End),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${Format.grams(reading?.weightGrams ?: 0.0, locale)} ${stringResource(R.string.unit_grams)}",
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = if (connected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
            Text(
                Format.timer(reading?.timerSeconds ?: 0),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = viewModel::tare, enabled = connected) { Text(stringResource(R.string.tare)) }
                FilledTonalButton(onClick = viewModel::toggleTimer, enabled = connected) {
                    Text(stringResource(if (state.timerRunning) R.string.timer_pause else R.string.timer_start))
                }
                TextButton(onClick = viewModel::resetTimer, enabled = connected) { Text(stringResource(R.string.timer_reset)) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BrewMode.entries.forEach { mode -> ModeButton(mode, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ModeButton(mode: BrewMode, modifier: Modifier) {
    ElevatedCard(onClick = {}, enabled = false, modifier = modifier.height(120.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(mode.icon), contentDescription = null, Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(mode.label), style = MaterialTheme.typography.titleMedium)
        }
    }
}
