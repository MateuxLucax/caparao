package io.github.mateuxlucax.caparao.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.ui.AppActions
import io.github.mateuxlucax.caparao.ui.components.BodyText
import io.github.mateuxlucax.caparao.ui.components.CopyLogButton
import io.github.mateuxlucax.caparao.ui.components.ScaleErrorCard
import io.github.mateuxlucax.caparao.ui.components.ScaleStatusRow
import io.github.mateuxlucax.caparao.ui.components.SectionCard
import io.github.mateuxlucax.caparao.ui.components.ServerForm
import io.github.mateuxlucax.caparao.ui.components.StatusLine
import io.github.mateuxlucax.caparao.ui.components.label
import io.github.mateuxlucax.caparao.ui.components.rememberLocalNetworkAccess
import io.github.mateuxlucax.caparao.ui.format.Format
import kotlinx.coroutines.launch

/** Server, permissions and scale, in the wireframe's order, then the target ratios and the guide's alerts. */
@Composable
fun SettingsScreen(
    server: ServerViewModel,
    scale: ScaleStatusViewModel,
    targets: TargetRatioViewModel,
    actions: AppActions, versionName: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(stringResource(R.string.section_server)) { ServerSection(server, actions) }
        SectionCard(stringResource(R.string.section_permissions)) { PermissionsSection(actions) }
        SectionCard(stringResource(R.string.section_scale)) { ScaleSection(scale, actions) }
        SectionCard(stringResource(R.string.section_target_ratio)) { TargetRatioSection(targets) }
        SectionCard(stringResource(R.string.section_guide_alerts)) { GuideAlertsSection(targets) }
        Text(
            stringResource(R.string.version, versionName),
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The editable server: the shared form, Save and Remove server. */
@Composable
private fun ServerSection(viewModel: ServerViewModel, actions: AppActions) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf(viewModel.savedUrl) }
    var token by rememberSaveable { mutableStateOf(viewModel.savedToken) }
    val scope = rememberCoroutineScope()
    val localNetworkAccess = rememberLocalNetworkAccess(viewModel::isLocalNetworkBlocked, actions)
    val changed = url.trim() != viewModel.savedUrl || token.trim() != viewModel.savedToken
    BodyText(stringResource(R.string.section_server_text))
    ServerForm(viewModel, url, { url = it }, token, { token = it }, actions)
    if (state.justSaved) StatusLine(true, stringResource(R.string.server_saved))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        if (state.configured) {
            TextButton(onClick = {
                viewModel.remove()
                url = ""
                token = ""
            }) { Text(stringResource(R.string.remove_server)) }
        } else {
            Spacer(Modifier.width(1.dp))
        }
        Button(
            onClick = {
                // Ask for local network access now, while the user is here; the worker can't ask.
                scope.launch {
                    localNetworkAccess(url.trim())
                    viewModel.save(url, token)
                }
            },
            enabled = changed && viewModel.canSave(url, token),
        ) { Text(stringResource(R.string.save)) }
    }
}

/** Nearby devices (Bluetooth): granted or not, re-checked when the app comes back from the system settings. */
@Composable
private fun PermissionsSection(actions: AppActions) {
    var granted by remember { mutableStateOf(actions.bluetoothPermissionGranted()) }
    LifecycleResumeEffect(Unit) {
        granted = actions.bluetoothPermissionGranted()
        onPauseOrDispose {}
    }
    val scope = rememberCoroutineScope()
    BodyText(stringResource(R.string.section_permissions_text))
    StatusLine(granted, stringResource(if (granted) R.string.permission_granted else R.string.error_permission_missing))
    if (!granted) {
        Button(onClick = {
            scope.launch {
                // After a "don't ask again", the request returns at once without a dialog: the settings are the way.
                granted = actions.requestBluetoothPermissions()
                if (!granted) actions.openAppSettings()
            }
        }) { Text(stringResource(R.string.allow_bluetooth)) }
    }
}

/** The connection with what went wrong, Reconnect and the debug log. */
@Composable
private fun ScaleSection(viewModel: ScaleStatusViewModel, actions: AppActions) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BodyText(stringResource(R.string.section_scale_text))
    ScaleStatusRow(state.scale)
    (state.scale as? ScaleState.Failed)?.let { ScaleErrorCard(it.error, it.retrying, actions, viewModel::reconnect) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = viewModel::reconnect) { Text(stringResource(R.string.reconnect)) }
        CopyLogButton(state.log)
    }
}

/** Vibration and sound for the guided brew; the visual cue is always on. */
@Composable
private fun GuideAlertsSection(viewModel: TargetRatioViewModel) {
    BodyText(stringResource(R.string.guide_alerts_text))
    var vibrate by rememberSaveable { mutableStateOf(viewModel.guideVibration) }
    var sound by rememberSaveable { mutableStateOf(viewModel.guideSound) }
    SwitchRow(stringResource(R.string.guide_vibration), vibrate) { vibrate = it; viewModel.guideVibration = it }
    SwitchRow(stringResource(R.string.guide_sound), sound) { sound = it; viewModel.guideSound = it }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, onValueChange = onChange, role = Role.Switch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** One 1:X field per mode, saved as soon as it holds a valid ratio. */
@Composable
private fun TargetRatioSection(viewModel: TargetRatioViewModel) {
    val locale = LocalConfiguration.current.locales[0]
    BodyText(stringResource(R.string.section_target_ratio_text))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BrewMode.entries.forEach { mode ->
            var text by rememberSaveable { mutableStateOf(Format.ratio(viewModel.ratio(mode), locale)) }
            val valid = TargetRatioViewModel.parse(text) != null
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(4)
                    viewModel.save(mode, text)
                },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(mode.label)) },
                prefix = { Text("1:") },
                isError = !valid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
    }
}
