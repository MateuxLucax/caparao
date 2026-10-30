package io.github.mateuxlucax.caparao.ui.brews

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.ui.brew.BrewNumbers
import io.github.mateuxlucax.caparao.ui.components.label
import io.github.mateuxlucax.caparao.ui.format.Format

/** A saved brew: its numbers, the weight and flow curves, and delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewDetailScreen(
    viewModel: BrewDetailViewModel,
    onClose: () -> Unit,
    onCompare: (List<String>) -> Unit,
    onTrends: (TrendKey) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { state.brew?.let { Text(stringResource(it.mode.label), style = MaterialTheme.typography.titleLargeEmphasized) } },
                navigationIcon = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClose) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.close)) }
                },
                actions = {
                    if (state.brew != null) TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete)) }
                },
            )
        },
    ) { padding ->
        val brew = state.brew ?: return@Scaffold
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                Format.dateTime(brew.startedAt, locale) + " · " +
                    (brew.syncedAt?.let { stringResource(R.string.synced_at, Format.time(it, locale)) } ?: stringResource(R.string.local_only)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BrewNumbers(brew)
            NotesCard(state, viewModel)
            BrewChart(
                state.samples,
                brew.durationMs,
                "${stringResource(R.string.brew_detail_weight)} (${stringResource(R.string.unit_grams)})",
                "${stringResource(R.string.brew_detail_flow)} (${stringResource(R.string.unit_grams_per_second)})",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.previousId?.let { previous ->
                    OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { onCompare(listOf(brew.id, previous)) }) { Text(stringResource(R.string.compare_with_last)) }
                }
                OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { onTrends(TrendKey.of(brew)) }) { Text(stringResource(R.string.trends)) }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
            title = { Text(stringResource(R.string.delete_brew_title)) },
            confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { viewModel.delete(onClose) }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** The brew's recipe and notes, or the form to edit them. */
@Composable
private fun NotesCard(state: BrewDetailUiState, viewModel: BrewDetailViewModel) {
    val brew = state.brew ?: return
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.brew_notes), style = MaterialTheme.typography.titleMedium)
            val form = state.notes
            if (form != null) {
                BrewNotesFields(form, state.coffees, viewModel::changeNotes)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = viewModel::cancelNotes) { Text(stringResource(R.string.cancel)) }
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = viewModel::saveNotes, enabled = form.valid) { Text(stringResource(R.string.save)) }
                }
                return@Column
            }
            val lines = listOfNotNull(
                brew.recipeName?.let { "${stringResource(R.string.recipe_label)}: $it" },
                brew.coffee?.let { "${stringResource(R.string.brew_coffee)}: $it" },
                brew.grind?.let { "${stringResource(R.string.recipe_grind)}: $it" },
                brew.waterTempCelsius?.let { "${stringResource(R.string.recipe_temp)}: $it ${stringResource(R.string.unit_celsius)}" },
                brew.rating?.let { "${stringResource(R.string.brew_rating)}: ${stringResource(R.string.rating_stars, it)}" },
                brew.notes,
            )
            if (lines.isEmpty()) {
                Text(stringResource(R.string.no_notes), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
            TextButton(shapes = ButtonDefaults.shapes(), onClick = viewModel::editNotes) { Text(stringResource(R.string.edit_notes)) }
        }
    }
}

