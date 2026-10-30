package io.github.mateuxlucax.caparao.ui.components

import android.content.ClipData
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.ScaleError
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.ui.AppActions
import io.github.mateuxlucax.caparao.ui.format.hint
import io.github.mateuxlucax.caparao.ui.format.label
import io.github.mateuxlucax.caparao.ui.format.title
import kotlinx.coroutines.launch

/** Connection state with its icon on the left and, when connected, the battery on the right (Home wireframe). */
@Composable
fun ScaleStatusRow(state: ScaleState, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            is ScaleState.Connected -> Icon(painterResource(R.drawable.ic_bluetooth_connected), null, tint = MaterialTheme.colorScheme.primary)
            ScaleState.Searching, ScaleState.Connecting -> LoadingIndicator(Modifier.size(20.dp))
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

/** The connection as one top-bar icon (primary once connected, error on failure, muted otherwise), named for screen readers. */
@Composable
fun ScaleStatusIcon(state: ScaleState, modifier: Modifier = Modifier) {
    val (icon, tint) = when (state) {
        is ScaleState.Connected -> R.drawable.ic_bluetooth_connected to MaterialTheme.colorScheme.primary
        ScaleState.Searching, ScaleState.Connecting -> R.drawable.ic_bluetooth_searching to MaterialTheme.colorScheme.onSurfaceVariant
        is ScaleState.Failed -> R.drawable.ic_error to MaterialTheme.colorScheme.error
        ScaleState.Idle -> R.drawable.ic_bluetooth_disabled to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Icon(painterResource(icon), stringResource(state.label), modifier, tint = tint)
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

/** The connection log, to paste into an issue. It never holds the scale's address (ScaleClient). */
@Composable
fun CopyLogButton(log: List<String>) {
    if (log.isEmpty()) return
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = {
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Caparaó log", log.joinToString("\n"))))
            copied = true
        }
    }) {
        Icon(painterResource(if (copied) R.drawable.ic_check_circle else R.drawable.ic_content_copy), null, Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(stringResource(if (copied) R.string.log_copied else R.string.copy_log))
    }
}
