package dev.mateux.caparao.ui.coffees

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mateux.caparao.R
import dev.mateux.caparao.model.BagRecipeAdjustmentHistory
import dev.mateux.caparao.model.BagRecipeAdjustmentReason
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.ui.format.Format
import java.util.Locale

/** What one coffee changes in one recipe (#462): grind, water temperature and dose, each beside the recipe's value. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoffeeAdjustmentScreen(viewModel: CoffeeAdjustmentViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClose() }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.adjustment_title), style = MaterialTheme.typography.titleLargeEmphasized)
                        state.recipe?.let {
                            Text("${state.bagName} · ${it.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClose) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.close)) }
                },
                actions = {
                    Button(shapes = ButtonDefaults.shapes(), onClick = viewModel::save, enabled = state.dirty, modifier = Modifier.padding(end = 12.dp)) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        val recipe = state.recipe ?: return@Scaffold
        val g = stringResource(R.string.unit_grams)
        val c = stringResource(R.string.unit_celsius)
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Stepper(
                R.string.recipe_grind,
                recipe.grind?.let { stringResource(R.string.adjustment_recipe_value, it.toString()) },
                state.grind?.toString() ?: "–",
                changed = state.grind != recipe.grind,
                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 4.dp),
                onLess = { viewModel.stepGrind(-1) },
                onMore = { viewModel.stepGrind(1) },
            )
            // A recipe without a temperature has none to adjust.
            recipe.waterTempCelsius?.let { recipeTemp ->
                Stepper(
                    R.string.recipe_temp_short,
                    stringResource(R.string.adjustment_recipe_value, "$recipeTemp $c"),
                    "${state.temp} $c",
                    changed = state.temp != recipeTemp,
                    shape = RoundedCornerShape(4.dp),
                    onLess = { viewModel.stepTemp(-1) },
                    onMore = { viewModel.stepTemp(1) },
                )
            }
            // The water follows the dose, at the recipe's ratio.
            val total = Format.grams(state.dose * recipe.targetRatio, locale)
            Stepper(
                R.string.recipe_dose,
                stringResource(R.string.adjustment_recipe_value, "${Format.grams(recipe.doseGrams, locale)} $g") + " · " +
                    stringResource(if (recipe.mode == BrewMode.ESPRESSO) R.string.adjustment_yield else R.string.adjustment_water, total),
                "${Format.grams(state.dose, locale)} $g",
                changed = state.dose != recipe.doseGrams,
                shape = RoundedCornerShape(4.dp, 4.dp, 16.dp, 16.dp),
                onLess = { viewModel.stepDose(-0.5) },
                onMore = { viewModel.stepDose(0.5) },
            )
            TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmReset = true }, enabled = state.adjusted, modifier = Modifier.padding(top = 6.dp)) {
                Text(stringResource(R.string.adjustment_reset))
            }
            if (history.isNotEmpty()) {
                Text(stringResource(R.string.adjustment_history), Modifier.padding(top = 14.dp, bottom = 6.dp), style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    history.forEach { HistoryLine(it, locale) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.adjustment_reset_title)) },
            text = { Text(stringResource(R.string.adjustment_reset_text)) },
            confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmReset = false; viewModel.reset() }) { Text(stringResource(R.string.adjustment_reset)) } },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

// One value with − and +, and the recipe's own value under its name. A changed value takes the primary color.
@Composable
private fun Stepper(label: Int, recipeValue: String?, value: String, changed: Boolean, shape: RoundedCornerShape, onLess: () -> Unit, onMore: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val name = stringResource(label)
    Row(
        Modifier.fillMaxWidth().background(colors.surfaceContainer, shape).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            recipeValue?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
        }
        FilledTonalIconButton(onClick = onLess, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(44.dp)) {
            Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.adjustment_less, name))
        }
        Text(
            value,
            Modifier.widthIn(min = 84.dp).padding(horizontal = 4.dp).semantics { contentDescription = "$name $value" },
            style = MaterialTheme.typography.titleLarge,
            color = if (changed) colors.primary else colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        FilledTonalIconButton(onClick = onMore, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(44.dp)) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.adjustment_more, name))
        }
    }
}

// One change: the day, what moved from what to what, and where it came from.
@Composable
private fun HistoryLine(entry: BagRecipeAdjustmentHistory, locale: Locale) {
    val g = stringResource(R.string.unit_grams)
    val c = stringResource(R.string.unit_celsius)
    // A side with no value followed the recipe. It says so instead of a number: the recipe may have changed since.
    val asRecipe = stringResource(R.string.adjustment_as_recipe)
    val changes = buildList {
        if (entry.oldGrind != entry.newGrind) {
            add(stringResource(R.string.adjustment_change_grind, entry.oldGrind?.toString() ?: asRecipe, entry.newGrind?.toString() ?: asRecipe))
        }
        if (entry.oldWaterTempCelsius != entry.newWaterTempCelsius) {
            add(stringResource(R.string.adjustment_change_temp, entry.oldWaterTempCelsius?.let { "$it $c" } ?: asRecipe, entry.newWaterTempCelsius?.let { "$it $c" } ?: asRecipe))
        }
        if (entry.oldDoseGrams != entry.newDoseGrams) {
            add(stringResource(R.string.adjustment_change_dose, entry.oldDoseGrams?.let { "${Format.grams(it, locale)} $g" } ?: asRecipe, entry.newDoseGrams?.let { "${Format.grams(it, locale)} $g" } ?: asRecipe))
        }
        if (!entry.oldTuned && entry.newTuned) add(stringResource(R.string.adjustment_tuned))
    }
    Column {
        Text(Format.dateTime(entry.changedAt, locale) + " · " + changes.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(if (entry.reason == BagRecipeAdjustmentReason.SUGGESTION) R.string.adjustment_from_suggestion else R.string.adjustment_manual),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
