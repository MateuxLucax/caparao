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
import io.github.mateuxlucax.caparao.watch.WatchGuide
import io.github.mateuxlucax.caparao.watch.WatchSnapshot
import io.github.mateuxlucax.caparao.watch.WatchState
import io.github.mateuxlucax.caparao.watch.WatchStepKind

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
            else -> snapshot.guide?.let { stepLabel(it) } ?: WatchFormat.flow(snapshot.flowGramsPerSecond)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(WatchFormat.grams(snapshot.weightGrams), fontSize = 44.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(WatchFormat.time(snapshot.elapsedMs), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        val hint = snapshot.guide?.takeIf { snapshot.state == WatchState.BREWING }?.let { paceLabel(it) }
        if (hint != null) Text(hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** "2/5 · Pour to 150 g", "3/5 · Wait", or "Last pour done" once every step is. */
@Composable
private fun stepLabel(guide: WatchGuide): String {
    if (guide.index >= guide.count || guide.kind == null) return stringResource(R.string.guide_all_done)
    val name = when (guide.kind) {
        WatchStepKind.BLOOM -> stringResource(R.string.step_bloom)
        WatchStepKind.POUR -> stringResource(R.string.step_pour)
        WatchStepKind.WAIT -> stringResource(R.string.step_wait)
        WatchStepKind.SWIRL -> stringResource(R.string.step_swirl)
        null -> ""
    }
    val target = guide.targetGrams
    val step = if (target != null) stringResource(R.string.step_to_target, name, WatchFormat.wholeGrams(target)) else name
    return stringResource(R.string.step_of, guide.index + 1, guide.count, step)
}

/** The countdown to the next step, or the pour's pace against the recipe; ±5 s counts as on time (ADR 0019). */
@Composable
private fun paceLabel(guide: WatchGuide): String? {
    guide.dueInMs?.let { return stringResource(R.string.guide_due_in, WatchFormat.seconds(it)) }
    val pace = guide.paceMs ?: return null
    return when {
        pace < -ON_TIME_MS -> stringResource(R.string.pace_ahead, WatchFormat.seconds(-pace))
        pace > ON_TIME_MS -> stringResource(R.string.pace_behind, WatchFormat.seconds(pace))
        else -> stringResource(R.string.pace_on_time)
    }
}

private const val ON_TIME_MS = 5_000L
