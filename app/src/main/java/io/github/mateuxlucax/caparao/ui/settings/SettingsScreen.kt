package io.github.mateuxlucax.caparao.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import android.widget.Toast
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.data.diagnostics.CaptureIncompleteReason
import io.github.mateuxlucax.caparao.data.diagnostics.CaptureWriterState
import io.github.mateuxlucax.caparao.data.local.VibrationTarget
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.ui.components.AppActions
import io.github.mateuxlucax.caparao.ui.components.ChoiceGroup
import io.github.mateuxlucax.caparao.ui.components.CopyLogButton
import io.github.mateuxlucax.caparao.ui.components.ScaleErrorCard
import io.github.mateuxlucax.caparao.ui.components.ScaleStatusRow
import io.github.mateuxlucax.caparao.ui.server.ServerForm
import io.github.mateuxlucax.caparao.ui.components.ShareFile
import io.github.mateuxlucax.caparao.ui.components.StatusLine
import io.github.mateuxlucax.caparao.ui.server.ServerCheck
import io.github.mateuxlucax.caparao.ui.server.ServerViewModel
import io.github.mateuxlucax.caparao.ui.server.rememberLocalNetworkAccess
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.format.label
import io.github.mateuxlucax.caparao.ui.format.title
import io.github.mateuxlucax.caparao.ui.format.message
import io.github.mateuxlucax.caparao.ui.theme.Motion
import io.github.mateuxlucax.caparao.ui.theme.animationsOff
import kotlinx.coroutines.launch

enum class SettingsDetail { Scale, Server, Diagnostics }

/** Everyday controls stay on the list; longer controls open a Navigation 3 entry. */
@Composable
fun SettingsScreen(
    server: ServerViewModel,
    scale: ScaleStatusViewModel,
    targets: TargetRatioViewModel,
    export: ExportViewModel,
    diagnostics: CaptureViewModel,
    actions: AppActions, versionName: String,
    onOpen: (SettingsDetail) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scaleState by scale.uiState.collectAsStateWithLifecycle()
    val sync by server.syncStatus.collectAsStateWithLifecycle()
    val capture by diagnostics.uiState.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val noBrowser = stringResource(R.string.settings_no_browser)
    var permissionGranted by remember { mutableStateOf(actions.bluetoothPermissionGranted()) }
    LifecycleResumeEffect(Unit) {
        permissionGranted = actions.bluetoothPermissionGranted()
        onPauseOrDispose {}
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.settings_brewing), style = MaterialTheme.typography.titleSmall)
        SettingsLink(stringResource(R.string.section_scale), when {
            !permissionGranted -> stringResource(R.string.settings_allow_nearby)
            scaleState.scale is ScaleState.Failed -> stringResource((scaleState.scale as ScaleState.Failed).error.title)
            scaleState.scale is ScaleState.Connected -> stringResource(R.string.scale_connected) +
                ((scaleState.scale as ScaleState.Connected).battery?.let { " · " + stringResource(R.string.battery_percent, it.percent) } ?: "")
            else -> stringResource(scaleState.scale.label)
        }) { onOpen(SettingsDetail.Scale) }
        GuideAlertsSection(targets)
        Text(stringResource(R.string.settings_your_data), style = MaterialTheme.typography.titleSmall)
        SettingsLink(stringResource(R.string.section_server), when {
            !sync.serverConfigured -> stringResource(R.string.settings_only_phone)
            sync.lastError != null -> stringResource(sync.lastError!!.kind.message)
            sync.lastSyncMs > 0 -> stringResource(R.string.last_sync, Format.dateTime(sync.lastSyncMs, locale))
            else -> stringResource(R.string.nothing_synced_yet)
        }) { onOpen(SettingsDetail.Server) }
        if (diagnostics.available) SettingsLink(stringResource(R.string.section_diagnostics), stringResource(when {
            capture.capture is CaptureWriterState.Recording -> R.string.capture_running
            capture.optedIn -> R.string.settings_on
            else -> R.string.settings_off
        })) { onOpen(SettingsDetail.Diagnostics) }
        DataSection(export)
        Text(stringResource(R.string.settings_app), style = MaterialTheme.typography.titleSmall)
        SettingsLink(stringResource(R.string.settings_about, versionName), null) {
            runCatching { uriHandler.openUri("https://github.com/MateuxLucax/caparao") }
                .onFailure { Toast.makeText(context, noBrowser, Toast.LENGTH_SHORT).show() }
        }
    }
}

