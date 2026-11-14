package dev.mateux.caparao.ui.brews

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import dev.mateux.caparao.model.BrewMode
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

/** "How was it?" (#464): rated once the coffee can be tasted. The brew's numbers, the taste, the bag's notes found, stars and notes. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BrewRatingScreen(viewModel: BrewRatingViewModel, onClose: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.brew_how_was_it), style = MaterialTheme.typography.titleLargeEmphasized)
                        state.brew?.let { brew ->
                            // Which brew this is, in one line: the coffee, the recipe and when.
                            Text(
                                listOfNotNull(brew.coffee, brew.recipeName ?: stringResource(R.string.free_brew), Format.dateTime(brew.startedAt, locale)).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        // Save stays in reach while the form scrolls under it.
        bottomBar = {
            if (!state.loading && state.brew != null) {
                Button(
                    onClick = { viewModel.save(onClose) },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 12.dp).height(ButtonDefaults.MediumContainerHeight),
                    shapes = ButtonDefaults.shapes(),
                    enabled = !state.saving,
                ) { Text(stringResource(R.string.brew_save_rating), style = MaterialTheme.typography.titleMedium) }
            }
        },
    ) { padding ->
        if (state.loading) {
            Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        } else {
            val brew = state.brew ?: return@Scaffold
            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                BrewFacts(brew, locale)
                Section(R.string.brew_taste) {
                    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        val tastes = listOf(
                            BrewTaste.SOUR to R.string.brew_taste_sour,
                            BrewTaste.BALANCED to R.string.brew_taste_balanced,
                            BrewTaste.BITTER to R.string.brew_taste_bitter,
                        )
                        tastes.forEachIndexed { index, (taste, label) ->
                            TasteButton(stringResource(label), state.taste == taste, index, tastes.size, Modifier.weight(1f)) { viewModel.setTaste(taste) }
                        }
                    }
                }
                if (state.bagTastingNotes.isNotEmpty()) {
                    Section(R.string.brew_tasting_notes_found, R.string.brew_tasting_notes_hint) {
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                }
            }
        }
    }
}

// A titled group of the form, with an optional line saying what to do.
@Composable
private fun Section(title: Int, hint: Int? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            hint?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        content()
    }
}

// One of the three tastes: a big segment of a connected group. Tapping the chosen one clears it.
@Composable
private fun TasteButton(label: String, selected: Boolean, index: Int, count: Int, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val outer = CornerSize(50)
    val inner = CornerSize(8.dp)
    val start = if (index == 0) outer else inner
    val end = if (index == count - 1) outer else inner
    val shape = RoundedCornerShape(start, end, end, start)
    val container by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHigh, label = "taste")
    Box(
        modifier.height(56.dp).clip(shape).background(container).selectable(selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (selected) colors.onPrimary else colors.onSurface, maxLines = 1)
    }
}

// What was brewed, to remember the cup by: the time, the water or yield, and the grind when it was noted.
@Composable
private fun BrewFacts(brew: Brew, locale: java.util.Locale) {
    val g = stringResource(R.string.unit_grams)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Fact(R.string.label_time, Format.timer((brew.durationMs / 1000).toInt()), Modifier.weight(1f))
        Fact(if (brew.mode == BrewMode.ESPRESSO) R.string.label_yield else R.string.label_water, "${Format.grams(brew.yieldGrams, locale)} $g", Modifier.weight(1f))
        brew.grind?.takeIf { it.isNotBlank() }?.let { Fact(R.string.recipe_grind, it, Modifier.weight(1f)) }
    }
}

@Composable
private fun Fact(label: Int, value: String, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.background(colors.surfaceContainer, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
