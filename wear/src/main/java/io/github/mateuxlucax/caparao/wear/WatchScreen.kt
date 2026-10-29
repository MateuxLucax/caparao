package io.github.mateuxlucax.caparao.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import io.github.mateuxlucax.caparao.watch.WatchSnapshot
import io.github.mateuxlucax.caparao.watch.WatchState

/**
 * The mode or the flow on top, the weight large in the middle, the timer under it; tare, and in Scale mode the timer's
 * start or pause and reset, at the bottom.
 */
@Composable
fun WatchScreen(snapshot: WatchSnapshot?, onTare: () -> Unit, onTimer: () -> Unit, onReset: () -> Unit) {
    MaterialTheme {
        ScreenScaffold {
            Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                when {
                    snapshot == null -> Message(stringResource(R.string.open_on_phone))
                    snapshot.state == WatchState.DISCONNECTED -> Message(stringResource(R.string.scale_disconnected))
                    else -> Readout(snapshot, onTare, onTimer, onReset)
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Readout(snapshot: WatchSnapshot, onTare: () -> Unit, onTimer: () -> Unit, onReset: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val label = when (snapshot.state) {
            WatchState.SCALE -> stringResource(R.string.mode_scale)
            WatchState.READY -> stringResource(R.string.state_ready)
            WatchState.DONE -> stringResource(R.string.state_done)
            else -> WatchFormat.flow(snapshot.flowGramsPerSecond)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(WatchFormat.grams(snapshot.weightGrams), fontSize = 44.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(WatchFormat.time(snapshot.elapsedMs), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onTare) {
                Icon(painterResource(R.drawable.ic_tare), contentDescription = stringResource(R.string.tare))
            }
            // Scale mode's timer, the same three buttons as the phone's Home; a brew runs its own clock.
            if (snapshot.state == WatchState.SCALE) {
                FilledIconButton(onClick = onTimer) {
                    Icon(
                        painterResource(if (snapshot.timerRunning) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                        contentDescription = stringResource(if (snapshot.timerRunning) R.string.timer_pause else R.string.timer_start),
                    )
                }
                FilledTonalIconButton(onClick = onReset) {
                    Icon(painterResource(R.drawable.ic_replay), contentDescription = stringResource(R.string.timer_reset))
                }
            }
        }
    }
}
