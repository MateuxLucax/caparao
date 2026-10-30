package io.github.mateuxlucax.caparao.ui.brew

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.ui.components.RatioTarget
import io.github.mateuxlucax.caparao.ui.components.ScaleReadout
import io.github.mateuxlucax.caparao.ui.components.label
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.recipes.recipeSummary
import io.github.mateuxlucax.caparao.ui.brews.BrewNotesFields
import io.github.mateuxlucax.caparao.ui.brews.NotesForm
import io.github.mateuxlucax.caparao.ui.theme.Motion

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** One brew: dose, then live with the detector driving it, then the saved result. Keeps the screen on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewScreen(viewModel: BrewViewModel, onClose: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val context = LocalContext.current
    val feedback = remember { GuideFeedback(context) }
    DisposableEffect(feedback) { onDispose { feedback.release() } }
    LaunchedEffect(viewModel) { viewModel.alerts.collect(feedback::play) }
    // Back while brewing asks before stopping (a stray gesture shouldn't end the shot); before the brew starts it just
    // closes, as nothing is recorded yet.
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    val back = { if (state.step == BrewStep.LIVE && state.phase == BrewPhase.BREWING) confirmStop = true else onClose() }
    BackHandler(onBack = back)
    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(if (state.mode == BrewMode.ESPRESSO) R.string.stop_brew_title else R.string.finish_brew_title)) },
            confirmButton = {
                TextButton(onClick = { confirmStop = false; viewModel.stop() }) {
                    Text(stringResource(if (state.mode == BrewMode.ESPRESSO) R.string.stop else R.string.done))
                }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.keep_brewing)) } },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(state.mode.label)) },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.close))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // A guided filter brew counts its recipe's steps instead.
            if (state.guide == null) GuideIndicator(state.mode, guideStage(state.mode, state.step, state.phase), Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            val density = LocalDensity.current
            AnimatedContent(
                state.step,
                Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = { Motion.sharedAxisX(targetState.ordinal > initialState.ordinal, density) },
                label = "step",
            ) { step ->
                when (step) {
                    BrewStep.DOSE -> DoseStep(state, viewModel::selectRecipe, viewModel::setDose)
                    BrewStep.LIVE -> LiveStep(state, viewModel)
                    BrewStep.SUMMARY -> SummaryStep(state, viewModel::editNotes, onSave = { if (viewModel.saveNotes()) onClose() }, onDiscard = { viewModel.discard(); onClose() }, onClose = onClose)
                }
            }
        }
    }
}

