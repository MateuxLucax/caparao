package io.github.mateuxlucax.caparao.ui.brew

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import io.github.mateuxlucax.caparao.ui.brews.BrewChart
import io.github.mateuxlucax.caparao.ui.brews.BrewNotesFields
import io.github.mateuxlucax.caparao.ui.brews.NotesForm
import io.github.mateuxlucax.caparao.ui.components.RatioTarget
import io.github.mateuxlucax.caparao.ui.components.ScaleReadout
import io.github.mateuxlucax.caparao.ui.components.label
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.theme.Motion
import io.github.mateuxlucax.caparao.ui.theme.animatedFlex
import io.github.mateuxlucax.caparao.ui.theme.animationsOff

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
    val back = { if (state.step == BrewStep.LIVE && (state.phase == BrewPhase.BREWING || state.phase == BrewPhase.PAUSED)) confirmStop = true else onClose() }
    BackHandler(onBack = back)
    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = {
                Text(
                    stringResource(
                        when (state.mode) {
                            BrewMode.ESPRESSO -> R.string.stop_brew_title
                            BrewMode.FILTER, BrewMode.FRENCH_PRESS -> R.string.finish_brew_title
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmStop = false; viewModel.stop() }) {
                    Text(
                        stringResource(
                            when (state.mode) {
                                BrewMode.ESPRESSO -> R.string.stop
                                BrewMode.FILTER, BrewMode.FRENCH_PRESS -> R.string.done
                            },
                        ),
                    )
                }
            },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmStop = false }) { Text(stringResource(R.string.keep_brewing)) } },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(state.mode.label), style = MaterialTheme.typography.titleLargeEmphasized) },
                subtitle = { state.recipe?.let { Text(it.name) } },
                navigationIcon = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = back) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.close))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // The milestone bar stays while the recipe guide runs. French press past its last step is serving: remove, press, serve.
            val serving = state.guide?.let { it.index >= state.steps.size } == true
            GuideIndicator(
                state.mode,
                guideStage(state.mode, state.step, state.phase, serving),
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                steps = state.steps,
                stepIndex = state.guide?.index,
            )
            val still = animationsOff()
            val density = LocalDensity.current
            // The guide is part of the live step, keyed apart so it can slide in on its own when the first step opens.
            AnimatedContent(
                state.step to (state.guide != null),
                Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val forward = if (initialState.first == targetState.first) targetState.second else targetState.first.ordinal > initialState.first.ordinal
                    if (still) EnterTransition.None togetherWith ExitTransition.None else Motion.springAxisX(forward, density)
                },
                label = "step",
            ) { (step, guided) ->
                when (step) {
                    BrewStep.DOSE -> DoseStep(
                        state,
                        viewModel::selectRecipe,
                        viewModel::setRatio,
                        viewModel::planDose,
                        viewModel::setFilterRecipeWater,
                        viewModel::setFilterRecipeDose,
                        viewModel::weigh,
                    )
                    BrewStep.WEIGH -> WeighStep(state, viewModel::tare, viewModel::setDose, vibrate = viewModel.phoneVibrates)
                    BrewStep.PREPARE_PORTAFILTER -> when (state.mode) {
                        BrewMode.FILTER, BrewMode.FRENCH_PRESS -> PrepareStep(state, viewModel::setPreparationCheck, viewModel::ready)
                        BrewMode.ESPRESSO -> PortafilterStep(state, viewModel::ready)
                    }
                    BrewStep.LIVE -> LiveStep(state, viewModel, guided)
                    BrewStep.SUMMARY -> SummaryStep(state, viewModel::editNotes, onSave = { if (viewModel.saveNotes()) onClose() }, onDiscard = { viewModel.discard(); onClose() }, onClose = onClose)
                }
            }
        }
    }
}

