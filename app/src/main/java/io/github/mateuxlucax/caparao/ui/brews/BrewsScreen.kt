package io.github.mateuxlucax.caparao.ui.brews

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.data.local.SyncStatus
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.SyncError
import io.github.mateuxlucax.caparao.ui.AppActions
import io.github.mateuxlucax.caparao.ui.components.SyncErrorLine
import io.github.mateuxlucax.caparao.ui.components.EmptyState
import io.github.mateuxlucax.caparao.ui.components.icon
import io.github.mateuxlucax.caparao.ui.components.label
import io.github.mateuxlucax.caparao.ui.format.Format
import kotlinx.coroutines.launch

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** Saved brews grouped into filter and espresso, newest first (Brews wireframe), under the upload status. */
@Composable
fun BrewsScreen(
    viewModel: BrewsViewModel,
    actions: AppActions,
    onOpen: (Brew) -> Unit,
    onOpenSettings: () -> Unit,
    onCompare: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Ids joined by commas, so it survives a rotation; a long press starts picking brews to compare.
    var picked by rememberSaveable { mutableStateOf("") }
    val selected = picked.split(',').filter { it.isNotEmpty() }
    val all = state.sections.flatMap { it.second }
    fun toggle(brew: Brew) {
        picked = Comparison.toggle(selected, brew, all).joinToString(",")
    }
    if (!state.loading && state.sections.isEmpty()) {
        Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            SyncStatusLine(state.sync, actions, viewModel::retry, onOpenSettings)
            EmptyState(R.drawable.ic_receipt_long, stringResource(R.string.brews_empty_title), stringResource(R.string.brews_empty_text), Modifier.padding(top = 48.dp))
        }
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            if (selected.isEmpty()) {
                SyncStatusLine(state.sync, actions, viewModel::retry, onOpenSettings)
            } else {
                CompareBar(selected.size, onCancel = { picked = "" }, onCompare = { onCompare(selected) })
            }
        }
        state.sections.forEach { (mode, brews) ->
            item(key = mode.name) {
                Text(stringResource(mode.label), Modifier.padding(top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            items(brews, key = { it.id }) { brew ->
                BrewRow(
                    brew,
                    selected = brew.id in selected,
                    onClick = { if (selected.isEmpty()) onOpen(brew) else toggle(brew) },
                    onLongClick = { toggle(brew) },
                )
            }
        }
    }
}

/**
 * "Last sync HH:MM", or that no server is set; below it the last upload error, with what fixes it: Retry, allowing the
 * local network, or the server settings.
 */
@Composable
private fun SyncStatusLine(sync: SyncStatus, actions: AppActions, onRetry: () -> Unit, onOpenSettings: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val scope = rememberCoroutineScope()
    val text = when {
        !sync.serverConfigured -> stringResource(R.string.server_not_configured)
        sync.lastSyncMs > 0 -> stringResource(R.string.last_sync, Format.time(sync.lastSyncMs, locale))
        else -> stringResource(R.string.nothing_synced_yet)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val error = sync.lastError
        if (sync.serverConfigured && error != null) {
            SyncErrorLine(error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (error.kind) {
                    SyncError.Kind.LOCAL_NETWORK_PERMISSION -> OutlinedButton(onClick = {
                        scope.launch { if (actions.requestLocalNetwork()) onRetry() }
                    }) { Text(stringResource(R.string.local_network_allow)) }
                    SyncError.Kind.UNAUTHORIZED, SyncError.Kind.REDIRECTED, SyncError.Kind.INVALID_URL ->
                        OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.server_settings)) }
                    else -> {
                        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                        if (error.kind == SyncError.Kind.REJECTED) {
                            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.server_settings)) }
                        }
                    }
                }
            }
        }
    }
}

/** How many brews are picked, with Compare once there are enough (docs/adr/0022-compare-and-trends.md). */
@Composable
private fun CompareBar(count: Int, onCancel: () -> Unit, onCompare: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.compare_picked, count, Comparison.MAX),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        Button(onClick = onCompare, enabled = count >= Comparison.MIN) { Text(stringResource(R.string.compare)) }
    }
}

/** Mode icon, dose → yield and ratio, time, date and sync state; outlined while picked for a comparison. */
@Composable
private fun BrewRow(brew: Brew, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val g = stringResource(R.string.unit_grams)
    Card(
        modifier = Modifier.fillMaxWidth().clip(CardDefaults.shape).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(brew.mode.icon), contentDescription = null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val dose = "${Format.grams(brew.doseGrams, locale)} $g → "
                Text("$dose${Format.grams(brew.yieldGrams, locale)} $g", style = MaterialTheme.typography.titleMedium.merge(Tabular))
                Text(
                    Format.dateTime(brew.startedAt, locale) + " · " +
                        (brew.syncedAt?.let { stringResource(R.string.synced_at, Format.time(it, locale)) } ?: stringResource(R.string.local_only)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.timer((brew.durationMs / 1000).toInt()), style = MaterialTheme.typography.titleMedium.merge(Tabular))
                Text(stringResource(R.string.ratio_value, Format.ratio(brew.ratio, locale)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
