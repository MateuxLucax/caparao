package dev.mateux.caparao.ui.brews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mateux.caparao.R
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewTaste
import dev.mateux.caparao.model.Recipe
import dev.mateux.caparao.ui.format.Format

/**
 * After "How was it?" (#468): what to change for this coffee next time, why, and what stays. − and + move the proposed
 * value; "Save for this coffee" stores it. Opposite signals show both facts and lead to the adjustment screen instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewSuggestionScreen(viewModel: BrewSuggestionViewModel, onEditAdjustment: (bagId: String, recipeId: String) -> Unit, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClose() }
    val brew = state.brew
    val recipe = state.recipe
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.suggestion_title), style = MaterialTheme.typography.titleLargeEmphasized)
                        recipe?.let {
                            Text(
                                "${state.bagName} · ${it.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClose) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back)) }
                },
            )
        },
        bottomBar = {
            if (brew != null && recipe != null) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val primary = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight)
                    if (state.field != null) {
                        Button(onClick = viewModel::save, modifier = primary, shapes = ButtonDefaults.shapes(), enabled = state.to != state.from) {
                            Text(stringResource(R.string.suggestion_save), style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        Button(onClick = { brew.bagId?.let { onEditAdjustment(it, recipe.id) } }, modifier = primary, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.suggestion_edit_adjustment), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = onClose) { Text(stringResource(R.string.suggestion_not_now)) }
                }
            }
        },
    ) { padding ->
        if (brew == null || recipe == null) return@Scaffold
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val c = stringResource(R.string.unit_celsius)
            val unit = if (state.field == SuggestedField.TEMPERATURE) " $c" else ""
            Text(stringResource(headline(state)), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
            state.field?.let { field ->
                val name = stringResource(if (field == SuggestedField.GRIND) R.string.recipe_grind else R.string.recipe_temp_short)
                Proposal(name, "${state.from}$unit", "${state.to}$unit", onLess = { viewModel.step(-1) }, onMore = { viewModel.step(1) })
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.suggestion_why), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                Fact(timeFact(brew, recipe))
                brew.taste?.let {
                    Fact(
                        stringResource(
                            when (it) {
                                BrewTaste.SOUR -> R.string.suggestion_taste_sour
                                BrewTaste.BALANCED -> R.string.suggestion_taste_balanced
                                BrewTaste.BITTER -> R.string.suggestion_taste_bitter
                            },
                        ),
                    )
                }
                if (state.bagNotes > 0) Fact(stringResource(R.string.suggestion_notes, brew.tastingNotesFound.size.coerceAtMost(state.bagNotes), state.bagNotes))
            }
            Text(
                stringResource(
                    when (state.field) {
                        SuggestedField.GRIND -> R.string.suggestion_keeps_grind
                        SuggestedField.TEMPERATURE -> R.string.suggestion_keeps_temp
                        null -> R.string.suggestion_opposite_text
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun headline(state: BrewSuggestionState): Int = when (state.field) {
    SuggestedField.GRIND -> when {
        state.to > state.from -> R.string.suggestion_grind_coarser
        state.to < state.from -> R.string.suggestion_grind_finer
        else -> R.string.recipe_grind
    }
    SuggestedField.TEMPERATURE -> when {
        state.to > state.from -> R.string.suggestion_temp_hotter
        state.to < state.from -> R.string.suggestion_temp_cooler
        else -> R.string.recipe_temp_short
    }
    null -> R.string.suggestion_opposite
}

/** "3:21, 21 s over the target (2:30–3:00)". */
@Composable
private fun timeFact(brew: Brew, recipe: Recipe): String {
    val time = Format.timer((brew.durationMs / 1000).toInt())
    val min = recipe.targetMinMs ?: 0
    val max = recipe.targetMaxMs ?: 0
    val low = Format.timer((min / 1000).toInt())
    val high = Format.timer((max / 1000).toInt())
    return when {
        brew.durationMs > max -> stringResource(R.string.suggestion_time_over, time, ((brew.durationMs - max + 999) / 1000).toInt(), low, high)
        brew.durationMs < min -> stringResource(R.string.suggestion_time_under, time, ((min - brew.durationMs + 999) / 1000).toInt(), low, high)
        else -> stringResource(R.string.suggestion_time_on, time, low, high)
    }
}

/** Today's value, an arrow, and the proposed value between − and +. */
@Composable
private fun Proposal(name: String, from: String, to: String, onLess: () -> Unit, onMore: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(colors.surfaceContainer, MaterialTheme.shapes.large).padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(from, Modifier.testTag("suggestion-from"), style = MaterialTheme.typography.headlineMedium, color = colors.onSurfaceVariant)
        Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null, Modifier.padding(horizontal = 12.dp).size(24.dp), tint = colors.onSurfaceVariant)
        FilledTonalIconButton(onClick = onLess, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.adjustment_less, name))
        }
        Text(
            to,
            Modifier.widthIn(min = 96.dp).padding(horizontal = 4.dp).testTag("suggestion-to").semantics { contentDescription = "$name $to" },
            style = MaterialTheme.typography.displaySmall,
            color = colors.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        FilledTonalIconButton(onClick = onMore, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.adjustment_more, name))
        }
    }
}

@Composable
private fun Fact(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
