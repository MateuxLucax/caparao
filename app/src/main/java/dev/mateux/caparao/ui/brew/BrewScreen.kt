package dev.mateux.caparao.ui.brew

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
import androidx.compose.foundation.background
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.delay
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import dev.mateux.caparao.R
import dev.mateux.caparao.brew.BrewPhase
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.Recipe
import dev.mateux.caparao.ui.components.BrewChart
import dev.mateux.caparao.ui.components.BrewNumbers
import dev.mateux.caparao.ui.components.AppActions
import dev.mateux.caparao.ui.components.RatioTarget
import dev.mateux.caparao.ui.components.ScaleReadout
import dev.mateux.caparao.ui.components.SystemBack
import dev.mateux.caparao.ui.components.label
import dev.mateux.caparao.ui.format.Format
import dev.mateux.caparao.ui.theme.Motion
import dev.mateux.caparao.ui.theme.animatedFlex
import dev.mateux.caparao.ui.theme.animationsOff

/** One brew: dose, then live with the detector driving it, then the saved result. Keeps the screen on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrewScreen(
    viewModel: BrewViewModel,
    onClose: () -> Unit,
    onRate: (String) -> Unit = {},
    onEditAdjustment: (bagId: String, recipeId: String) -> Unit = { _, _ -> },
    actions: AppActions? = null,
) {
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
    // Closing while brewing asks before stopping (a stray gesture shouldn't end the shot); before the brew starts it just
    // closes, as nothing is recorded yet.
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    val finish = onClose
    var remindToRate by rememberSaveable { mutableStateOf(viewModel.remindToRate) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.saved?.id) {
        if (
            state.saved != null && viewModel.remindToRate && actions != null &&
            !actions.notificationsPermissionGranted() && !viewModel.ratingNotificationsPermissionAsked
        ) {
            viewModel.markRatingNotificationsPermissionAsked()
            if (!actions.requestNotificationsPermission()) {
                remindToRate = false
                viewModel.setRemindToRate(false)
            }
        }
    }
    val changeReminder: (Boolean) -> Unit = { enabled ->
        if (!enabled) {
            remindToRate = false
            viewModel.setRemindToRate(false)
        } else {
            scope.launch {
                if (actions != null && !actions.notificationsPermissionGranted()) {
                    if (viewModel.ratingNotificationsPermissionAsked) return@launch
                    viewModel.markRatingNotificationsPermissionAsked()
                    if (!actions.requestNotificationsPermission()) {
                        remindToRate = false
                        viewModel.setRemindToRate(false)
                        return@launch
                    }
                }
                remindToRate = true
                viewModel.setRemindToRate(true)
            }
        }
    }
    val closeBrew = {
        if (state.step == BrewStep.LIVE && (state.phase == BrewPhase.BREWING || state.phase == BrewPhase.PAUSED)) confirmStop = true else onClose()
    }
    // Back goes one step back before Live (#185): Beans to Coffee or Dose, Coffee to Dose, Prepare to Beans; on the Dose step it closes the brew.
    // In Live it asks to restart the brew, which drops what was recorded (#287); the X keeps the stop dialog above.
    var confirmRestart by rememberSaveable { mutableStateOf(false) }
    val back = {
        when (state.step) {
            BrewStep.SUMMARY -> finish()
            BrewStep.COFFEE, BrewStep.WEIGH, BrewStep.PREPARE_PORTAFILTER -> viewModel.backOneStep()
            BrewStep.LIVE -> confirmRestart = true
            else -> closeBrew()
        }
    }
    SystemBack(back)
    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text(stringResource(R.string.restart_brew_title)) },
            text = { Text(stringResource(R.string.restart_brew_text)) },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmRestart = false; viewModel.restart() }) { Text(stringResource(R.string.restart)) }
            },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmRestart = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
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
    // The press deadline keeps the surface (#302): its alert is the screen's motion, vibration and sound, not a color.
    val screenColor = MaterialTheme.colorScheme.surface
    val contentColor = MaterialTheme.colorScheme.onSurface
    Scaffold(
        modifier = Modifier.testTag("brew-screen"),
        containerColor = screenColor,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(state.mode.label), style = MaterialTheme.typography.titleLargeEmphasized) },
                subtitle = { state.recipe?.let { Text(it.name) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = screenColor,
                    titleContentColor = contentColor,
                    subtitleContentColor = contentColor,
                    navigationIconContentColor = contentColor,
                    actionIconContentColor = contentColor,
                ),
                navigationIcon = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = back) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                // The X closes the whole brew from every step but the summary, which closes through Back (#185).
                actions = {
                    if (state.step != BrewStep.SUMMARY) {
                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = closeBrew) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.close_brew))
                        }
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
                // A free French press has no recipe steps to split the Brew segment into.
                steps = if (state.recipe != null) state.steps else emptyList(),
                stepIndex = state.guide?.index,
                coffee = state.bags.isNotEmpty(),
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
                        viewModel::setSteep,
                    )
                    BrewStep.COFFEE -> CoffeeStep(state, viewModel::selectBag, viewModel::continueToBeans, viewModel::retryScale, viewModel::setFollowRecipe, onEditAdjustment)
                    BrewStep.WEIGH -> WeighStep(state, viewModel::tare, viewModel::setDose, vibrate = viewModel.phoneVibrates)
                    BrewStep.PREPARE_PORTAFILTER -> when (state.mode) {
                        BrewMode.FILTER, BrewMode.FRENCH_PRESS -> PrepareStep(state, viewModel::setPreparationCheck, viewModel::ready)
                        BrewMode.ESPRESSO -> PortafilterStep(state, viewModel::ready)
                    }
                    BrewStep.LIVE -> LiveStep(state, viewModel, guided) { confirmStop = true }
                    BrewStep.SUMMARY -> SummaryStep(
                        state,
                        remindToRate = remindToRate,
                        onRemindToRate = changeReminder,
                        onRateNow = { brew -> onRate(brew.id) },
                        onDone = finish,
                        onDiscard = { viewModel.discard(); onClose() },
                    )
                }
            }
        }
    }
}

private const val PAUSED_RING_ALPHA = 0.45f

/** How long the espresso screen waits for the cup before it offers "Tare now". */
private const val TARE_LINK_DELAY_MS = 5_000L

