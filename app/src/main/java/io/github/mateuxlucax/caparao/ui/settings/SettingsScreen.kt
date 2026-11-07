package io.github.mateuxlucax.caparao.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticIncompleteReason
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticRecorderState
import io.github.mateuxlucax.caparao.data.local.VibrationTarget
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.ui.AppActions
import io.github.mateuxlucax.caparao.ui.components.BodyText
import io.github.mateuxlucax.caparao.ui.components.ChoiceGroup
import io.github.mateuxlucax.caparao.ui.components.CopyLogButton
import io.github.mateuxlucax.caparao.ui.components.ScaleErrorCard
import io.github.mateuxlucax.caparao.ui.components.ScaleStatusRow
import io.github.mateuxlucax.caparao.ui.components.SectionCard
import io.github.mateuxlucax.caparao.ui.components.ServerForm
import io.github.mateuxlucax.caparao.ui.components.ShareFile
import io.github.mateuxlucax.caparao.ui.components.StatusLine
import io.github.mateuxlucax.caparao.ui.components.label
import io.github.mateuxlucax.caparao.ui.components.rememberLocalNetworkAccess
import io.github.mateuxlucax.caparao.ui.format.Format
import kotlinx.coroutines.launch

/** Server, permissions and scale, in the wireframe's order, then the target ratios, the guide's alerts and the colors. */
@Composable
fun SettingsScreen(
    server: ServerViewModel,
    scale: ScaleStatusViewModel,
    targets: TargetRatioViewModel,
    export: ExportViewModel,
    diagnostics: DiagnosticsViewModel,
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
        SectionCard(stringResource(R.string.section_diagnostics)) { DiagnosticsSection(diagnostics) }
        SectionCard(stringResource(R.string.section_guide_alerts)) { GuideAlertsSection(targets) }
        SectionCard(stringResource(R.string.section_data)) { DataSection(export) }
        SectionCard(stringResource(R.string.section_colors)) { ColorsSection(targets) }
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
            TextButton(shapes = ButtonDefaults.shapes(), onClick = {
                viewModel.remove()
                url = ""
                token = ""
            }) { Text(stringResource(R.string.remove_server)) }
        } else {
            Spacer(Modifier.width(1.dp))
        }
        Button(
            shapes = ButtonDefaults.shapes(),
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
        Button(shapes = ButtonDefaults.shapes(), onClick = {
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
        TextButton(shapes = ButtonDefaults.shapes(), onClick = viewModel::reconnect) { Text(stringResource(R.string.reconnect)) }
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
    if (vibrate) {
        var on by rememberSaveable { mutableStateOf(viewModel.guideVibrationOn) }
        ChoiceGroup(
            VibrationTarget.entries,
            selected = on,
            onSelect = { on = it; viewModel.guideVibrationOn = it },
            label = {
                stringResource(
                    when (it) {
                        VibrationTarget.PHONE -> R.string.vibrate_on_phone
                        VibrationTarget.WATCH -> R.string.vibrate_on_watch
                        VibrationTarget.BOTH -> R.string.vibrate_on_both
                    },
                )
            },
        )
    }
    SwitchRow(stringResource(R.string.guide_sound), sound) { sound = it; viewModel.guideSound = it }
}

/** The whole history in a zip: contract JSON, `brews.csv` and, when asked, `samples.csv` (#76). */
@Composable
private fun DataSection(viewModel: ExportViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var includeSamples by rememberSaveable { mutableStateOf(true) }
    BodyText(stringResource(R.string.section_data_text))
    SwitchRow(stringResource(R.string.export_include_samples), includeSamples) { includeSamples = it }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        if (state.failed) {
            Text(stringResource(R.string.export_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        } else {
            Spacer(Modifier.width(1.dp))
        }
        Button(shapes = ButtonDefaults.shapes(), onClick = { viewModel.export(includeSamples) }, enabled = !state.working) {
            Text(stringResource(if (state.working) R.string.export_working else R.string.export))
        }
    }
    ShareFile(state.file, viewModel::shared)
}

/** Opt-in capture of the live BLE link, kept locally until the user shares the file. */
@Composable
private fun DiagnosticsSection(viewModel: DiagnosticsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recorder = state.recorder
    val status = if (state.starting) R.string.diagnostics_starting else when (recorder) {
        DiagnosticRecorderState.Idle -> null
        DiagnosticRecorderState.Starting -> R.string.diagnostics_starting
        is DiagnosticRecorderState.Recording -> R.string.diagnostics_recording
        is DiagnosticRecorderState.Finalizing -> R.string.diagnostics_saving
        is DiagnosticRecorderState.Ready -> if (recorder.complete) R.string.diagnostics_ready else R.string.diagnostics_incomplete
        is DiagnosticRecorderState.Failed -> R.string.diagnostics_start_failed
    }
    if (status != null) {
        Text(
            stringResource(status),
            style = MaterialTheme.typography.bodyMedium,
            color = if (recorder is DiagnosticRecorderState.Failed || recorder is DiagnosticRecorderState.Ready && !recorder.complete) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
    if (recorder is DiagnosticRecorderState.Ready && !recorder.complete) {
        Text(
            stringResource(incompleteReasonString(recorder.reason)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (state.startFailed && recorder !is DiagnosticRecorderState.Failed) {
        Text(stringResource(R.string.diagnostics_start_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
    if (state.stopFailed) {
        Text(stringResource(R.string.diagnostics_stop_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
    if (state.shareFailed) {
        Text(stringResource(R.string.diagnostics_share_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!state.starting) {
            when (recorder) {
                is DiagnosticRecorderState.Recording -> Button(shapes = ButtonDefaults.shapes(), onClick = viewModel::stop) {
                    Text(stringResource(R.string.diagnostics_stop))
                }
                DiagnosticRecorderState.Starting, is DiagnosticRecorderState.Finalizing -> Unit
                else -> {
                    Button(shapes = ButtonDefaults.shapes(), onClick = viewModel::start) {
                        Text(stringResource(R.string.diagnostics_start))
                    }
                    if (recorder is DiagnosticRecorderState.Ready) {
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = viewModel::share) {
                            Text(stringResource(R.string.diagnostics_share))
                        }
                    }
                }
            }
        }
    }
    ShareFile(state.shareFile, viewModel::shared, title = R.string.diagnostics_share_title)
}

private fun incompleteReasonString(reason: DiagnosticIncompleteReason?): Int = when (reason) {
    DiagnosticIncompleteReason.SCALE_DISCONNECTED -> R.string.diagnostics_reason_disconnected
    DiagnosticIncompleteReason.SESSION_SIZE_LIMIT -> R.string.diagnostics_reason_size_limit
    DiagnosticIncompleteReason.WRITER_OVERRUN -> R.string.diagnostics_reason_writer_overrun
    DiagnosticIncompleteReason.STORAGE_ERROR -> R.string.diagnostics_reason_storage
    DiagnosticIncompleteReason.PROCESS_INTERRUPTED, null -> R.string.diagnostics_reason_interrupted
}

/** System colors (Material You) or Caparaó's green; the theme follows the choice at once. */
@Composable
private fun ColorsSection(viewModel: TargetRatioViewModel) {
    var brand by rememberSaveable { mutableStateOf(viewModel.brandColors) }
    val options = listOf(false to R.string.colors_system, true to R.string.colors_brand)
    ChoiceGroup(
        options.map { it.first },
        selected = brand,
        onSelect = { brand = it; viewModel.brandColors = it },
        label = { value -> stringResource(options.first { it.first == value }.second) },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, onValueChange = onChange, role = Role.Switch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = null,
            thumbContent = if (checked) {
                { Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(SwitchDefaults.IconSize)) }
            } else {
                null
            },
        )
    }
}
