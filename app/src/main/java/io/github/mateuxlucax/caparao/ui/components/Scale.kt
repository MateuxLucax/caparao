package io.github.mateuxlucax.caparao.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.ScaleError
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.ui.AppActions
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.format.hint
import io.github.mateuxlucax.caparao.ui.format.label
import io.github.mateuxlucax.caparao.ui.format.title

/** Connection state with its icon on the left and, when connected, the battery on the right (Home wireframe). */
@Composable
fun ScaleStatusRow(state: ScaleState, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            is ScaleState.Connected -> Icon(painterResource(R.drawable.ic_bluetooth_connected), null, tint = MaterialTheme.colorScheme.primary)
            ScaleState.Searching, ScaleState.Connecting -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            is ScaleState.Failed -> Icon(painterResource(R.drawable.ic_error), null, tint = MaterialTheme.colorScheme.error)
            ScaleState.Idle -> Icon(painterResource(R.drawable.ic_bluetooth_disabled), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(state.label),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (state is ScaleState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        val battery = (state as? ScaleState.Connected)?.battery
        if (battery != null) {
            Icon(painterResource(R.drawable.ic_battery_full), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.battery_percent, battery.percent), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** What went wrong, what to do, and the button that does it. [onRetry] restarts the connection. */
@Composable
fun ScaleErrorCard(error: ScaleError, retrying: Boolean, actions: AppActions, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(error.title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(stringResource(error.hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            when {
                error == ScaleError.BLUETOOTH_OFF -> Button(onClick = actions.enableBluetooth) { Text(stringResource(R.string.turn_on_bluetooth)) }
                error == ScaleError.PERMISSION_MISSING -> Button(onClick = actions.openAppSettings) { Text(stringResource(R.string.open_app_settings)) }
                retrying -> Text(stringResource(R.string.retrying), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                else -> TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

/** The logo with rings pulsing out of it while [searching]; still once connected. */
@Composable
fun SearchingLogo(searching: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "search")
    val pulse by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "pulse")
    val ring = MaterialTheme.colorScheme.primary
    Box(modifier.size(200.dp), contentAlignment = Alignment.Center) {
        if (searching) {
            Box(
                Modifier
                    .size(96.dp)
                    .scale(1f + pulse * 1.0f)
                    .alpha(1f - pulse)
                    .clip(CircleShape)
                    .drawBehind { drawCircle(ring.copy(alpha = 0.35f)) },
            )
        }
        Logo(96.dp)
    }
}

/** Welcome animation: a drop falls into the cup on the scale while the weight below counts up. */
@Composable
fun BrewAnimation(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "brew")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val drop = MaterialTheme.colorScheme.primary
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 160.dp, height = 180.dp), contentAlignment = Alignment.BottomCenter) {
            // Falls during the first half of each cycle, then disappears into the cup.
            val fall = (t * 2f).coerceAtMost(1f)
            Box(
                Modifier
                    .offset(x = (-8).dp, y = (-150 + fall * 90).dp)
                    .size(12.dp)
                    .alpha(if (t < 0.5f) 1f else 0f)
                    .clip(CircleShape)
                    .drawBehind { drawCircle(drop) },
            )
            Logo(120.dp)
        }
        Text(
            "${Format.grams(t * 36.0, LocalConfiguration.current.locales[0])} g",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
