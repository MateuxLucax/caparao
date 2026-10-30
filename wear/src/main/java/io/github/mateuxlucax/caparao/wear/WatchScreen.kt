package io.github.mateuxlucax.caparao.wear

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.dynamicColorScheme
import io.github.mateuxlucax.caparao.watch.WatchGuide
import io.github.mateuxlucax.caparao.watch.WatchSnapshot
import io.github.mateuxlucax.caparao.watch.WatchState
import io.github.mateuxlucax.caparao.watch.WatchStepKind

/**
 * Wear Material 3 Expressive (docs/adr/0025-material-3-expressive.md), one thing at a time: the weight large and the
 * timer under it. In Scale mode a tap on the timer starts or pauses it, a long press resets it, and tare is the edge
 * button hugging the bottom of the round screen; a brew hides it. A pour to a target draws its progress around the
 * edge, colored by the pace.
 */
@Composable
fun WatchScreen(
    snapshot: WatchSnapshot?,
    onTare: () -> Unit,
    onTimer: () -> Unit,
    onReset: () -> Unit,
    timeSource: TimeSource = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat()),
) {
    MaterialTheme(colorScheme = dynamicColorScheme(LocalContext.current) ?: ColorScheme()) {
        AppScaffold(timeText = { TimeText(timeSource = timeSource) }) {
            val tare = snapshot != null && snapshot.state != WatchState.DISCONNECTED && snapshot.state != WatchState.BREWING
            ScreenScaffold {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    pourProgress(snapshot)?.let { progress ->
                        val pace = snapshot?.guide?.let { paceLabel(it) }
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxSize().semantics { if (pace != null) stateDescription = pace },
                            colors = ProgressIndicatorDefaults.colors(indicatorColor = paceColor(snapshot?.guide?.paceMs)),
                        )
                    }
                    // Clear of the edge button when it shows.
                    Box(
                        Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, bottom = if (tare) 28.dp else 0.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            snapshot == null -> Message(stringResource(R.string.open_on_phone))
                            snapshot.state == WatchState.DISCONNECTED -> Message(stringResource(R.string.scale_disconnected))
                            else -> Readout(snapshot, onTimer, onReset)
                        }
                    }
                    if (tare) {
                        EdgeButton(onClick = onTare, modifier = Modifier.align(Alignment.BottomCenter), buttonSize = EdgeButtonSize.Small) {
                            Icon(painterResource(R.drawable.ic_tare), contentDescription = stringResource(R.string.tare))
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
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val label = when (snapshot.state) {
            WatchState.SCALE -> null
            WatchState.READY -> stringResource(R.string.state_ready)
            WatchState.DONE -> stringResource(R.string.state_done)
            else -> snapshot.guide?.let { stepLabel(it) } ?: WatchFormat.flow(snapshot.flowGramsPerSecond)
        }
        if (label != null) Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(WatchFormat.grams(snapshot.weightGrams), fontSize = 52.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        val time = WatchFormat.time(snapshot.elapsedMs)
        val timeStyle = MaterialTheme.typography.titleLarge
        if (snapshot.state == WatchState.SCALE) {
            // Scale mode's timer, the phone Home's play and reset folded into the timer itself; a brew runs its own clock.
            Row(
                Modifier.clip(CircleShape).combinedClickable(
                    onClickLabel = stringResource(if (snapshot.timerRunning) R.string.timer_pause else R.string.timer_start),
                    onLongClickLabel = stringResource(R.string.timer_reset),
                    onLongClick = onReset,
                    onClick = onTimer,
                ).padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(if (snapshot.timerRunning) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                Text(time, style = timeStyle, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Text(time, style = timeStyle, color = MaterialTheme.colorScheme.primary)
        }
        // The pace shows as the arc's color; only the countdown to the next step stays in words.
        val due = snapshot.guide?.dueInMs?.takeIf { snapshot.state == WatchState.BREWING }
        if (due != null) {
            Text(stringResource(R.string.guide_due_in, WatchFormat.seconds(due)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "2/5 · 150 g" for a step with a target, "3/5 · Wait" without, or "Last pour done" once every step is. */
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
    val step = guide.targetGrams?.let { WatchFormat.wholeGrams(it) } ?: name
    return stringResource(R.string.step_of, guide.index + 1, guide.count, step)
}

/** The pour's pace against the recipe, in words for TalkBack; ±5 s counts as on time (ADR 0019). */
@Composable
private fun paceLabel(guide: WatchGuide): String? {
    val pace = guide.paceMs ?: return null
    return when {
        pace < -ON_TIME_MS -> stringResource(R.string.pace_ahead, WatchFormat.seconds(-pace))
        pace > ON_TIME_MS -> stringResource(R.string.pace_behind, WatchFormat.seconds(pace))
        else -> stringResource(R.string.pace_on_time)
    }
}

/** The arc's color: the theme's primary on time, amber ahead, error behind. */
@Composable
private fun paceColor(pace: Long?): Color = when {
    pace == null -> MaterialTheme.colorScheme.primary
    pace < -ON_TIME_MS -> AHEAD
    pace > ON_TIME_MS -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}

private val AHEAD = Color(0xFFFFB300)

private const val ON_TIME_MS = 5_000L
