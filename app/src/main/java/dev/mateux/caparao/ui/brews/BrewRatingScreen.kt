package dev.mateux.caparao.ui.brews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mateux.caparao.R
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewTaste
import dev.mateux.caparao.ui.components.BrewRating
import dev.mateux.caparao.ui.components.label
import dev.mateux.caparao.ui.format.Format

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BrewRatingScreen(viewModel: BrewRatingViewModel, onClose: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.brew_how_was_it), style = MaterialTheme.typography.titleLargeEmphasized) },
                navigationIcon = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else {
            val brew = state.brew ?: return@Scaffold
            Column(
                Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BrewRatingHeader(brew, locale)
                Text(stringResource(R.string.brew_taste), style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(
                        BrewTaste.SOUR to R.string.brew_taste_sour,
                        BrewTaste.BALANCED to R.string.brew_taste_balanced,
                        BrewTaste.BITTER to R.string.brew_taste_bitter,
                    ).forEach { (taste, label) ->
                        FilterChip(
                            selected = state.taste == taste,
                            onClick = { viewModel.setTaste(taste) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
                if (state.bagTastingNotes.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.brew_tasting_notes_found), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.brew_tasting_notes_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.bagTastingNotes.forEach { note ->
                                FilterChip(
                                    selected = note in state.tastingNotesFound,
                                    onClick = { viewModel.toggleTastingNote(note) },
                                    label = { Text(note) },
                                )
                            }
                        }
                    }
                }
                BrewRating(state.rating, viewModel::setRating)
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::setNotes,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.recipe_notes)) },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = MaterialTheme.shapes.medium,
                )
                Button(
                    onClick = { viewModel.save(onClose) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shapes = ButtonDefaults.shapes(),
                    enabled = !state.saving,
                ) { Text(stringResource(R.string.brew_save_rating), style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

@Composable
private fun BrewRatingHeader(brew: Brew, locale: java.util.Locale) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(brew.coffee ?: stringResource(R.string.no_bag), style = MaterialTheme.typography.titleMedium)
        Text(brew.recipeName ?: stringResource(R.string.free_brew), style = MaterialTheme.typography.bodyLarge)
        Text(
            Format.dateTime(brew.startedAt, locale),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