@Composable
private fun LiveStep(state: BrewUiState, viewModel: BrewViewModel, guided: Boolean) {
    if (state.phase == BrewPhase.PAUSED) {
        FilterPausedStep(state, viewModel::stop)
        return
    }
    if (guided) {
        GuidedStep(state, viewModel::stop)
        return
    }
    val brewing = state.phase == BrewPhase.BREWING
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val espresso = state.mode == BrewMode.ESPRESSO
        GuideHeader(guideStage(state.mode, state.step, state.phase), espresso = espresso)
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
                // Espresso's ring opens on the first drop, the tare rolls the weight to zero, and the flow thickens a cue (#194).
                started = !espresso || brewing || state.phase == BrewPhase.DONE,
                rolling = espresso && state.phase == BrewPhase.TARING,
                flowCue = espresso,
                timerColor = when (state.window) {
                    BrewUiState.Window.INSIDE -> MaterialTheme.colorScheme.tertiary
                    BrewUiState.Window.PAST -> MaterialTheme.colorScheme.error
                    else -> null
                },
            )
            state.recipe?.let { recipe ->
                val min = recipe.targetMinMs
                val max = recipe.targetMaxMs
                when (state.mode) {
                    BrewMode.ESPRESSO -> if (min != null && max != null) {
                        Text(
                            stringResource(R.string.guide_window, (min / 1000).toString(), (max / 1000).toString()),
                            Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BrewMode.FILTER, BrewMode.FRENCH_PRESS -> Unit
                }
            }
        }
        when (state.phase) {
            BrewPhase.WAITING_FOR_CUP -> OutlinedButton(onClick = viewModel::armNow, modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes(), enabled = state.connected) {
                Text(stringResource(R.string.cup_on), style = MaterialTheme.typography.titleMedium)
            }
            BrewPhase.BREWING -> Button(onClick = viewModel::stop, modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) {
                Text(
                    stringResource(
                        when (state.mode) {
                            BrewMode.ESPRESSO -> R.string.stop
                            BrewMode.FILTER, BrewMode.FRENCH_PRESS -> R.string.done
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            BrewPhase.TARING -> if (state.tareTimedOut) {
                TareFailed(connected = state.connected, onRetry = viewModel::retryTare)
            } else {
                Spacer(Modifier.height(ButtonDefaults.MediumContainerHeight))
            }
            else -> Spacer(Modifier.height(ButtonDefaults.MediumContainerHeight))
        }
    }
}

/** Free and guided filter share the same lift pause: the clock and guide hold until the dripper returns. */
@Composable
private fun FilterPausedStep(state: BrewUiState, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.filter_paused), style = MaterialTheme.typography.titleLargeEmphasized)
            Text(
                stringResource(R.string.filter_paused_hint),
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            ScaleReadout(
                weightGrams = state.weightGrams,
                flowGramsPerSecond = 0.0,
                timerSeconds = (state.elapsedMs / 1000).toInt(),
                timerRunning = false,
                modifier = Modifier.padding(top = 12.dp).widthIn(max = 320.dp).fillMaxWidth(0.85f),
                ratio = RatioTarget(state.ratio, state.targetRatio, state.ratioProgress, state.onTarget),
                started = true,
                ringAlpha = 0.45f,
            )
            state.pauseRemainingMs?.takeIf { it in 1L..10_000L }?.let { remaining ->
                Text(
                    stringResource(R.string.filter_finishing_in, Format.timer(((remaining + 999) / 1_000).toInt())),
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.done), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** The tare never read zero, even after the retry: the problem, what to do, and Retry. Nothing is recorded. */
@Composable
private fun TareFailed(connected: Boolean, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.tare_failed_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        Text(stringResource(R.string.tare_failed_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes(), enabled = connected) {
            Text(stringResource(R.string.retry), style = MaterialTheme.typography.titleMedium)
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
            icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
            title = { Text(stringResource(R.string.discard_brew_title)) },
            confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmDiscard = false; onDiscard() }) { Text(stringResource(R.string.discard)) } },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        if (brew == null) {
            Text(stringResource(R.string.nothing_recorded), style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.close), style = MaterialTheme.typography.titleMedium) }
            return@Column
        }
        // The result springs open.
        val still = animationsOff()
        val open = remember { Animatable(if (still) 1f else 0.85f) }
        LaunchedEffect(Unit) { open.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
        Column(
            Modifier.graphicsLayer { scaleX = open.value; scaleY = open.value; alpha = ((open.value - 0.85f) / 0.15f).coerceIn(0f, 1f) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.brew_saved), style = MaterialTheme.typography.headlineMedium)
            if (brew.interrupted) {
                Text(stringResource(R.string.brew_interrupted), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            // Espresso's result counts up and its curve draws in (#198); the other modes show their numbers as they are.
            val espressoResult = brew.mode == BrewMode.ESPRESSO
            BrewNumbers(brew, countUp = espressoResult)
            if (espressoResult && state.curve.isNotEmpty()) {
                BrewChart(
                    state.curve,
                    brew.durationMs,
                    "${stringResource(R.string.brew_detail_weight)} (${stringResource(R.string.unit_grams)})",
                    "${stringResource(R.string.brew_detail_flow)} (${stringResource(R.string.unit_grams_per_second)})",
                    Modifier.fillMaxWidth().testTag("brew-curve"),
                )
            }
        }
        // Asked after the brew, never before, so the main flow stays a few taps (#105).
        state.notes?.let { BrewNotesFields(it, state.coffees, onNotes) }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium) }
        TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmDiscard = true }, modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight)) { Text(stringResource(R.string.discard)) }
    }
}