@Composable
private fun SettingsLink(title: String, status: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = status?.let { { Text(it) } },
        trailingContent = { Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null) },
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDetailScreen(
    detail: SettingsDetail, server: ServerViewModel, scale: ScaleStatusViewModel,
    diagnostics: CaptureViewModel, actions: AppActions, onClose: () -> Unit,
) {
    val title = stringResource(when (detail) {
        SettingsDetail.Scale -> R.string.section_scale
        SettingsDetail.Server -> R.string.section_server
        SettingsDetail.Diagnostics -> R.string.section_diagnostics
    })
    Scaffold(topBar = {
        TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleLargeEmphasized) }, navigationIcon = {
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClose) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
            }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (detail) {
                SettingsDetail.Scale -> { ScaleSection(scale, actions); PermissionsSection(actions) }
                SettingsDetail.Server -> ServerSection(server, actions)
                SettingsDetail.Diagnostics -> if (diagnostics.available) DiagnosticsSection(diagnostics)
            }
        }
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
                // Check the host, then ask for local network access now, while the user is here; the worker can't ask.
                scope.launch {
                    if (!viewModel.checkHost(url, ServerCheck.Save)) return@launch
                    localNetworkAccess(url.trim())
                    viewModel.save(url, token)
                }
            },
            enabled = changed && viewModel.canSave(url, token) && state.checking == null && !state.urlRefused,
        ) { Text(stringResource(if (state.checking == ServerCheck.Save) R.string.server_checking else R.string.save)) }
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
    var vibrate by rememberSaveable { mutableStateOf(viewModel.guideVibration) }
    var sound by rememberSaveable { mutableStateOf(viewModel.guideSound) }
    SwitchRow(stringResource(R.string.guide_vibration), vibrate) { vibrate = it; viewModel.guideVibration = it }
    AnimatedVisibility(vibrate, enter = fadeIn(Motion.standardOrSnap(animationsOff())) + expandVertically(Motion.standardOrSnap(animationsOff())), exit = fadeOut(Motion.standardOrSnap(animationsOff())) + shrinkVertically(Motion.standardOrSnap(animationsOff()))) {
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
    SwitchRow(stringResource(R.string.export_include_samples), includeSamples) { includeSamples = it }
    if (state.failed) Text(stringResource(R.string.export_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    TextButton(onClick = { viewModel.export(includeSamples) }, enabled = !state.working) {
        Text(stringResource(if (state.working) R.string.export_working else R.string.export))
    }
    ShareFile(state.file?.uri, state.file?.mimeType, viewModel::shared)
}

/** Opt-in diagnostics upload and capture of the next brew. */
@Composable
private fun DiagnosticsSection(viewModel: CaptureViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val capture = state.capture
    val still = animationsOff()
    val optInLabel = stringResource(R.string.diagnostics_on)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(optInLabel, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(
            modifier = Modifier.testTag("diagnostics-opt-in").semantics { contentDescription = optInLabel },
            checked = state.optedIn,
            onCheckedChange = viewModel::setOptedIn,
        )
    }
    Text(stringResource(if (state.optedIn) R.string.diagnostics_text else R.string.diagnostics_off_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    AnimatedVisibility(
        visible = state.optedIn,
        enter = fadeIn(Motion.standardOrSnap(still)) + expandVertically(Motion.standardOrSnap(still)),
        exit = fadeOut(Motion.standardOrSnap(still)) + shrinkVertically(Motion.standardOrSnap(still)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.capture_title), style = MaterialTheme.typography.titleMedium)
            val status = if (state.starting) R.string.diagnostics_starting else when (capture) {
                CaptureWriterState.Idle -> null
                CaptureWriterState.Starting -> R.string.diagnostics_starting
                is CaptureWriterState.Recording -> R.string.capture_running
                is CaptureWriterState.Finalizing -> R.string.diagnostics_saving
                is CaptureWriterState.Ready -> when {
                    !capture.complete -> R.string.diagnostics_incomplete
                    capture.sent -> R.string.capture_sent
                    capture.pending -> R.string.capture_pending
                    else -> R.string.diagnostics_saving
                }
                is CaptureWriterState.Failed -> R.string.diagnostics_start_failed
            }
            if (status != null) {
                Text(
                    stringResource(status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (capture is CaptureWriterState.Failed || capture is CaptureWriterState.Ready && !capture.complete) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (capture is CaptureWriterState.Recording) {
                var elapsedSeconds by remember(capture.startedAtEpochMillis) { mutableStateOf(0L) }
                LaunchedEffect(capture.startedAtEpochMillis) {
                    while (true) {
                        elapsedSeconds = ((System.currentTimeMillis() - capture.startedAtEpochMillis) / 1000).coerceAtLeast(0)
                        delay(1_000)
                    }
                }
                Text(stringResource(R.string.capture_elapsed, elapsedSeconds / 60, elapsedSeconds % 60))
            }
            if (capture is CaptureWriterState.Ready && !capture.complete) {
                Text(
                    stringResource(incompleteReasonString(capture.reason)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (capture is CaptureWriterState.Ready && !capture.complete && (capture.pending || capture.sent)) {
                Text(
                    stringResource(if (capture.sent) R.string.capture_sent else R.string.capture_pending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.startFailed && capture !is CaptureWriterState.Failed && !(capture is CaptureWriterState.Ready && capture.pending)) {
                Text(stringResource(R.string.diagnostics_start_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            if (state.stopFailed) {
                Text(stringResource(R.string.diagnostics_stop_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!state.starting) {
                    when (capture) {
                        is CaptureWriterState.Recording -> Button(shapes = ButtonDefaults.shapes(), onClick = viewModel::stop) {
                            Text(stringResource(R.string.capture_stop))
                        }
                        CaptureWriterState.Starting, is CaptureWriterState.Finalizing -> Unit
                        else -> {
                            Button(
                                shapes = ButtonDefaults.shapes(),
                                onClick = viewModel::start,
                                enabled = capture !is CaptureWriterState.Ready || capture.sent,
                            ) {
                                Text(stringResource(R.string.capture_start))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun incompleteReasonString(reason: CaptureIncompleteReason?): Int = when (reason) {
    CaptureIncompleteReason.SCALE_DISCONNECTED -> R.string.diagnostics_reason_disconnected
    CaptureIncompleteReason.SESSION_SIZE_LIMIT -> R.string.diagnostics_reason_size_limit
    CaptureIncompleteReason.SESSION_DURATION_LIMIT -> R.string.diagnostics_reason_duration_limit
    CaptureIncompleteReason.WRITER_OVERRUN -> R.string.diagnostics_reason_writer_overrun
    CaptureIncompleteReason.STORAGE_ERROR -> R.string.diagnostics_reason_storage
    CaptureIncompleteReason.PROCESS_INTERRUPTED, null -> R.string.diagnostics_reason_interrupted
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
