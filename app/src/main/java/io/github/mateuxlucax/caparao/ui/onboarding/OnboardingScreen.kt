package io.github.mateuxlucax.caparao.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.ui.AppActions
import io.github.mateuxlucax.caparao.ui.components.CopyLogButton
import io.github.mateuxlucax.caparao.ui.components.MorphingShape
import io.github.mateuxlucax.caparao.ui.components.PourAnimation
import io.github.mateuxlucax.caparao.ui.components.ScaleErrorCard
import io.github.mateuxlucax.caparao.ui.components.ScaleStatusRow
import io.github.mateuxlucax.caparao.ui.components.SearchingLogo
import io.github.mateuxlucax.caparao.ui.components.ServerForm
import io.github.mateuxlucax.caparao.ui.components.rememberLocalNetworkAccess
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.settings.ServerViewModel
import io.github.mateuxlucax.caparao.ui.theme.Motion
import kotlinx.coroutines.launch

/** The onboarding steps, in order. */
enum class OnboardingStep { WELCOME, PERMISSIONS, SCALE, SERVER }

/** First launch: welcome, Bluetooth permission, connecting to the scale and an optional server (Onboarding wireframe). */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, server: ServerViewModel, actions: AppActions, onFinished: () -> Unit) {
    // Outside the Scaffold, so the surface supplies the background and the text colors.
    Surface(Modifier.fillMaxSize()) { Steps(viewModel, server, actions, onFinished) }
}

@Composable
private fun Steps(viewModel: OnboardingViewModel, server: ServerViewModel, actions: AppActions, onFinished: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    var url by rememberSaveable { mutableStateOf(server.savedUrl) }
    var token by rememberSaveable { mutableStateOf(server.savedToken) }
    val scope = rememberCoroutineScope()
    val localNetworkAccess = rememberLocalNetworkAccess(server::isLocalNetworkBlocked, actions)
    val finish = {
        viewModel.finish()
        onFinished()
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(24.dp)) {
        StepIndicator(step)
        val density = LocalDensity.current
        AnimatedContent(
            step,
            Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { Motion.springAxisX(targetState.ordinal > initialState.ordinal, density) },
            label = "step",
        ) { shown ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                when (shown) {
                    OnboardingStep.WELCOME -> Welcome()
                    OnboardingStep.PERMISSIONS -> Permissions(actions) { step = OnboardingStep.SCALE }
                    OnboardingStep.SCALE -> ConnectScale(viewModel, actions)
                    OnboardingStep.SERVER -> ServerStep(server, url, { url = it }, token, { token = it }, actions)
                }
            }
        }
        if (step == OnboardingStep.WELCOME) {
            Button(shapes = ButtonDefaults.shapes(), onClick = { step = OnboardingStep.PERMISSIONS }, modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight)) {
                Text(stringResource(R.string.get_started))
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { step = OnboardingStep.entries[step.ordinal - 1] }) { Text(stringResource(R.string.back)) }
                when (step) {
                    OnboardingStep.PERMISSIONS -> TextButton(shapes = ButtonDefaults.shapes(), onClick = { step = OnboardingStep.SCALE }) { Text(stringResource(R.string.skip)) }
                    OnboardingStep.SCALE -> {
                        val connected = viewModel.uiState.collectAsStateWithLifecycle().value.scale is ScaleState.Connected
                        val next = { step = OnboardingStep.SERVER }
                        if (connected) {
                            Button(shapes = ButtonDefaults.shapes(), onClick = next) { Text(stringResource(R.string.next)) }
                        } else {
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = next) { Text(stringResource(R.string.skip)) }
                        }
                    }
                    else -> if (url.isBlank() && token.isBlank()) {
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = finish) { Text(stringResource(R.string.skip)) }
                    } else {
                        Button(
                            shapes = ButtonDefaults.shapes(),
                            onClick = {
                                // Ask for local network access now, while the user is here; the upload job can't ask.
                                scope.launch {
                                    localNetworkAccess(url.trim())
                                    if (server.save(url, token)) finish()
                                }
                            },
                            enabled = server.canSave(url, token),
                        ) { Text(stringResource(R.string.finish)) }
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
                Motion.standard(),
                label = "step",
            )
            Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(color))
        }
    }
}

/** The step's icon on its own Material shape, which springs in from a circle. */
@Composable
private fun StepPicture(step: OnboardingStep, @DrawableRes icon: Int) {
    MorphingShape(step.ordinal, Modifier.size(120.dp), MaterialTheme.colorScheme.primaryContainer) {
        Icon(painterResource(icon), null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
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
    StepPicture(OnboardingStep.PERMISSIONS, R.drawable.ic_bluetooth_searching)
    Text(stringResource(R.string.permissions_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(
        stringResource(R.string.permissions_text),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Button(shapes = ButtonDefaults.shapes(), onClick = {
        scope.launch {
            if (actions.requestBluetoothPermissions()) onGranted() else denied = true
        }
    }) { Text(stringResource(R.string.allow_bluetooth)) }
    if (denied) {
        Text(stringResource(R.string.hint_permission_missing), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = actions.openAppSettings) { Text(stringResource(R.string.open_app_settings)) }
    }
}

@Composable
private fun ServerStep(
    server: ServerViewModel,
    url: String,
    onUrlChange: (String) -> Unit,
    token: String,
    onTokenChange: (String) -> Unit,
    actions: AppActions,
) {
    StepPicture(OnboardingStep.SERVER, R.drawable.ic_cloud_upload)
    Text(stringResource(R.string.server_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(
        stringResource(R.string.server_text),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    ServerForm(server, url, onUrlChange, token, onTokenChange, actions)
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
            CopyLogButton(state.log)
        }
        else -> Text(stringResource(R.string.connect_scale_text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