@Composable
private fun DoseStep(state: BrewUiState, onRecipe: (Recipe?) -> Unit, onDose: (Double) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    var text by rememberSaveable(state.dose) { mutableStateOf(state.dose?.let { Format.grams(it, locale) } ?: "") }
    val typed = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it < 1000 }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GuideHeader(GuideStage.DOSE)
        if (state.recipes.isNotEmpty()) RecipePicker(state, onRecipe)
        Text(
            "${Format.grams(state.weightGrams, locale)} ${stringResource(R.string.unit_grams)}",
            style = MaterialTheme.typography.displayMedium.merge(Tabular),
            color = if (state.connected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The weighed dose is the common case: one tap takes it and moves on.
        FilledTonalButton(onClick = { onDose(state.weightGrams) }, Modifier.fillMaxWidth().height(56.dp), enabled = state.connected && state.weightGrams > 0) {
            Text(stringResource(R.string.use_weight))
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) },
            label = { Text(stringResource(R.string.dose_field)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { typed?.let(onDose) }),
            textStyle = MaterialTheme.typography.headlineSmall.merge(Tabular),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { typed?.let(onDose) }, Modifier.fillMaxWidth().height(64.dp), enabled = typed != null) {
            Text(stringResource(R.string.next), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** The recipes of this mode and a free brew; the chosen one sets the dose and the target. */
@Composable
private fun RecipePicker(state: BrewUiState, onRecipe: (Recipe?) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.recipe_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.recipes.forEach { recipe ->
                FilterChip(selected = state.recipe?.id == recipe.id, onClick = { onRecipe(recipe) }, label = { Text(recipe.name) })
            }
            FilterChip(selected = state.recipe == null, onClick = { onRecipe(null) }, label = { Text(stringResource(R.string.free_brew)) })
        }
        state.recipe?.let { Text(recipeSummary(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun LiveStep(state: BrewUiState, viewModel: BrewViewModel) {
    if (state.guide != null) {
        GuidedStep(state, viewModel::stop)
        return
    }
    val brewing = state.phase == BrewPhase.BREWING
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GuideHeader(guideStage(state.mode, state.step, state.phase))
        if (!state.connected) {
            Text(stringResource(R.string.scale_needed), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            ScaleReadout(
                weightGrams = state.weightGrams,
                flowGramsPerSecond = state.flowGramsPerSecond,
                timerSeconds = (state.elapsedMs / 1000).toInt(),
                timerRunning = brewing,
                modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(0.85f),
                ratio = RatioTarget(state.ratio, state.targetRatio, state.ratioProgress, state.onTarget),
                timerColor = when (state.window) {
                    BrewUiState.Window.INSIDE -> MaterialTheme.colorScheme.tertiary
                    BrewUiState.Window.PAST -> MaterialTheme.colorScheme.error
                    else -> null
                },
            )
            state.recipe?.let { recipe ->
                val min = recipe.targetMinMs
                val max = recipe.targetMaxMs
                if (state.mode == BrewMode.ESPRESSO && min != null && max != null) {
                    Text(
                        stringResource(R.string.guide_window, (min / 1000).toString(), (max / 1000).toString()),
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        when (state.phase) {
            BrewPhase.WAITING_FOR_CUP -> OutlinedButton(onClick = viewModel::armNow, Modifier.fillMaxWidth().height(64.dp), enabled = state.connected) {
                Text(stringResource(R.string.cup_on), style = MaterialTheme.typography.titleMedium)
            }
            BrewPhase.BREWING -> Button(onClick = viewModel::stop, Modifier.fillMaxWidth().height(64.dp)) {
                Text(stringResource(if (state.mode == BrewMode.ESPRESSO) R.string.stop else R.string.done), style = MaterialTheme.typography.titleMedium)
            }
            else -> Spacer(Modifier.height(64.dp))
        }
    }
}

@Composable
private fun SummaryStep(state: BrewUiState, onNotes: (NotesForm) -> Unit, onSave: () -> Unit, onDiscard: () -> Unit, onClose: () -> Unit) {
    val brew = state.saved
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_brew_title)) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDiscard() }) { Text(stringResource(R.string.discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        if (brew == null) {
            Text(stringResource(R.string.nothing_recorded), style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onClose, Modifier.fillMaxWidth().height(64.dp)) { Text(stringResource(R.string.close), style = MaterialTheme.typography.titleMedium) }
            return@Column
        }
        Text(stringResource(R.string.brew_saved), style = MaterialTheme.typography.headlineMedium)
        if (brew.interrupted) {
            Text(stringResource(R.string.brew_interrupted), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        BrewNumbers(brew)
        // Asked after the brew, never before, so the main flow stays a few taps (docs/adr/0021-brew-notes.md).
        state.notes?.let { BrewNotesFields(it, state.coffees, onNotes) }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSave, Modifier.fillMaxWidth().height(64.dp)) { Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium) }
        TextButton(onClick = { confirmDiscard = true }, Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.discard)) }
    }
}

/** Yield, time, ratio, dose and flow of a saved brew, in a card. Shared with the brew detail. */
@Composable
fun BrewNumbers(brew: Brew, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val g = stringResource(R.string.unit_grams)
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Metric(
                    stringResource(if (brew.mode == BrewMode.FILTER) R.string.label_water else R.string.label_yield),
                    "${Format.grams(brew.yieldGrams, locale)} $g",
                    Modifier.weight(1f),
                    large = true,
                )
                Metric(stringResource(R.string.label_time), Format.timer((brew.durationMs / 1000).toInt()), Modifier.weight(1f), large = true)
            }
            Row(Modifier.fillMaxWidth()) {
                Metric(stringResource(R.string.label_dose), "${Format.grams(brew.doseGrams, locale)} $g", Modifier.weight(1f))
                Metric(stringResource(R.string.label_ratio), stringResource(R.string.ratio_value, Format.ratio(brew.ratio, locale)), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                Metric(
                    stringResource(R.string.label_flow_avg),
                    "${Format.flow(brew.flowAvgGramsPerSecond, locale)} ${stringResource(R.string.unit_grams_per_second)}",
                    Modifier.weight(1f),
                )
                brew.timeToFirstDropMs?.let {
                    Metric(stringResource(R.string.label_first_drop), Format.timer((it / 1000).toInt()), Modifier.weight(1f))
                } ?: Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier, large: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = (if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge).merge(Tabular))
    }
}
