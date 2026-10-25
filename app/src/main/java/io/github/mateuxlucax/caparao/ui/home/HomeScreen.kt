package io.github.mateuxlucax.caparao.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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

/** Flow that fills the ring; a fast espresso or a steady pour stays well under it. */
private const val RING_FULL_FLOW = 10.0

/** The ring is open at the bottom, where the flow reading sits. */
private const val RING_START = 135f
private const val RING_SWEEP = 270f

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** Live scale readout and the two brew modes (Home wireframe). The modes open in stage 3. */
@Composable
fun HomeScreen(viewModel: HomeViewModel, actions: AppActions, modifier: Modifier = Modifier) {
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
            BrewMode.entries.forEach { mode -> ModeButton(mode, Modifier.weight(1f)) }
        }
    }
}

/** Weight in the middle of a ring that fills with the flow; the timer under the weight, the flow in the ring's gap. */
@Composable
private fun Readout(state: HomeUiState, modifier: Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val reading = state.reading
    // Readings come at 10 Hz: glide between them so the number rolls instead of jumping.
    val weight by animateFloatAsState((reading?.weightGrams ?: 0.0).toFloat(), tween(100, easing = LinearEasing), label = "weight")
    val flow = (reading?.flowGramsPerSecond ?: 0.0).coerceIn(0.0, 99.9)
    val ring by animateFloatAsState(
        (flow / RING_FULL_FLOW).coerceIn(0.0, 1.0).toFloat(),
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "ring",
    )
    val timerColor by animateColorAsState(
        if (state.timerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "timer",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.primary
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val style = Stroke(stroke, cap = StrokeCap.Round)
            drawArc(track, RING_START, RING_SWEEP, false, Offset(inset, inset), arcSize, style = style)
            if (ring > 0.005f) drawArc(fill, RING_START, RING_SWEEP * ring, false, Offset(inset, inset), arcSize, style = style)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Format.grams(weight.toDouble(), locale),
                    style = Tabular,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.unit_grams),
                    Modifier.padding(start = 4.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Format.timer(reading?.timerSeconds ?: 0), style = MaterialTheme.typography.headlineSmall.merge(Tabular), color = timerColor)
        }
        Text(
            "${Format.flow(flow, locale)} ${stringResource(R.string.unit_grams_per_second)}",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            style = MaterialTheme.typography.titleMedium.merge(Tabular),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
private fun ModeButton(mode: BrewMode, modifier: Modifier) {
    ElevatedCard(onClick = {}, enabled = false, modifier = modifier.height(96.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(mode.icon), contentDescription = null, Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(stringResource(mode.label), style = MaterialTheme.typography.titleMedium)
        }
    }
}
