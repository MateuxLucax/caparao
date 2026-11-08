package io.github.mateuxlucax.caparao.ui.brew

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import io.github.mateuxlucax.caparao.ui.components.MorphingShape
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Rect
import io.github.mateuxlucax.caparao.ui.components.CupArt.drawServer
import io.github.mateuxlucax.caparao.ui.components.PressArt
import io.github.mateuxlucax.caparao.ui.components.PressOnScale
import io.github.mateuxlucax.caparao.ui.components.RingColors
import io.github.mateuxlucax.caparao.ui.components.TargetBand
import io.github.mateuxlucax.caparao.ui.components.ringStroke
import io.github.mateuxlucax.caparao.ui.components.readableOn
import io.github.mateuxlucax.caparao.ui.components.rememberRingColors
import io.github.mateuxlucax.caparao.ui.components.targetBand
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.brew.Pace
import io.github.mateuxlucax.caparao.brew.PourRate
import io.github.mateuxlucax.caparao.brew.StepState
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.recipes.label
import io.github.mateuxlucax.caparao.ui.theme.Motion
import io.github.mateuxlucax.caparao.ui.theme.animationsOff
import kotlin.math.abs
import kotlin.math.ceil

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/**
 * A guided filter brew, one full-screen step at a time (wireframes 3a–3e, #103): the cup
 * filling to the target line for a bloom or pour, a countdown ring for a wait or swirl, then the drawdown. Each kind has
 * its own colour; a late pour turns the error colour. Done stays at the bottom: the filter detector still ends the brew.
 */
@Composable
fun GuidedStep(state: BrewUiState, onDone: () -> Unit) {
    val guide = state.guide ?: return
    val steps = state.steps
    val density = LocalDensity.current
    val still = state.mode == BrewMode.FILTER && animationsOff()
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.mode == BrewMode.FRENCH_PRESS && !state.connected) {
            Text(stringResource(R.string.guide_scale_dropped), color = LocalContentColor.current, textAlign = TextAlign.Center)
        }
        AnimatedContent(
            guide.index,
            Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                if (still) EnterTransition.None togetherWith ExitTransition.None
                else Motion.sharedAxisX(targetState > initialState, density)
            },
            label = "guided",
        ) { index ->
            val step = steps.getOrNull(index)
            // Once shown, a step's screen keeps reading the live state.
            val live = state.guide.takeIf { it.index == index } ?: guide
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                // A free brew checks off the water that went in, which can be short of the planned target.
                val previous = if (state.recipe == null && index > 0) state.weightGrams else steps.take(index).lastOrNull { it.kind.pours }?.targetWeightGrams
                if (step == null) {
                    if (state.mode == BrewMode.FRENCH_PRESS) FrenchPressReady(state) else Drawdown(state)
                } else {
                    Step(step, index, live, state, previous)
                }
            }
        }
        val doneModifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight)
        val doneLabel: @Composable () -> Unit = { Text(stringResource(R.string.done), style = MaterialTheme.typography.titleMedium) }
        // French press: Done is secondary while the steep runs and primary once the deadline has passed (#222).
        if (state.mode == BrewMode.FRENCH_PRESS && !state.frenchPressDeadlineReached) {
            FilledTonalButton(onClick = onDone, modifier = doneModifier, shapes = ButtonDefaults.shapes()) { doneLabel() }
        } else {
            Button(onClick = onDone, modifier = doneModifier, shapes = ButtonDefaults.shapes()) { doneLabel() }
        }
    }
}

