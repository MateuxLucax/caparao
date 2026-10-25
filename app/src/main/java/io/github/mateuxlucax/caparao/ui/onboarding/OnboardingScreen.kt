package io.github.mateuxlucax.caparao.ui.onboarding

import android.content.ClipData
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.ui.AppActions
import io.github.mateuxlucax.caparao.ui.components.PourAnimation
import io.github.mateuxlucax.caparao.ui.components.ScaleErrorCard
import io.github.mateuxlucax.caparao.ui.components.ScaleStatusRow
import io.github.mateuxlucax.caparao.ui.components.SearchingLogo
import io.github.mateuxlucax.caparao.ui.format.Format
import kotlinx.coroutines.launch

/** The onboarding steps, in order. The server step joins in stage 5. */
enum class OnboardingStep { WELCOME, PERMISSIONS, SCALE }

/** First launch: welcome, Bluetooth permission, and connecting to the scale (Onboarding wireframe). */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, actions: AppActions, onFinished: () -> Unit) {
    // Outside the Scaffold, so the surface supplies the background and the text colors.
    Surface(Modifier.fillMaxSize()) { Steps(viewModel, actions, onFinished) }
}

@Composable
private fun Steps(viewModel: OnboardingViewModel, actions: AppActions, onFinished: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
        StepIndicator(step)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            when (step) {
                OnboardingStep.WELCOME -> Welcome()
                OnboardingStep.PERMISSIONS -> Permissions(actions) { step = OnboardingStep.SCALE }
                OnboardingStep.SCALE -> ConnectScale(viewModel, actions)
            }
        }
        if (step == OnboardingStep.WELCOME) {
            Button(onClick = { step = OnboardingStep.PERMISSIONS }, Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.get_started))
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { step = OnboardingStep.entries[step.ordinal - 1] }) { Text(stringResource(R.string.back)) }
                if (step == OnboardingStep.PERMISSIONS) {
                    TextButton(onClick = { step = OnboardingStep.SCALE }) { Text(stringResource(R.string.skip)) }
                } else {
                    val connected = viewModel.uiState.collectAsStateWithLifecycle().value.scale is ScaleState.Connected
                    val finish = {
                        viewModel.finish()
                        onFinished()
                    }
                    if (connected) {
                        Button(onClick = finish) { Text(stringResource(R.string.finish)) }
                    } else {
                        TextButton(onClick = finish) { Text(stringResource(R.string.skip)) }
                    }
                }
            }
        }
    }
}

/** One segment per step; the done and current ones filled. */
@Composable
private fun StepIndicator(step: OnboardingStep) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OnboardingStep.entries.forEach { entry ->
            val color by animateColorAsState(
                if (entry <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                label = "step",
            )
            Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun Welcome() {
    PourAnimation()
    Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Text(
        stringResource(R.string.welcome_text),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Permissions(actions: AppActions, onGranted: () -> Unit) {
    val scope = rememberCoroutineScope()
    var denied by rememberSaveable { mutableStateOf(false) }
    Icon(painterResource(R.drawable.ic_bluetooth_searching), null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
    Text(stringResource(R.string.permissions_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(
        stringResource(R.string.permissions_text),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Button(onClick = {
        scope.launch {
            if (actions.requestBluetoothPermissions()) onGranted() else denied = true
        }
    }) { Text(stringResource(R.string.allow_bluetooth)) }
    if (denied) {
        Text(stringResource(R.string.hint_permission_missing), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        OutlinedButton(onClick = actions.openAppSettings) { Text(stringResource(R.string.open_app_settings)) }
    }
}

@Composable
private fun ConnectScale(viewModel: OnboardingViewModel, actions: AppActions) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Coming from the permission step: connect now that Bluetooth may be allowed.
    LaunchedEffect(Unit) { if (state.scale is ScaleState.Failed || state.scale == ScaleState.Idle) viewModel.retry() }
    val scale = state.scale
    SearchingLogo(searching = scale == ScaleState.Searching || scale == ScaleState.Connecting)
    Text(stringResource(R.string.connect_scale_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    ScaleStatusRow(scale)
    when (scale) {
        is ScaleState.Connected -> {
            val reading = state.reading
            Text(
                "${Format.grams(reading?.weightGrams ?: 0.0, LocalConfiguration.current.locales[0])} ${stringResource(R.string.unit_grams)}",
                style = MaterialTheme.typography.displayMedium,
            )
            Text(stringResource(R.string.connect_scale_live), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        is ScaleState.Failed -> {
            ScaleErrorCard(scale.error, scale.retrying, actions, viewModel::retry)
            DebugLog(state.log)
        }
        else -> Text(stringResource(R.string.connect_scale_text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** The connection log, to paste into an issue. It never holds the scale's address (ScaleClient). */
@Composable
private fun DebugLog(log: List<String>) {
    if (log.isEmpty()) return
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by rememberSaveable { mutableStateOf(false) }
    Spacer(Modifier.height(4.dp))
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
