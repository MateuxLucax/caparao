package io.github.mateuxlucax.caparao.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.ui.AppActions
import io.github.mateuxlucax.caparao.ui.components.ScaleErrorCard
import io.github.mateuxlucax.caparao.ui.components.ScaleReadout
import io.github.mateuxlucax.caparao.ui.components.ScaleStatusRow
import io.github.mateuxlucax.caparao.ui.components.icon
import io.github.mateuxlucax.caparao.ui.components.label

/** Live scale readout and the two brew modes (Home wireframe); a mode opens once the scale is connected. */
@Composable
fun HomeScreen(viewModel: HomeViewModel, actions: AppActions, onBrew: (BrewMode) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connected = state.scale is ScaleState.Connected
    Column(
        modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScaleStatusRow(state.scale)
        (state.scale as? ScaleState.Failed)?.let { ScaleErrorCard(it.error, it.retrying, actions, viewModel::retry) }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Readout(state, Modifier.widthIn(max = 320.dp).fillMaxWidth(0.85f).alpha(if (connected) 1f else 0.45f))
        }
        Controls(state, connected, viewModel)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BrewMode.entries.forEach { mode -> ModeButton(mode, connected, Modifier.weight(1f)) { onBrew(mode) } }
        }
    }
}

@Composable
private fun Readout(state: HomeUiState, modifier: Modifier) {
    val reading = state.reading
    ScaleReadout(
        weightGrams = reading?.weightGrams ?: 0.0,
        flowGramsPerSecond = reading?.flowGramsPerSecond ?: 0.0,
        timerSeconds = reading?.timerSeconds ?: 0,
        timerRunning = state.timerRunning,
        modifier = modifier,
    )
}

/** Tare, start or pause, reset: round buttons with their names under them, the timer one bigger. */
@Composable
private fun Controls(state: HomeUiState, connected: Boolean, viewModel: HomeViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        RoundAction(R.drawable.ic_tare, stringResource(R.string.tare), 64.dp, connected, primary = false, onClick = viewModel::tare)
        RoundAction(
            if (state.timerRunning) R.drawable.ic_pause else R.drawable.ic_play_arrow,
            stringResource(if (state.timerRunning) R.string.timer_pause else R.string.timer_start),
            80.dp,
            connected,
            primary = true,
            onClick = viewModel::toggleTimer,
        )
        RoundAction(R.drawable.ic_replay, stringResource(R.string.timer_reset), 64.dp, connected, primary = false, onClick = viewModel::resetTimer)
    }
}

@Composable
private fun RoundAction(@DrawableRes icon: Int, label: String, size: Dp, enabled: Boolean, primary: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val content: @Composable () -> Unit = { Icon(painterResource(icon), contentDescription = label, Modifier.size(size * 0.4f)) }
        if (primary) {
            FilledIconButton(onClick, Modifier.size(size), enabled = enabled, content = content)
        } else {
            FilledTonalIconButton(onClick, Modifier.size(size), enabled = enabled, content = content)
        }
        Spacer(Modifier.height(6.dp))
        // The button already carries the label for screen readers.
        Text(label, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ModeButton(mode: BrewMode, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, enabled = enabled, modifier = modifier.height(96.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(mode.icon), contentDescription = null, Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(stringResource(mode.label), style = MaterialTheme.typography.titleMedium)
        }
    }
}
