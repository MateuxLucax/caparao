package io.github.mateuxlucax.caparao.ui.brew

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.mateuxlucax.caparao.ui.theme.animationsOff
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.StepKind
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.components.DripperArt
import io.github.mateuxlucax.caparao.ui.components.MorphingShape
import io.github.mateuxlucax.caparao.ui.components.PortafilterArt.drawPortafilter
import io.github.mateuxlucax.caparao.ui.recipes.RecipeForm
import io.github.mateuxlucax.caparao.ui.recipes.label

import io.github.mateuxlucax.caparao.ui.theme.Motion
import io.github.mateuxlucax.caparao.ui.theme.TabularFlex
import io.github.mateuxlucax.caparao.ui.theme.flex
import java.util.Locale

/**
 * The ratio first for a free brew: the recipe chips set it, - and + nudge it, and the dose and water under it are a reference that a
 * tap edits. Next goes to weighing the beans. Espresso with a recipe is a target (#189): its dose and ratio are shown
 * as a summary. A filter or French press recipe makes water the control and scales its steps for this brew (#204, #215).
 */
@Composable
fun DoseStep(
    state: BrewUiState,
    onRecipe: (Recipe?) -> Unit,
    onRatio: (Double) -> Unit,
    onDose: (Double) -> Unit,
    onFilterWater: (Double) -> Unit,
    onFilterDose: (Double) -> Unit,
    onNext: () -> Unit,
    onSteep: (Long) -> Unit = {},
    onRetryScale: () -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    val step = when (state.mode) {
        BrewMode.ESPRESSO -> 0.1
        BrewMode.FILTER, BrewMode.FRENCH_PRESS -> 0.5
    }
    val espressoRecipe = state.recipe?.takeIf { state.mode == BrewMode.ESPRESSO }
    val filterRecipe = state.recipe?.takeIf { state.mode == BrewMode.FILTER || state.mode == BrewMode.FRENCH_PRESS }
    val still = animationsOff()
    val haptic = LocalHapticFeedback.current
    val espresso = state.mode == BrewMode.ESPRESSO
    val filter = state.mode == BrewMode.FILTER
    // A tick on the ratio buttons, for espresso's free brew (#198); the other modes' ratio stays as it was.
    fun tick() { if (espresso) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    fun filterTick() { if (filter) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    // The summary keeps the last recipe while it folds away, so switching to Free brew doesn't blank it first.
    var summaryRecipe by remember { mutableStateOf(espressoRecipe) }
    LaunchedEffect(espressoRecipe) { espressoRecipe?.let { summaryRecipe = it } }
    var editing by rememberSaveable { mutableStateOf<Editing?>(null) }
    var editingSteep by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.recipes.isNotEmpty()) RecipeChips(state, onRecipe)
        Spacer(Modifier.weight(1f))
        AnimatedVisibility(visible = espressoRecipe != null, enter = reveal(still), exit = conceal(still)) {
            summaryRecipe?.let { EspressoRecipeSummary(it, locale, still) }
        }
        AnimatedVisibility(visible = filterRecipe != null, enter = reveal(still), exit = conceal(still)) {
            filterRecipe?.let {
                FilterRecipePlan(state, it, locale, still, onWater = onFilterWater, onNudge = ::filterTick) { editing = Editing.DOSE }
            }
        }
        AnimatedVisibility(visible = espressoRecipe == null && filterRecipe == null, enter = reveal(still), exit = conceal(still)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(state.targetRatio, label = "ratio") { ratio ->
                    Text(
                        stringResource(R.string.ratio_value, ratioText(ratio, locale)),
                        style = MaterialTheme.typography.displayLarge.merge(TabularFlex).merge(flex(800, 100)),
                    )
                }
                Text(stringResource(R.string.ratio_caption), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    FilledTonalIconButton(onClick = { tick(); onRatio(state.targetRatio - step) }, Modifier.size(72.dp)) {
                        Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.ratio_less), Modifier.size(32.dp))
                    }
                    FilledTonalIconButton(onClick = { tick(); onRatio(state.targetRatio + step) }, Modifier.size(72.dp)) {
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
                if (state.mode == BrewMode.FRENCH_PRESS) SteepField(state.steepMs) { editingSteep = true }
            }
        }
        Spacer(Modifier.weight(1f))
        // The dose needs no scale: it stays editable while the scale connects (#182).
        ScaleConnection(state, onRetryScale, Modifier.padding(bottom = 8.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
            shapes = ButtonDefaults.shapes(),
            enabled = state.dose != null && !state.waitingForScale,
        ) {
            Text(stringResource(R.string.next), style = MaterialTheme.typography.titleMedium)
        }
    }
    if (editingSteep) {
        SteepDialog(
            initial = RecipeForm.clockText(state.steepMs),
            onDone = { ms -> onSteep(ms); editingSteep = false },
            onDismiss = { editingSteep = false },
        )
    }
    editing?.let { field ->
        val initial = if (field == Editing.DOSE) state.dose else state.plannedWater
        GramsDialog(
            label = if (field == Editing.DOSE) R.string.dose_field else R.string.water_field,
            initial = initial?.let { Format.grams(it, locale) } ?: "",
            onDone = { grams ->
                if (filterRecipe != null) {
                    if (field == Editing.DOSE) onFilterDose(grams) else onFilterWater(grams)
                } else {
                    onDose(if (field == Editing.DOSE) grams else grams / state.targetRatio)
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

private const val FILTER_WATER_STEP_ML = 10.0

@Composable
private fun FilterRecipePlan(
    state: BrewUiState,
    recipe: Recipe,
    locale: Locale,
    still: Boolean,
    onWater: (Double) -> Unit,
    onNudge: () -> Unit,
    onDose: () -> Unit,
) {
    val water = recipe.steps.lastOrNull { it.kind.pours }?.targetWeightGrams ?: state.plannedWater ?: recipe.targetYieldGrams
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = Format.grams(water, locale) + " ${stringResource(R.string.unit_ml)}",
            transitionSpec = { filterNumberRoll(still) },
            label = "filter water",
        ) { shown ->
            Text(shown, style = MaterialTheme.typography.displayMedium.merge(TabularFlex).merge(flex(800, 100)))
        }
        Text(stringResource(R.string.label_water), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            FilledTonalIconButton(
                onClick = { onNudge(); onWater((water - FILTER_WATER_STEP_ML).coerceAtLeast(1.0)) },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.ratio_less), Modifier.size(28.dp))
            }
            FilledTonalIconButton(
                onClick = { onNudge(); onWater((water + FILTER_WATER_STEP_ML).coerceAtMost(2000.0)) },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.ratio_more), Modifier.size(28.dp))
            }
        }
        Row(
            Modifier.padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Amount(state.dose, R.string.unit_grams, locale, rollChanges = true, still = still, onClick = onDose)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.label_ratio), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.ratio_value, ratioText(recipe.targetRatio, locale)), style = MaterialTheme.typography.titleMedium)
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                recipe.steps.forEach { step ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(step.kind.label), style = MaterialTheme.typography.bodyMedium)
                        Text(Format.timer((step.targetAtMs / 1000).toInt()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        step.targetWeightGrams?.let { target ->
                            AnimatedContent(
                                targetState = "${Format.grams(target, locale)} ${stringResource(R.string.unit_grams)}",
                                transitionSpec = { filterNumberRoll(still) },
                                label = "filter step weight",
                            ) { shown ->
                                Text(shown, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                idealTime(recipe.notes)?.let { time ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.label_time), style = MaterialTheme.typography.bodyMedium)
                        Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Roll changed recipe amounts in together; reduced motion swaps directly to the complete new plan. */
private fun filterNumberRoll(still: Boolean) = if (still) {
    EnterTransition.None togetherWith ExitTransition.None
} else {
    (slideInVertically(Motion.standard()) { it / 2 } + fadeIn(Motion.standard())) togetherWith
        (slideOutVertically(Motion.standard()) { -it / 2 } + fadeOut(Motion.standard()))
}

/** Built-in filter recipes keep their ideal time in the existing note; show its final time range when present. */
private fun idealTime(notes: String?): String? = notes
    ?.let { Regex("\\d+:\\d{2}(?:\\s*[–-]\\s*\\d+:\\d{2})?").findAll(it).lastOrNull()?.value?.replace('-', '–') }

private enum class Editing { DOSE, WATER }

/** The free brew controls fade and grow in; a plain swap with animations off (#183). */
private fun reveal(still: Boolean): EnterTransition =
    fadeIn(Motion.standardOrSnap(still)) + expandVertically(Motion.standardOrSnap(still))

private fun conceal(still: Boolean): ExitTransition =
    fadeOut(Motion.standardOrSnap(still)) + shrinkVertically(Motion.standardOrSnap(still))

/** 1:16 rather than 1:16,0; a half or a tenth shows its decimal. */
private fun ratioText(ratio: Double, locale: Locale) =
    if (Math.round(ratio * 10) % 10 == 0L) Math.round(ratio).toString() else Format.ratio(ratio, locale)

/**
 * Espresso with a recipe: a target to follow, not settings to change (#189). Fields the recipe doesn't have are left
 * out, so a bare recipe still reads as a short summary.
 */
@Composable
private fun EspressoRecipeSummary(recipe: Recipe, locale: Locale, still: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        // Another recipe with more or fewer rows resizes the card.
        modifier = Modifier.fillMaxWidth().animateContentSize(Motion.standardOrSnap(still)),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryRow(stringResource(R.string.label_dose), amount(recipe.doseGrams, R.string.unit_grams, locale), still)
            SummaryRow(stringResource(R.string.label_yield), amount(recipe.targetYieldGrams, R.string.unit_grams, locale), still)
            SummaryRow(stringResource(R.string.label_ratio), stringResource(R.string.ratio_value, ratioText(recipe.targetRatio, locale)), still)
            val min = recipe.targetMinMs
            val max = recipe.targetMaxMs
            if (min != null && max != null) {
                SummaryRow(
                    stringResource(R.string.recipe_window),
                    stringResource(R.string.espresso_window, (min / 1000).toString(), (max / 1000).toString()),
                    still,
                )
            }
            recipe.grind?.takeIf { it.isNotBlank() }?.let { SummaryRow(stringResource(R.string.recipe_grind), it, still) }
            recipe.waterTempCelsius?.let {
                SummaryRow(stringResource(R.string.recipe_temp), "$it ${stringResource(R.string.unit_celsius)}", still)
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
private fun SummaryRow(label: String, value: String, still: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(
            label,
            Modifier.weight(1f).padding(end = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // A new recipe's value rolls in from below as the old one rolls out.
        AnimatedContent(
            value,
            label = "summary value",
            transitionSpec = {
                if (still) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (slideInVertically(Motion.standard()) { it / 2 } + fadeIn(Motion.standard())) togetherWith
                        (slideOutVertically(Motion.standard()) { -it / 2 } + fadeOut(Motion.standard()))
                }
            },
        ) { shown ->
            Text(
                shown,
                style = MaterialTheme.typography.titleMedium.merge(TabularFlex),
                textAlign = TextAlign.End,
            )
        }
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
private fun Amount(
    grams: Double?,
    @StringRes unit: Int,
    locale: Locale,
    rollChanges: Boolean = false,
    still: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.clip(MaterialTheme.shapes.extraLarge).clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val shown = "${grams?.let { Format.grams(it, locale) } ?: "–"} ${stringResource(unit)}"
            if (rollChanges) {
                AnimatedContent(shown, transitionSpec = { filterNumberRoll(still) }, label = "filter dose") { value ->
                    Text(value, style = MaterialTheme.typography.titleLarge.merge(TabularFlex).merge(flex(700)))
                }
            } else {
                Text(shown, style = MaterialTheme.typography.titleLarge.merge(TabularFlex).merge(flex(700)))
            }
        }
    }
}

/** A free French press steeps for this long, from the first water (#218). */
@Composable
private fun SteepField(steepMs: Long, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.padding(top = 16.dp).fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick).testTag("press-steep-field"),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.guide_steep), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(RecipeForm.clockText(steepMs), style = MaterialTheme.typography.titleLarge.merge(TabularFlex).merge(flex(700)))
        }
    }
}

@Composable
private fun SteepDialog(initial: String, onDone: (Long) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val typed = RecipeForm.clock(text)?.takeIf { it in BrewViewModel.STEEP_RANGE_MS }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == ':' }.take(5) },
                label = { Text(stringResource(R.string.steep_time_field)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.testTag("press-steep-input"),
            )
        },
        confirmButton = { TextButton(onClick = { typed?.let(onDone) }, enabled = typed != null) { Text(stringResource(R.string.done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
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
 * The beans actually weighed, when the reading is a dose; otherwise the planned one. Beans left on the scale (0.1 g) or
 * a cup (150 g) don't become the dose (#195).
 */
fun weighedDose(connected: Boolean, readingGrams: Double, plannedDose: Double): Double =
    if (connected && readingGrams in RecipeForm.DOSE) readingGrams else plannedDose

/**
 * The scale before the brew starts (#182): a quiet line while it connects, and after ten seconds the not-found card
 * with Retry. The app keeps listening either way, so a scale that arrives later goes on by itself.
 */
@Composable
internal fun ScaleConnection(state: BrewUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    if (state.connected) return
    if (!state.scaleTimedOut) {
        Text(
            stringResource(R.string.scale_connecting),
            modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        return
    }
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.error_not_found), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(stringResource(R.string.hint_not_found), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

/**
 * Weighing the beans before grinding: a ring fills toward the dose around the live weight.
 * The ring and gaps adapt on short screens so Next stays reachable. It takes what was weighed, or the planned dose when
 * the scale shows nothing or the weight is outside the dose limits.
 */
@Composable
fun WeighStep(state: BrewUiState, onTare: () -> Unit, onNext: (Double) -> Unit, vibrate: Boolean) {
    val locale = LocalConfiguration.current.locales[0]
    val dose = state.dose ?: 0.0
    val progress by animateFloatAsState(if (dose > 0) (state.weightGrams / dose).coerceIn(0.0, 1.0).toFloat() else 0f, Motion.standard(), label = "beans")
    val espresso = state.mode == BrewMode.ESPRESSO
    val reached = dose > 0 && state.weightGrams >= dose
    val still = animationsOff()
    val haptic = LocalHapticFeedback.current
    // Espresso's ring pulses, Next springs once and the phone buzzes (if Settings allows) when the weight first reaches the
    // dose in this visit (#198). The latch keeps a scale that jitters around the dose from firing again; a screen opened
    // already there stays quiet.
    val openedAtDose = remember { reached }
    var latched by remember { mutableStateOf(false) }
    LaunchedEffect(reached) { if (reached) latched = true }
    val pulse = remember { Animatable(1f) }
    val nextScale = remember { Animatable(1f) }
    LaunchedEffect(latched) {
        if (latched && espresso && !openedAtDose) {
            if (vibrate) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (!still) {
                val bounce = spring<Float>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                launch {
                    pulse.animateTo(1.06f, bounce)
                    pulse.animateTo(1f, bounce)
                }
                launch {
                    nextScale.snapTo(1.06f)
                    nextScale.animateTo(1f, bounce)
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp)) {
        val compact = maxHeight < 500.dp
        val ringSize = minOf(260.dp, maxHeight * 0.4f)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(ringSize).graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }, contentAlignment = Alignment.Center) {
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
                onClick = { onNext(weighedDose(state.connected, state.weightGrams, dose)) },
                modifier = Modifier.testTag("weigh-step-next").fillMaxWidth().height(ButtonDefaults.MediumContainerHeight)
                    .graphicsLayer { scaleX = nextScale.value; scaleY = nextScale.value },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.next), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Espresso puck preparation; checks are local to this visit and never affect the Ready action. */
@Composable
fun PortafilterStep(state: BrewUiState, onReady: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var checked by remember { mutableStateOf(setOf<Int>()) }
    val grind = state.recipe?.grind?.takeIf(String::isNotBlank)
    val items = listOf(
        PrepRowData(stringResource(R.string.prepare_rdt)),
        PrepRowData(stringResource(R.string.prepare_grind), grind),
        PrepRowData(stringResource(R.string.prepare_wdt)),
        PrepRowData(stringResource(R.string.prepare_tamp)),
        PrepRowData(stringResource(R.string.prepare_puck_screen)),
    )
    val still = animationsOff()
    val allChecked = checked.size == items.size
    // Ready springs once when every item is checked (#198). It rests in the default color and stays enabled: the checks never block it.
    val readyScale = remember { Animatable(1f) }
    val pictureScale = remember { Animatable(1f) }
    LaunchedEffect(allChecked) {
        if (allChecked && !still) {
            launch {
                pictureScale.animateTo(1.06f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                pictureScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
            }
            readyScale.snapTo(1.06f)
            readyScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        } else {
            pictureScale.snapTo(1f)
            readyScale.snapTo(1f)
        }
    }
    Box(Modifier.fillMaxSize().padding(24.dp)) {
      val compact = LocalConfiguration.current.screenHeightDp < 700
      Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        GuideHeader(GuideStage.PREPARE_PORTAFILTER)
        if (!compact) PortafilterPicture(checked.size, Modifier.padding(top = 4.dp).size(128.dp).graphicsLayer { scaleX = pictureScale.value; scaleY = pictureScale.value })
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.small,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.testTag("portafilter-dose").padding(top = if (compact) 8.dp else 6.dp),
        ) {
            Text(
                stringResource(R.string.prepare_dose, Format.grams(state.dose ?: 0.0, locale)),
                Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(top = if (compact) 12.dp else 16.dp).testTag("portafilter-list"),
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        ) {
            items.forEachIndexed { index, item ->
                PrepItem(item, index, checked = index in checked, still = still) { isChecked ->
                    checked = if (isChecked) checked + index else checked - index
                }
            }
        }
        Button(
            onClick = onReady,
            modifier = Modifier.testTag("portafilter-ready").fillMaxWidth().height(ButtonDefaults.MediumContainerHeight)
                .graphicsLayer { scaleX = readyScale.value; scaleY = readyScale.value },
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.prepare_ready), style = MaterialTheme.typography.titleMedium)
        }
      }
    }
}

/** Filter or French press preparation; checklist marks survive a return, and never gate Ready. */
@Composable
fun PrepareStep(state: BrewUiState, onCheck: (Int, Boolean) -> Unit, onReady: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val grind = state.recipe?.grind?.takeIf(String::isNotBlank)
    val items = when (state.mode) {
        BrewMode.FILTER -> listOf(
            PrepRowData(stringResource(R.string.prepare_rdt)),
            PrepRowData(stringResource(R.string.prepare_grind), grind),
            PrepRowData(stringResource(R.string.prepare_rinse_filter)),
            PrepRowData(stringResource(R.string.prepare_add_grounds)),
            PrepRowData(stringResource(R.string.prepare_dripper_server)),
        )
        BrewMode.FRENCH_PRESS -> listOf(
            PrepRowData(stringResource(R.string.prepare_rdt)),
            PrepRowData(stringResource(R.string.prepare_grind), grind),
            PrepRowData(stringResource(R.string.prepare_add_grounds)),
            PrepRowData(stringResource(R.string.prepare_press_scale)),
        )
        BrewMode.ESPRESSO -> error("Espresso uses PortafilterStep")
    }
    val still = animationsOff()
    val allChecked = state.preparationChecks.size == items.size
    val readyScale = remember { Animatable(1f) }
    val pictureScale = remember { Animatable(1f) }
    LaunchedEffect(allChecked) {
        if (allChecked && !still) {
            launch {
                pictureScale.animateTo(1.06f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                pictureScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
            }
            readyScale.snapTo(1.06f)
            readyScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        } else {
            pictureScale.snapTo(1f)
            readyScale.snapTo(1f)
        }
    }
    Box(Modifier.fillMaxSize().padding(24.dp)) {
        val compact = LocalConfiguration.current.screenHeightDp < 700
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            val press = state.mode == BrewMode.FRENCH_PRESS
            GuideHeader(if (press) GuideStage.PREPARE_FRENCH_PRESS else GuideStage.PREPARE_FILTER)
            if (!compact) {
                if (press) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.padding(top = 4.dp).size(128.dp).testTag("press-prepare-picture"),
                    ) {
                        Icon(painterResource(R.drawable.ic_french_press), null, Modifier.padding(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                } else {
                    FilterPicture(
                        state.preparationChecks.size,
                        Modifier.padding(top = 4.dp).size(128.dp).graphicsLayer { scaleX = pictureScale.value; scaleY = pictureScale.value },
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.testTag(if (press) "press-prepare-dose" else "filter-prepare-dose").padding(top = if (compact) 8.dp else 6.dp),
            ) {
                Text(
                    stringResource(R.string.prepare_dose, Format.grams(state.dose ?: 0.0, locale)),
                    Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(top = if (compact) 12.dp else 16.dp).testTag(if (press) "press-prepare-list" else "filter-prepare-list"),
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            ) {
                items.forEachIndexed { index, item ->
                    PrepItem(
                        item,
                        index,
                        checked = index in state.preparationChecks,
                        still = still,
                        tagPrefix = if (press) "press-prepare" else "filter-prepare",
                    ) { isChecked -> onCheck(index, isChecked) }
                }
            }
            Button(
                onClick = onReady,
                modifier = Modifier.testTag(if (press) "press-prepare-ready" else "filter-prepare-ready").fillMaxWidth().height(ButtonDefaults.MediumContainerHeight)
                    .graphicsLayer { scaleX = readyScale.value; scaleY = readyScale.value },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.prepare_ready), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private data class PrepRowData(val label: String, val setting: String? = null)

/** Each row owns a switchable portafilter layer; the row itself is the whole touch target. */
@Composable
private fun PrepItem(item: PrepRowData, index: Int, checked: Boolean, still: Boolean, tagPrefix: String = "portafilter", onCheckedChange: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val tick = remember { Animatable(if (still || !checked) 1f else 0.8f) }
    val corner by animateFloatAsState(if (checked) 50f else if (index == 0 || index == 4) 22f else 8f, if (still) snap() else spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "prep corner")
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) .98f else 1f, if (still) snap() else spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "prep press")
    LaunchedEffect(checked) {
        if (checked && !still) {
            tick.snapTo(0.8f)
            tick.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        }
    }
    Row(
        Modifier.fillMaxWidth().height(52.dp).graphicsLayer { scaleX = press; scaleY = press }
            .clip(RoundedCornerShape(corner.dp))
            .background(if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .toggleable(value = checked, role = Role.Checkbox, interactionSource = interactionSource, indication = null, onValueChange = { isChecked ->
                if (isChecked) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(isChecked)
            })
            .testTag("$tagPrefix-row-$index")
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp).graphicsLayer { scaleX = tick.value; scaleY = tick.value }, contentAlignment = Alignment.Center) {
            val primary = MaterialTheme.colorScheme.primary
            val onPrimary = MaterialTheme.colorScheme.onPrimary
            val outline = MaterialTheme.colorScheme.outline
            Canvas(Modifier.size(26.dp)) {
                if (checked) {
                    val path = Path().apply { addOval(Rect(Offset.Zero, Size(size.width, size.height))) }
                    drawPath(path, primary)
                    val p = Path().apply { moveTo(size.width * .25f, size.height * .52f); lineTo(size.width * .43f, size.height * .68f); lineTo(size.width * .76f, size.height * .34f) }
                    drawPath(p, onPrimary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                } else drawCircle(outline, style = Stroke(width = 1.7.dp.toPx()))
            }
        }
        Text(item.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
        item.setting?.let { setting ->
            Surface(color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small) {
                Text(setting, Modifier.testTag("$tagPrefix-grind-setting").padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge, color = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The portafilter picture and its morphing shape pulse together when preparation is complete. */
@Composable
private fun PortafilterPicture(checkedCount: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    MorphingShape(checkedCount, modifier.testTag("portafilter-picture"), colors.primaryContainer) {
        Canvas(Modifier.fillMaxSize()) {
            drawPortafilter(metal = colors.primary, handle = colors.onPrimaryContainer)
        }
    }
}

/** The filter dripper and server assemble as its preparation checklist advances. */
@Composable
private fun FilterPicture(checkedCount: Int, modifier: Modifier = Modifier) {
    val still = animationsOff()
    val visible = DripperArt.visibleParts(checkedCount)
    val reveal = DripperArt.parts.map { part ->
        animateFloatAsState(
            targetValue = if (part in visible) 1f else 0f,
            animationSpec = if (still) snap() else spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
            label = "filter ${part.name.lowercase(Locale.ROOT)}",
        ).value
    }
    val colors = MaterialTheme.colorScheme
    MorphingShape(checkedCount, modifier.testTag("filter-prepare-picture"), colors.primaryContainer) {
        Canvas(Modifier.fillMaxSize()) {
            with(DripperArt) { drawDripper(reveal, colors.primary, colors.onPrimaryContainer) }
        }
    }
}
