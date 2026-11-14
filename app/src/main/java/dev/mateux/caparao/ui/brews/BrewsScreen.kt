package dev.mateux.caparao.ui.brews

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.mateux.caparao.model.BrewTaste
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mateux.caparao.R
import dev.mateux.caparao.data.local.SyncStatus
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.SyncError
import dev.mateux.caparao.ui.components.AppActions
import dev.mateux.caparao.ui.components.SyncErrorLine
import dev.mateux.caparao.ui.components.EmptyState
import dev.mateux.caparao.ui.components.icon
import dev.mateux.caparao.ui.components.label
import dev.mateux.caparao.ui.components.segmentedShape
import dev.mateux.caparao.ui.format.Format
import kotlinx.coroutines.launch

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** Saved brews in one list by date: the ones to rate first, then the rest, under the upload status and a coffee filter (#466). */
@Composable
fun BrewsScreen(
    viewModel: BrewsViewModel,
    actions: AppActions,
    onOpen: (Brew) -> Unit,
    onRate: (Brew) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (!state.loading && state.empty) {
        Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            SyncStatusLine(state.sync, actions, viewModel::retry, onOpenSettings)
            EmptyState(R.drawable.ic_receipt_long, stringResource(R.string.brews_empty_title), Modifier.padding(top = 48.dp))
        }
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) { SyncStatusLine(state.sync, actions, viewModel::retry, onOpenSettings) }
        }
        if (state.coffees.isNotEmpty()) {
            item(key = "filter") { CoffeeFilterRow(state.coffees, state.coffee, viewModel::filter) }
        }
        if (!state.loading && state.toRate.isEmpty() && state.rated.isEmpty()) {
            item(key = "filter-empty") {
                EmptyState(R.drawable.ic_receipt_long, stringResource(R.string.brews_filter_empty), Modifier.padding(top = 24.dp))
            }
        }
        if (state.toRate.isNotEmpty()) {
            item(key = "to-rate") { SectionTitle(R.string.brews_to_rate) }
            itemsIndexed(state.toRate, key = { _, line -> "brew-${line.brew.id}" }) { index, line ->
                BrewRow(line, segmentedShape(index, state.toRate.size), onClick = { onOpen(line.brew) }, onRate = { onRate(line.brew) })
            }
            if (state.rated.isNotEmpty()) item(key = "rated") { SectionTitle(R.string.brews_rated) }
        } else if (state.rated.isNotEmpty()) {
            item(key = "gap") { Spacer(Modifier.height(16.dp)) }
        }
        itemsIndexed(state.rated, key = { _, line -> "brew-${line.brew.id}" }) { index, line ->
            BrewRow(line, segmentedShape(index, state.rated.size), onClick = { onOpen(line.brew) }, onRate = null)
        }
    }
}

@Composable
private fun SectionTitle(@StringRes title: Int) {
    Text(
        stringResource(title),
        Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** "All" and the open bags; it scrolls sideways when the names do not fit. */
@Composable
private fun CoffeeFilterRow(coffees: List<CoffeeFilter>, selected: String?, onSelect: (String?) -> Unit) {
    Row(
        Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(stringResource(R.string.filter_all)) })
        coffees.forEach { coffee ->
            FilterChip(
                selected = selected == coffee.bagId,
                onClick = { onSelect(coffee.bagId) },
                label = { Text(coffee.name, Modifier.widthIn(max = 180.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
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
                    SyncError.Kind.LOCAL_NETWORK_PERMISSION -> OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = {
                        scope.launch { if (actions.requestLocalNetwork()) onRetry() }
                    }) { Text(stringResource(R.string.local_network_allow)) }
                    SyncError.Kind.UNAUTHORIZED, SyncError.Kind.REDIRECTED, SyncError.Kind.INVALID_URL ->
                        OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onOpenSettings) { Text(stringResource(R.string.server_settings)) }
                    else -> {
                        OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onRetry) { Text(stringResource(R.string.retry)) }
                        if (error.kind == SyncError.Kind.REJECTED) {
                            TextButton(shapes = ButtonDefaults.shapes(), onClick = onOpenSettings) { Text(stringResource(R.string.server_settings)) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Mode icon; coffee and recipe; dose → yield, time and grind; date, taste and sync state. A brew to rate ends in
 * "Rate" ([onRate]); a rated one in its stars.
 */
@Composable
private fun BrewRow(line: BrewLine, shape: Shape, onClick: () -> Unit, onRate: (() -> Unit)?) {
    val brew = line.brew
    val locale = LocalConfiguration.current.locales[0]
    val g = stringResource(R.string.unit_grams)
    val title = (line.coffee ?: stringResource(R.string.no_bag)) + " · " + (brew.recipeName ?: stringResource(brew.mode.label))
    val numbers = listOfNotNull(
        "${Format.grams(brew.doseGrams, locale)} $g → ${Format.grams(brew.yieldGrams, locale)} $g",
        Format.timer((brew.durationMs / 1000).toInt()),
        brew.grind?.takeIf(String::isNotBlank)?.let { stringResource(R.string.brew_row_grind, it) },
    ).joinToString(" · ")
    val taste = when (brew.taste) {
        BrewTaste.SOUR -> R.string.brew_taste_sour
        BrewTaste.BALANCED -> R.string.brew_taste_balanced
        BrewTaste.BITTER -> R.string.brew_taste_bitter
        null -> null
    }
    val status = listOfNotNull(
        Format.dateTime(brew.startedAt, locale),
        taste?.let { stringResource(it) },
        stringResource(if (brew.syncedAt != null) R.string.synced else R.string.local_only),
    ).joinToString(" · ")
    Card(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(shape).clickable(onClick = onClick),
        shape = shape,
    ) {
        Row(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = if (onRate != null) 12.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(brew.mode.icon), contentDescription = stringResource(brew.mode.label), Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(numbers, style = MaterialTheme.typography.bodyMedium.merge(Tabular))
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            if (onRate != null) {
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onRate) { Text(stringResource(R.string.brew_rate)) }
            } else {
                brew.rating?.let { rating ->
                    val stars = stringResource(R.string.rating_stars, rating)
                    Row(Modifier.clearAndSetSemantics { contentDescription = stars }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_star), contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(2.dp))
                        Text(rating.toString(), style = MaterialTheme.typography.titleMedium.merge(Tabular))
                    }
                }
            }
        }
    }
}