@Composable
private fun LiveStep(state: BrewUiState, viewModel: BrewViewModel, guided: Boolean, onConfirmStop: () -> Unit) {
    // The ring comes back from its paused color instead of jumping when the brew resumes.
    var wasPaused by remember { mutableStateOf(false) }
    if (state.phase == BrewPhase.PAUSED) {
        wasPaused = true
        FilterPausedStep(state, viewModel::stop)
        return
    }
    if (guided) {
        // A French press steep still running asks first; one stray tap would end it for good (#222).
        GuidedStep(state, onDone = { if (state.mode == BrewMode.FRENCH_PRESS && !state.frenchPressDeadlineReached) onConfirmStop() else viewModel.stop() })
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
            // A French press pouring when the scale drops keeps counting; say so instead of asking to start a brew.
            val dropped = state.mode == BrewMode.FRENCH_PRESS && brewing
            Text(stringResource(if (dropped) R.string.guide_scale_dropped else R.string.scale_needed), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            ScaleReadout(
                weightGrams = state.weightGrams,
                flowGramsPerSecond = state.flowGramsPerSecond,
                timerSeconds = (state.elapsedMs / 1000).toInt(),
                timerRunning = brewing,
                modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(0.85f),
                ratio = RatioTarget(state.ratio, state.targetRatio, state.ratioProgress, state.onTarget),
                // Espresso opens on its first drop (#194); filter opens on first water. Both roll through tare.
                started = when (state.mode) {
                    BrewMode.ESPRESSO, BrewMode.FILTER -> brewing || state.phase == BrewPhase.DONE
                    BrewMode.FRENCH_PRESS -> true
                },
                rolling = (espresso || state.mode == BrewMode.FILTER) && state.phase == BrewPhase.TARING,
                respectMotionSetting = state.mode == BrewMode.FILTER,
                initialRingAlpha = if (wasPaused) PAUSED_RING_ALPHA else 1f,
                flowCue = espresso,
                // The cup and the first drop share one screen (#458): the tare shows where the timer will run.
                timerText = if (espresso && state.phase == BrewPhase.TARING) stringResource(R.string.taring) else null,
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
            // No button asks to confirm the cup (#458). A cup that was already on the scale never shows up as a rise,
            // so after a short wait a quiet link offers the tare by hand.
            BrewPhase.WAITING_FOR_CUP -> {
                var offerTare by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { delay(TARE_LINK_DELAY_MS); offerTare = true }
                Box(Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight), contentAlignment = Alignment.Center) {
                    if (offerTare && espresso) {
                        TextButton(onClick = viewModel::armNow, shapes = ButtonDefaults.shapes(), enabled = state.connected) { Text(stringResource(R.string.tare_now)) }
                    }
                }
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
                ringAlpha = PAUSED_RING_ALPHA,
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
private fun SummaryStep(
    state: BrewUiState,
    remindToRate: Boolean,
    onRemindToRate: (Boolean) -> Unit,
    onRateNow: (Brew) -> Unit,
    onDone: () -> Unit,
    onDiscard: () -> Unit,
) {
    val brew = state.saved ?: return
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
        // Rating waits until the coffee can be tasted (#464): a reminder by default, or right away.
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.brew_remind_to_rate), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.brew_rate_reminder_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = remindToRate, onCheckedChange = onRemindToRate, modifier = Modifier.testTag("remind-to-rate"))
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(
            onClick = { onRateNow(brew) },
            modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
            shapes = ButtonDefaults.shapes(),
        ) { Text(stringResource(R.string.brew_rate_now), style = MaterialTheme.typography.titleMedium) }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.done), style = MaterialTheme.typography.titleMedium) }
        TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmDiscard = true }, modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight)) { Text(stringResource(R.string.discard)) }
    }
}

