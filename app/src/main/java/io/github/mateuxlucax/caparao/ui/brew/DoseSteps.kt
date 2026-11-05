package io.github.mateuxlucax.caparao.ui.brew

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.theme.Motion
import io.github.mateuxlucax.caparao.ui.theme.TabularFlex
import io.github.mateuxlucax.caparao.ui.theme.flex
import java.util.Locale

/**
 * The ratio first: the recipe chips set it, - and + nudge it, and the dose and water under it are a reference that a
 * tap edits. Next goes to weighing the beans. Espresso with a recipe is a target (#189): its dose and ratio are shown
 * as a summary, and a free brew calculates the water instead of editing it.
 */
@Composable
fun DoseStep(state: BrewUiState, onRecipe: (Recipe?) -> Unit, onRatio: (Double) -> Unit, onDose: (Double) -> Unit, onNext: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val step = when (state.mode) {
        BrewMode.ESPRESSO -> 0.1
        BrewMode.FILTER, BrewMode.FRENCH_PRESS -> 0.5
    }
    val espressoRecipe = state.recipe?.takeIf { state.mode == BrewMode.ESPRESSO }
    var editing by rememberSaveable { mutableStateOf<Editing?>(null) }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.recipes.isNotEmpty()) RecipeChips(state, onRecipe)
        Spacer(Modifier.weight(1f))
        if (espressoRecipe != null) {
            EspressoRecipeSummary(espressoRecipe, locale)
        } else {
            AnimatedContent(state.targetRatio, label = "ratio") { ratio ->
                Text(
                    stringResource(R.string.ratio_value, ratioText(ratio, locale)),
                    style = MaterialTheme.typography.displayLarge.merge(TabularFlex).merge(flex(800, 100)),
                )
            }
            Text(stringResource(R.string.ratio_caption), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                FilledTonalIconButton(onClick = { onRatio(state.targetRatio - step) }, Modifier.size(72.dp)) {
                    Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.ratio_less), Modifier.size(32.dp))
                }
                FilledTonalIconButton(onClick = { onRatio(state.targetRatio + step) }, Modifier.size(72.dp)) {
                    Icon(painterResource(R.drawable.ic_add), stringResource(R.string.ratio_more), Modifier.size(32.dp))
                }
            }
            Row(Modifier.padding(top = 40.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Amount(state.dose, R.string.unit_grams, locale) { editing = Editing.DOSE }
                Icon(painterResource(R.drawable.ic_arrow_forward), null, tint = MaterialTheme.colorScheme.outline)
                if (state.mode == BrewMode.ESPRESSO) {
                    Text(
                        "${state.plannedWater?.let { Format.grams(it, locale) } ?: "–"} ${stringResource(R.string.unit_ml)}",
                        Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.titleLarge.merge(TabularFlex).merge(flex(700)),
                    )
                } else {
                    Amount(state.plannedWater, R.string.unit_ml, locale) { editing = Editing.WATER }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes(), enabled = state.dose != null) {
            Text(stringResource(R.string.next), style = MaterialTheme.typography.titleMedium)
        }
    }
    editing?.let { field ->
        val initial = if (field == Editing.DOSE) state.dose else state.plannedWater
        GramsDialog(
            label = if (field == Editing.DOSE) R.string.dose_field else R.string.water_field,
            initial = initial?.let { Format.grams(it, locale) } ?: "",
            onDone = { grams -> onDose(if (field == Editing.DOSE) grams else grams / state.targetRatio); editing = null },
            onDismiss = { editing = null },
        )
    }
}

private enum class Editing { DOSE, WATER }

/** 1:16 rather than 1:16,0; a half or a tenth shows its decimal. */
private fun ratioText(ratio: Double, locale: Locale) =
    if (Math.round(ratio * 10) % 10 == 0L) Math.round(ratio).toString() else Format.ratio(ratio, locale)

/**
 * Espresso with a recipe: a target to follow, not settings to change (#189). Fields the recipe doesn't have are left
 * out, so a bare recipe still reads as a short summary.
 */
