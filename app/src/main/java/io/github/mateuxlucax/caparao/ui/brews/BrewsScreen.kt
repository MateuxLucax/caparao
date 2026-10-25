package io.github.mateuxlucax.caparao.ui.brews

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
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.ui.components.EmptyState
import io.github.mateuxlucax.caparao.ui.components.icon
import io.github.mateuxlucax.caparao.ui.components.label
import io.github.mateuxlucax.caparao.ui.format.Format

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** Saved brews grouped into filter and espresso, newest first (Brews wireframe). */
@Composable
fun BrewsScreen(viewModel: BrewsViewModel, onOpen: (Brew) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (!state.loading && state.sections.isEmpty()) {
        Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(stringResource(R.string.server_not_configured), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            EmptyState(R.drawable.ic_receipt_long, stringResource(R.string.brews_empty_title), stringResource(R.string.brews_empty_text), Modifier.padding(top = 48.dp))
        }
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(stringResource(R.string.server_not_configured), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.sections.forEach { (mode, brews) ->
            item(key = mode.name) {
                Text(stringResource(mode.label), Modifier.padding(top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            items(brews, key = { it.id }) { BrewRow(it) { onOpen(it) } }
        }
    }
}

/** Mode icon, dose → yield and ratio, time, date and sync state. */
@Composable
private fun BrewRow(brew: Brew, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val g = stringResource(R.string.unit_grams)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(brew.mode.icon), contentDescription = null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val dose = brew.doseGrams?.let { "${Format.grams(it, locale)} $g → " } ?: ""
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
                brew.ratio?.let {
                    Text(stringResource(R.string.ratio_value, Format.ratio(it, locale)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
