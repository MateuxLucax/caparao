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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import io.github.mateuxlucax.caparao.ui.components.icon
import io.github.mateuxlucax.caparao.ui.components.label

/** The brew modes on top, then the live scale readout and its controls (Home wireframe); a mode opens once the scale is connected. */
@Composable
fun HomeScreen(viewModel: HomeViewModel, actions: AppActions, onBrew: (BrewMode) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connected = state.scale is ScaleState.Connected
    Column(
        modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ModeSelector(connected, onBrew)
        // Say why the modes are off; a failure already explains itself in the card below.
        if (!connected && state.scale !is ScaleState.Failed) {
            Text(stringResource(R.string.scale_needed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        (state.scale as? ScaleState.Failed)?.let { ScaleErrorCard(it.error, it.retrying, actions, viewModel::retry) }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Readout(state, Modifier.widthIn(max = 400.dp).fillMaxWidth().alpha(if (connected) 1f else 0.45f))
        }
        Controls(state, connected, viewModel)
        Spacer(Modifier.height(4.dp))
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
        spacious = true,
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
            FilledIconButton(onClick, modifier = Modifier.size(size), enabled = enabled, shapes = IconButtonDefaults.shapes(), content = content)
        } else {
            FilledTonalIconButton(onClick, modifier = Modifier.size(size), enabled = enabled, shapes = IconButtonDefaults.shapes(), content = content)
        }
        Spacer(Modifier.height(6.dp))
        // The button already carries the label for screen readers.
        Text(label, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Scale (this screen, no mode) or a brew mode. Home always shows Scale selected: picking a mode opens its brew on top,
 * and closing the brew comes back here. The modes wait for the scale.
 */
@Composable
private fun ModeSelector(connected: Boolean, onBrew: (BrewMode) -> Unit) {
    val options = listOf<BrewMode?>(null) + BrewMode.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == null,
                onClick = { if (mode != null) onBrew(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                enabled = mode == null || connected,
                label = { Text(stringResource(mode?.label ?: R.string.mode_scale)) },
            )
        }
    }
}