@Composable
private fun EspressoRecipeSummary(recipe: Recipe, locale: Locale) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryRow(stringResource(R.string.label_dose), amount(recipe.doseGrams, R.string.unit_grams, locale))
            SummaryRow(stringResource(R.string.label_yield), amount(recipe.targetYieldGrams, R.string.unit_grams, locale))
            SummaryRow(stringResource(R.string.label_ratio), stringResource(R.string.ratio_value, ratioText(recipe.targetRatio, locale)))
            val min = recipe.targetMinMs
            val max = recipe.targetMaxMs
            if (min != null && max != null) {
                SummaryRow(
                    stringResource(R.string.recipe_window),
                    stringResource(R.string.espresso_window, (min / 1000).toString(), (max / 1000).toString()),
                )
            }
            recipe.grind?.takeIf { it.isNotBlank() }?.let { SummaryRow(stringResource(R.string.recipe_grind), it) }
            recipe.waterTempCelsius?.let {
                SummaryRow(stringResource(R.string.recipe_temp), "$it ${stringResource(R.string.unit_celsius)}")
            }
            recipe.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.recipe_notes), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(notes, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(
            label,
            Modifier.weight(1f).padding(end = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.merge(TabularFlex),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun amount(grams: Double, @StringRes unit: Int, locale: Locale) = "${Format.grams(grams, locale)} ${stringResource(unit)}"

/** The recipes of this mode and a free brew, as a connected group; the chosen one sets the dose and the ratio. */
@Composable
private fun RecipeChips(state: BrewUiState, onRecipe: (Recipe?) -> Unit) {
    val options = state.recipes + null
    Row(
        Modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, recipe ->
            val checked = state.recipe?.id == recipe?.id
            ToggleButton(
                checked = checked,
                onCheckedChange = { onRecipe(recipe) },
                modifier = Modifier.semantics { role = Role.RadioButton; selected = checked },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) { Text(recipe?.name ?: stringResource(R.string.free_brew)) }
        }
    }
}

/** An amount on a filled pill, in g for the beans and ml for the water; a tap edits it. */
@Composable
private fun Amount(grams: Double?, @StringRes unit: Int, locale: Locale, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.clip(MaterialTheme.shapes.extraLarge).clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${grams?.let { Format.grams(it, locale) } ?: "–"} ${stringResource(unit)}",
                style = MaterialTheme.typography.titleLarge.merge(TabularFlex).merge(flex(700)),
            )
        }
    }
}

@Composable
private fun GramsDialog(label: Int, initial: String, onDone: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val typed = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it < 2000 }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) },
                label = { Text(stringResource(label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { TextButton(onClick = { typed?.let(onDone) }, enabled = typed != null) { Text(stringResource(R.string.done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Weighing the beans before grinding: a ring fills toward the dose around the live weight.
 * The ring and gaps adapt on short screens so Next stays reachable. It takes what was weighed, or the planned dose when
 * the scale shows nothing.
 */
@Composable
fun WeighStep(state: BrewUiState, onTare: () -> Unit, onNext: (Double) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val dose = state.dose ?: 0.0
    val progress by animateFloatAsState(if (dose > 0) (state.weightGrams / dose).coerceIn(0.0, 1.0).toFloat() else 0f, Motion.standard(), label = "beans")
    BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp)) {
        val compact = maxHeight < 500.dp
        val ringSize = minOf(260.dp, maxHeight * 0.4f)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 18.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${Format.grams(state.weightGrams, locale)} ${stringResource(R.string.unit_grams)}",
                        style = MaterialTheme.typography.displayMedium.merge(TabularFlex).merge(flex(800)),
                        color = if (state.connected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stringResource(R.string.weigh_of, Format.grams(dose, locale)), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            GuideHeader(GuideStage.BEANS, Modifier.padding(top = if (compact) 16.dp else 32.dp))
            FilledTonalButton(onClick = onTare, Modifier.padding(top = if (compact) 8.dp else 16.dp).height(56.dp), enabled = state.connected) {
                Icon(painterResource(R.drawable.ic_tare), null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.tare))
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onNext(if (state.connected && state.weightGrams > 0) state.weightGrams else dose) },
                modifier = Modifier.testTag("weigh-step-next").fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.next), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
