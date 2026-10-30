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
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.dynamicColorScheme
import androidx.wear.compose.material3.Text
import io.github.mateuxlucax.caparao.watch.WatchGuide
import io.github.mateuxlucax.caparao.watch.WatchSnapshot
import io.github.mateuxlucax.caparao.watch.WatchState
import io.github.mateuxlucax.caparao.watch.WatchStepKind

/**
 * Wear Material 3 Expressive (docs/adr/0025-material-3-expressive.md): the system's colors, the time curved on top, the
 * mode or the flow, the weight large, the timer; in Scale mode the timer's start or pause and reset, and tare as the
 * edge button hugging the bottom of the round screen. A pour to a target draws its progress around the edge.
 */
@Composable
fun WatchScreen(snapshot: WatchSnapshot?, onTare: () -> Unit, onTimer: () -> Unit, onReset: () -> Unit) {
    MaterialTheme(colorScheme = dynamicColorScheme(LocalContext.current) ?: ColorScheme()) {
        AppScaffold {
            val live = snapshot != null && snapshot.state != WatchState.DISCONNECTED
            ScreenScaffold(
                edgeButton = {
                    if (live) {
                        EdgeButton(onClick = onTare, buttonSize = EdgeButtonSize.Small) {
                            Icon(painterResource(R.drawable.ic_tare), contentDescription = stringResource(R.string.tare))
                        }
                    }
                },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    pourProgress(snapshot)?.let { CircularProgressIndicator(progress = { it }, modifier = Modifier.fillMaxSize()) }
                    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                        when {
                            snapshot == null -> Message(stringResource(R.string.open_on_phone))
                            snapshot.state == WatchState.DISCONNECTED -> Message(stringResource(R.string.scale_disconnected))
                            else -> Readout(snapshot, onTimer, onReset)
                        }
                    }
                }
            }
        }
    }
}

/** The weight against the current pour's target, while a guided brew is on a step that has one. */
private fun pourProgress(snapshot: WatchSnapshot?): Float? {
    if (snapshot?.state != WatchState.BREWING) return null
    val target = snapshot.guide?.targetGrams?.takeIf { it > 0f } ?: return null
    return (snapshot.weightGrams / target).coerceIn(0f, 1f)
}

@Composable
private fun Message(text: String) {
    Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Readout(snapshot: WatchSnapshot, onTimer: () -> Unit, onReset: () -> Unit) {
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
        // Scale mode's timer, the same buttons as the phone's Home; a brew runs its own clock. Pressed, they morph shape.
        if (snapshot.state == WatchState.SCALE) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(onClick = onTimer, shapes = IconButtonDefaults.animatedShapes()) {
                    Icon(
                        painterResource(if (snapshot.timerRunning) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                        contentDescription = stringResource(if (snapshot.timerRunning) R.string.timer_pause else R.string.timer_start),
                    )
                }
                FilledTonalIconButton(onClick = onReset, shapes = IconButtonDefaults.animatedShapes()) {
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