@Composable
private fun Step(step: RecipeStep, index: Int, guide: StepState, state: BrewUiState, previousTarget: Double?) {
    val locale = LocalConfiguration.current.locales[0]
    val g = stringResource(R.string.unit_grams)
    val colors = MaterialTheme.colorScheme
    val still = state.mode == BrewMode.FILTER && animationsOff()
    val chartFlow by animateFloatAsState(
        state.flowGramsPerSecond.coerceAtLeast(0.0).toFloat(),
        Motion.standardOrSnap(still, 150),
        label = "flowChart",
    )
    val flowHistory = remember(index) { mutableStateListOf<FlowPoint>() }
    LaunchedEffect(index, state.elapsedMs, state.flowGramsPerSecond) {
        val point = FlowPoint(state.elapsedMs, state.flowGramsPerSecond.coerceAtLeast(0.0))
        if (flowHistory.lastOrNull()?.elapsedMs == point.elapsedMs) {
            flowHistory[flowHistory.lastIndex] = point
        } else {
            flowHistory.add(point)
        }
        if (flowHistory.size > 24) flowHistory.removeAt(0)
    }
    val color by animateColorAsState(
        when {
            guide.pace == Pace.BEHIND -> colors.error
            step.kind == StepKind.BLOOM -> colors.tertiary
            step.kind == StepKind.POUR -> colors.primary
            else -> colors.secondary
        },
        Motion.standardOrSnap(still, 500),
        label = "kind",
    )
    val stepTitle = stringResource(step.kind.label)
    // The French press steep shows only its clock (#302): "Let it steep" names the step and the water is already in.
    val steep = step.kind == StepKind.PRESS && state.mode == BrewMode.FRENCH_PRESS
    if (!steep) {
        Text(
            stepTitle,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
        )
    }
    if (!steep) previousTarget?.let { ReachedCheck("${Format.grams(it, locale)} $g", still) }
    val target = step.targetWeightGrams
    if (step.kind == StepKind.PRESS) {
        // The steep is one clock (#302): the press on its shape, the countdown and the bar up to the deadline.
        if (steep) {
            SteepClock(step, guide, state.elapsedMs)
        } else {
            CountdownRing(guide.progress, color) {
                guide.dueInMs?.let {
                    Text(Format.timer(seconds(it)), style = MaterialTheme.typography.displaySmall.merge(Tabular), color = color, maxLines = 1)
                }
            }
            Text(stringResource(R.string.guide_steep_text), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        }
    } else if (step.kind.pours && target != null) {
        val fill by animateFloatAsState(
            (state.weightGrams / target).coerceIn(0.0, 1.1).toFloat(),
            Motion.standardOrSnap(still, 100),
            label = "fill",
        )
        val targetColors = rememberRingColors(targetBand(state.weightGrams, target))
        // Both fill their server with the step's color against the target: no target line, the color says it (#208).
        if (state.mode == BrewMode.FILTER) {
            ServerToTarget(fill, targetColors.stroke)
        } else {
            PressOnScale(fill, targetColors.stroke)
        }
        Text(
            stringResource(if (step.kind == StepKind.BLOOM) R.string.guide_bloom else R.string.guide_pour, Format.grams(target, locale)),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            "${Format.grams(state.weightGrams, locale)} / ${Format.grams(target, locale)} $g",
            style = MaterialTheme.typography.displaySmall.merge(Tabular),
            color = targetColors.text,
            modifier = Modifier.testTag("guided-weight"),
        )
        when {
            guide.dueInMs != null -> Note(stringResource(R.string.guide_starts_in, Format.timer(seconds(guide.dueInMs))))
            guide.paceMs != null -> Note(paceText(guide.pace, guide.paceMs), modifier = Modifier.testTag("guide-pace"))
        }
        FlowChart(state.flowGramsPerSecond, chartFlow.toDouble(), flowHistory, step.targetFlowGramsPerSecond, locale)
    } else {
        CountdownRing(guide.progress, color)
        Text(
            stringResource(
                when (step.kind) {
                    StepKind.SWIRL -> R.string.guide_swirl
                    StepKind.STIR -> R.string.guide_stir
                    else -> R.string.guide_wait
                },
            ),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (step.kind == StepKind.STIR) {
            Text(stringResource(R.string.guide_stir_text), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        guide.dueInMs?.let { Text(Format.timer(seconds(it)), style = MaterialTheme.typography.displaySmall.merge(Tabular), color = color) }
        state.steps.getOrNull(index + 1)?.let { next ->
            next.targetWeightGrams?.let { Note(stringResource(R.string.guide_next_pour, Format.grams(it, locale))) }
        }
    }
    guide.overshootGrams?.let { Note(stringResource(R.string.guide_over, Format.grams(it, locale)), colors.onSurfaceVariant) }
    // The French press steep has one clock, its countdown and bar: no total elapsed note on that step (#302).
    if (!steep) {
        Note(Format.timer((state.elapsedMs / 1000).toInt()))
    }
}

@Composable
private fun FlowChart(liveFlow: Double, animatedFlow: Double, history: List<FlowPoint>, target: Double?, locale: java.util.Locale) {
    val colors = MaterialTheme.colorScheme
    val band = target?.let(PourRate::band)
    val displayedHistory = history.mapIndexed { index, point -> if (index == history.lastIndex) point.copy(flow = animatedFlow) else point }
    Text(
        stringResource(R.string.guide_flow, Format.flow(liveFlow, locale)),
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSurfaceVariant,
    )
    Canvas(Modifier.fillMaxWidth(0.9f).height(40.dp).testTag(if (target == null) "flow-chart-no-target" else "flow-chart-target")) {
        val stroke = 4.dp.toPx()
        val left = 3.dp.toPx()
        val right = size.width - left
        val top = stroke
        val bottom = size.height - top
        val maxFlow = maxOf(displayedHistory.maxOfOrNull { it.flow } ?: animatedFlow, band?.endInclusive ?: 0.0, 1.0)
        fun y(value: Double) = bottom - (value / maxFlow).coerceIn(0.0, 1.0).toFloat() * (bottom - top)
        band?.let { ideal ->
            val bandTop = y(ideal.endInclusive)
            val bandBottom = y(ideal.start)
            drawRoundRect(
                colors.tertiaryContainer.copy(alpha = 0.75f),
                topLeft = Offset(left, bandTop),
                size = Size(right - left, bandBottom - bandTop),
                cornerRadius = CornerRadius(stroke / 2),
            )
        }
        val points = displayedHistory.mapIndexed { index, point ->
            val x = if (displayedHistory.size == 1) right else left + (right - left) * index / (displayedHistory.size - 1)
            Offset(x, y(point.flow))
        }
        if (points.size == 1) {
            drawCircle(colors.primary, radius = stroke / 2, center = points.single())
        } else if (points.size > 1) {
            // Midpoint smoothing: each sample is a control point, the curve passes through the midpoints between them.
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.lastIndex) {
                    val control = points[i]
                    val next = points[i + 1]
                    quadraticTo(control.x, control.y, (control.x + next.x) / 2, (control.y + next.y) / 2)
                }
                lineTo(points.last().x, points.last().y)
            }
            drawPath(path, colors.primary, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private data class FlowPoint(val elapsedMs: Long, val flow: Double)

@Composable
private fun Drawdown(state: BrewUiState) {
    val locale = LocalConfiguration.current.locales[0]
    ReachedCheck(null, still = state.mode == BrewMode.FILTER && animationsOff())
    Text(stringResource(R.string.guide_drawdown), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(Format.timer((state.elapsedMs / 1000).toInt()), style = MaterialTheme.typography.displayMedium.merge(Tabular), color = MaterialTheme.colorScheme.primary)
    DrainTimeBar(state, Modifier.fillMaxWidth().padding(horizontal = 24.dp))
    Note(
        "${Format.grams(state.weightGrams, locale)} ${stringResource(R.string.unit_grams)} · " +
            stringResource(R.string.ratio_value, Format.ratio(state.ratio ?: 0.0, locale)),
    )
    Note(stringResource(R.string.guide_drawdown_text))
}

/** The filter recipe's ideal drain-time range, shown only after the guided pours finish. */
@Composable
fun DrainTimeBar(state: BrewUiState, modifier: Modifier = Modifier) {
    val window = state.drainWindow ?: return
    val min = state.recipe?.targetMinMs ?: return
    val max = state.recipe?.targetMaxMs ?: return
    val domain = max + maxOf(max - min, 15_000L)
    val progress = (state.elapsedMs.toFloat() / domain).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        progress,
        Motion.standardOrSnap(animationsOff(), 120),
        label = "drainTimeProgress",
    )
    val still = animationsOff()
    val pulse = remember { Animatable(1f) }
    var previousWindow by remember { mutableStateOf(window) }
    LaunchedEffect(window, still) {
        if (!still && previousWindow == BrewUiState.Window.BEFORE && window == BrewUiState.Window.INSIDE) {
            pulse.snapTo(1f)
            pulse.animateTo(1.14f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
            pulse.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        }
        previousWindow = window
    }
    val colors = MaterialTheme.colorScheme
    val ring = drainTimeRingColors(colors, window, isSystemInDarkTheme())
    val status = when (window) {
        BrewUiState.Window.BEFORE -> stringResource(R.string.drain_time_ideal_range, Format.timer((min / 1000).toInt()), Format.timer((max / 1000).toInt()))
        BrewUiState.Window.INSIDE -> stringResource(R.string.drain_time_in_range)
        BrewUiState.Window.PAST -> stringResource(
            R.string.drain_time_past_range,
            ((state.elapsedMs - max) / 1000).toInt(),
            stringResource(R.string.unit_seconds),
        )
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(14.dp).graphicsLayer { scaleY = pulse.value }.testTag("drain-time-progress")) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(colors.surfaceContainerHighest, cornerRadius = radius)
            drawRoundRect(ring.stroke, size = Size(size.width * animatedProgress, size.height), cornerRadius = radius)
            val minX = size.width * (min.toFloat() / domain).coerceIn(0f, 1f)
            val maxX = size.width * (max.toFloat() / domain).coerceIn(0f, 1f)
            drawLine(colors.onSurfaceVariant, Offset(minX, 0f), Offset(minX, size.height), strokeWidth = 2.dp.toPx())
            drawLine(colors.onSurfaceVariant, Offset(maxX, 0f), Offset(maxX, size.height), strokeWidth = 2.dp.toPx())
            val dotRadius = 4.dp.toPx()
            val currentX = (size.width * animatedProgress).coerceIn(dotRadius, (size.width - dotRadius).coerceAtLeast(dotRadius))
            drawCircle(ring.text, radius = dotRadius, center = Offset(currentX, size.height / 2))
        }
        Text(
            status,
            Modifier.fillMaxWidth().testTag("drain-time-${window.name.lowercase()}"),
            style = MaterialTheme.typography.labelLarge,
            color = ring.text,
            textAlign = TextAlign.Center,
        )
    }
}

internal fun drainTimeRingColors(colors: ColorScheme, window: BrewUiState.Window, dark: Boolean): RingColors {
    val band = when (window) {
        BrewUiState.Window.BEFORE -> TargetBand.BELOW
        BrewUiState.Window.INSIDE -> TargetBand.AT
        BrewUiState.Window.PAST -> TargetBand.ABOVE
    }
    val stroke = colors.ringStroke(band, dark)
    return RingColors(stroke, stroke.readableOn(colors.surface))
}

/** Shows the correct post-timer phase; the plunger lowers only after the press has left the scale (#226). */
@Composable
private fun FrenchPressReady(state: BrewUiState) {
    if (state.frenchPressRemoved || state.frenchPressRemovalPending) {
        RemovedPress()
        Text(stringResource(R.string.guide_press_serve), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(Format.timer((state.elapsedMs / 1000).toInt()), style = MaterialTheme.typography.displayMedium.merge(Tabular), color = MaterialTheme.colorScheme.primary)
        Note(stringResource(R.string.guide_press_serve_text))
    } else {
        val deadlineMs = state.recipe?.steps?.lastOrNull()?.targetAtMs ?: state.elapsedMs
        val delayMs = (state.elapsedMs - deadlineMs).coerceAtLeast(0L)
        val repeats = (delayMs / BrewViewModel.FRENCH_PRESS_ALERT_INTERVAL_MS).toInt()
        val still = animationsOff()
        // The deadline keeps the steep's look (#302): the press on its shape, which springs in and pulses with each
        // repeat of the alert, as does the delay.
        val pulse = remember { Animatable(if (still) 1f else 0.6f) }
        LaunchedEffect(repeats, still) {
            if (still) return@LaunchedEffect pulse.snapTo(1f)
            if (repeats > 0) pulse.snapTo(1.18f)
            pulse.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        }
        val colors = MaterialTheme.colorScheme
        PressOnShape(STEEP_DEADLINE_SHAPE, { pulse.value }, PressArt.steepColor(1f, colors.primary), "press-deadline-art")
        Text(stringResource(R.string.guide_remove_press), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(stringResource(R.string.guide_time_up), style = MaterialTheme.typography.titleLarge, color = colors.onSurfaceVariant)
        Text(
            if (delayMs == 0L) Format.timer((deadlineMs / 1000).toInt()) else "+${Format.timer((delayMs / 1000).toInt())}",
            Modifier.graphicsLayer { scaleX = pulse.value.coerceAtLeast(1f); scaleY = pulse.value.coerceAtLeast(1f) }.testTag("press-delay"),
            style = MaterialTheme.typography.displayMedium.merge(Tabular),
            color = colors.primary,
        )
    }
}

/** Serving keeps the steep's look (#302): the press springs in on its shape, then the plunger lowers once. */
@Composable
private fun RemovedPress() {
    val still = animationsOff()
    val enter = remember { Animatable(if (still) 1f else 0.6f) }
    val plunger = remember { Animatable(if (still) 1f else 0f) }
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(still) {
        if (!still) {
            enter.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
            plunger.animateTo(1f, spatial)
        }
    }
    PressOnShape(
        STEEP_SERVE_SHAPE,
        { enter.value },
        PressArt.steepColor(1f, MaterialTheme.colorScheme.primary),
        "press-serve-shape",
        Modifier.testTag("press-serve"),
        plunger = plunger.value,
    )
}

@Composable
private fun Note(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier,
) = Text(text, modifier = modifier, style = MaterialTheme.typography.titleMedium.merge(Tabular), color = color, textAlign = TextAlign.Center)

@Composable
private fun paceText(pace: Pace?, paceMs: Long): String {
    val s = (abs(paceMs) / 1000).toInt()
    return when (pace) {
        Pace.AHEAD -> stringResource(R.string.guide_ahead, s)
        Pace.BEHIND -> stringResource(R.string.guide_behind, s)
        else -> stringResource(R.string.guide_on_time)
    }
}

private fun seconds(ms: Long) = ceil(ms / 1000.0).toInt()

/** A check that grows and fades when a step opens after its target was reached, with what was reached. */
@Composable
private fun ReachedCheck(label: String?, still: Boolean) {
    val t = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(still) {
        if (still) t.snapTo(1f) else t.animateTo(1f, tween(900, easing = Motion.EmphasizedDecelerate))
    }
    val color = MaterialTheme.colorScheme.tertiary
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(28.dp)) {
            val r = size.minDimension / 2 * (0.6f + 0.4f * t.value)
            drawCircle(color.copy(alpha = 0.2f), r)
            val path = Path().apply {
                moveTo(size.width * 0.3f, size.height * 0.52f)
                lineTo(size.width * 0.45f, size.height * 0.66f)
                lineTo(size.width * 0.72f, size.height * 0.36f)
            }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
        label?.let { Text(it, style = MaterialTheme.typography.titleMedium.merge(Tabular), color = color) }
    }
}

/**
 * The French press steep (#302), built like the filter screens: the press darkening on a morphing shape, the countdown,
 * and a bar from the first water to the deadline. The shape turns slowly, breathes with each second, morphs every
 * minute, and pulses harder through the last five seconds. Everything holds still with animations off.
 */
@Composable
private fun SteepClock(step: RecipeStep, guide: StepState, elapsedMs: Long) {
    val colors = MaterialTheme.colorScheme
    val still = animationsOff()
    val dueMs = guide.dueInMs ?: 0L
    val secondsLeft = seconds(dueMs)
    val breath = remember { Animatable(1f) }
    LaunchedEffect(secondsLeft, still) {
        if (still || dueMs <= 0L) return@LaunchedEffect breath.snapTo(1f)
        val last = dueMs <= STEEP_LAST_MS
        breath.animateTo(if (last) 1.12f else 1.04f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        breath.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, if (last) Spring.StiffnessMedium else Spring.StiffnessLow))
    }
    // The coffee and the bar share one clock: the brew from the first water to the deadline.
    val deadline = step.targetAtMs.coerceAtLeast(1L)
    val brewed = (elapsedMs.toFloat() / deadline).coerceIn(0f, 1f)
    PressOnShape((elapsedMs / 60_000L).toInt(), { breath.value }, PressArt.steepColor(brewed, colors.primary), "steep-shape", Modifier.testTag("steep-press"))
    Text(stringResource(R.string.guide_steep_text), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(
        Format.timer(secondsLeft),
        style = MaterialTheme.typography.displayMedium.merge(Tabular),
        color = colors.primary,
        maxLines = 1,
    )
    val fill by animateFloatAsState(
        brewed,
        Motion.standardOrSnap(still, 200),
        label = "steepBar",
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(14.dp).testTag("steep-progress")) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(colors.surfaceContainerHighest, cornerRadius = radius)
            if (fill > 0f) drawRoundRect(colors.primary, size = Size(maxOf(size.height, size.width * fill), size.height), cornerRadius = radius)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Format.timer(0), style = MaterialTheme.typography.labelSmall.merge(Tabular), color = colors.onSurfaceVariant)
            Text(Format.timer((deadline / 1000).toInt()), style = MaterialTheme.typography.labelSmall.merge(Tabular), color = colors.onSurfaceVariant)
        }
    }
}

private const val STEEP_TURN_MS = 24_000
private const val STEEP_LAST_MS = 5_000L
private const val STEEP_DEADLINE_SHAPE = 7
private const val STEEP_SERVE_SHAPE = 0

/**
 * The press on a slowly turning morphing shape, shared by the steep, the deadline and serving (#302). [index] picks the
 * shape, [scale] reads the caller's breath or pulse and [plunger] lowers the mesh; the shape holds still with animations off.
 */
@Composable
private fun PressOnShape(index: Int, scale: () -> Float, coffee: Color, shapeTag: String, pressModifier: Modifier = Modifier, plunger: Float = 0f) {
    // The turn and the scale are read in the layer only, so they redraw the shape without recomposing the screen.
    val turn = if (animationsOff()) null else {
        val transition = rememberInfiniteTransition(label = "pressShapeTurn")
        transition.animateFloat(0f, 360f, infiniteRepeatable(tween(STEEP_TURN_MS, easing = LinearEasing)), label = "turn")
    }
    Box(Modifier.padding(vertical = 8.dp).size(136.dp), contentAlignment = Alignment.Center) {
        MorphingShape(
            index,
            Modifier.matchParentSize().graphicsLayer {
                rotationZ = turn?.value ?: 0f
                val s = scale()
                scaleX = s
                scaleY = s
            }.testTag(shapeTag),
            MaterialTheme.colorScheme.primaryContainer,
        )
        PressOnScale(1f, coffee, pressModifier, plunger = plunger, lid = true, size = 80.dp, showScale = false)
    }
}

/** Filter pours fill the server with the step's color; the color is the only target cue (#208). */
@Composable
private fun ServerToTarget(fill: Float, water: Color) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(160.dp).testTag("guided-cup")) {
        Canvas(Modifier.fillMaxSize().testTag("guided-server")) {
            drawServer(
                Rect(0f, 0f, size.width, size.height),
                body = colors.surfaceContainerHighest,
                handle = colors.outlineVariant,
                water = water,
                fill = fill,
            )
        }
    }
}

/** The brew readout's smooth open ring, emptying as the wait runs out. */
@Composable
private fun CountdownRing(progress: Float, color: Color, centerContent: @Composable () -> Unit = {}) {
    val still = animationsOff()
    val shown by animateFloatAsState(
        progress,
        if (still) snap() else tween(200, easing = LinearEasing),
        label = "ring",
    )
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(Modifier.size(160.dp).testTag("countdown-ring"), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val inset = strokeWidth / 2
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val arcTopLeft = Offset(inset, inset)
            val style = Stroke(strokeWidth, cap = StrokeCap.Round)
            drawArc(track, 135f, 270f, false, arcTopLeft, arcSize, style = style)
            val remaining = 1f - shown
            if (remaining > 0.005f) {
                drawArc(color, 135f, 270f * remaining, false, arcTopLeft, arcSize, style = style)
            }
        }
        centerContent()
    }
}