/**
 * Yield, time, ratio, dose and flow of a saved brew, in a card. Shared with the brew detail. [countUp] counts the numbers
 * up from zero on the save screen (#198); with animations off they show at once.
 */
@Composable
fun BrewNumbers(brew: Brew, modifier: Modifier = Modifier, countUp: Boolean = false) {
    val locale = LocalConfiguration.current.locales[0]
    val g = stringResource(R.string.unit_grams)
    val flowUnit = stringResource(R.string.unit_grams_per_second)
    Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Metric(
                    stringResource(
                        when (brew.mode) {
                            BrewMode.ESPRESSO -> R.string.label_yield
                            BrewMode.FILTER, BrewMode.FRENCH_PRESS -> R.string.label_water
                        },
                    ),
                    countedText(brew.yieldGrams, countUp) { "${Format.grams(it, locale)} $g" },
                    Modifier.weight(1f),
                    large = true,
                )
                Metric(
                    stringResource(R.string.label_time),
                    countedText(brew.durationMs / 1000.0, countUp) { Format.timer(it.toInt()) },
                    Modifier.weight(1f),
                    large = true,
                )
            }
            Row(Modifier.fillMaxWidth()) {
                Metric(stringResource(R.string.label_dose), countedText(brew.doseGrams, countUp) { "${Format.grams(it, locale)} $g" }, Modifier.weight(1f))
                Metric(
                    stringResource(R.string.label_ratio),
                    countedText(brew.ratio, countUp) { stringResource(R.string.ratio_value, Format.ratio(it, locale)) },
                    Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth()) {
                Metric(
                    stringResource(R.string.label_flow_avg),
                    countedText(brew.flowAvgGramsPerSecond, countUp) { "${Format.flow(it, locale)} $flowUnit" },
                    Modifier.weight(1f),
                )
                brew.timeToFirstDropMs?.let {
                    Metric(
                        stringResource(R.string.label_first_drop),
                        countedText(it / 1000.0, countUp) { Format.timer(it.toInt()) },
                        Modifier.weight(1f),
                    )
                } ?: Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * A number that counts up from zero to [target] when [countUp] is set (#198). With animations off, or without the count,
 * it is the final value at once, so the information is on screen either way.
 */
@Composable
private fun countedText(target: Double, countUp: Boolean, format: @Composable (Double) -> String): String {
    val still = animationsOff()
    if (!countUp || still) return format(target)
    // The progress runs 0 to 1 and scales the Double target, so the count never goes through Float: the last text is exact.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(COUNT_UP_MS, easing = Motion.EmphasizedDecelerate))
    }
    return if (progress.value >= 1f) format(target) else format(target * progress.value)
}

private const val COUNT_UP_MS = 800

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier, large: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (large) {
            // The result opens light and narrow and springs to heavy and wide in Roboto Flex.
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { shown = true }
            Text(value, style = MaterialTheme.typography.displaySmall.merge(animatedFlex(if (shown) 760 else 200, if (shown) 115 else 70)), maxLines = 1)
        } else {
            Text(value, style = MaterialTheme.typography.titleLarge.merge(Tabular))
        }
    }
}
