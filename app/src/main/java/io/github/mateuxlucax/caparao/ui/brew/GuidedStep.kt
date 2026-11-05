package io.github.mateuxlucax.caparao.ui.brew

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Rect
import io.github.mateuxlucax.caparao.ui.components.CupArt.drawCup
import io.github.mateuxlucax.caparao.ui.components.CupArt.drawServer
import io.github.mateuxlucax.caparao.ui.components.RingColors
import io.github.mateuxlucax.caparao.ui.components.TargetBand
import io.github.mateuxlucax.caparao.ui.components.ringStroke
import io.github.mateuxlucax.caparao.ui.components.readableOn
import io.github.mateuxlucax.caparao.ui.components.rememberRingColors
import io.github.mateuxlucax.caparao.ui.components.targetBand
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
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
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        StepProgress(guideProgress(state.mode, steps, guide.index, state.elapsedMs))
        AnimatedContent(
            guide.index,
            Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { Motion.sharedAxisX(targetState > initialState, density) },
            label = "guided",
        ) { index ->
            val step = steps.getOrNull(index)
            // Once shown, a step's screen keeps reading the live state.
            val live = state.guide.takeIf { it.index == index } ?: guide
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                val previous = steps.take(index).lastOrNull { it.kind.pours }?.targetWeightGrams
                if (step == null) {
                    if (state.mode == BrewMode.FRENCH_PRESS) FrenchPressReady(state) else Drawdown(state)
                } else {
                    Step(step, index, steps.size, live, state, previous)
                }
            }
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight), shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.done), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Step(step: RecipeStep, index: Int, count: Int, guide: StepState, state: BrewUiState, previousTarget: Double?) {
    val locale = LocalConfiguration.current.locales[0]
    val g = stringResource(R.string.unit_grams)
    val colors = MaterialTheme.colorScheme
    val still = animationsOff()
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
        tween(500, easing = Motion.Standard),
        label = "kind",
    )
    // A French press steeps until its deadline: say so instead of naming the press still to come.
    val stage = if (step.kind == StepKind.PRESS && state.mode == BrewMode.FRENCH_PRESS) R.string.guide_steep else step.kind.label
    Text(
        "${stringResource(stage)} · ${stringResource(R.string.step_of, index + 1, count)}",
        style = MaterialTheme.typography.labelLarge,
        color = colors.onSurfaceVariant,
    )
    previousTarget?.let { ReachedCheck("${Format.grams(it, locale)} $g") }
    val target = step.targetWeightGrams
    if (step.kind == StepKind.PRESS) {
        CountdownRing(guide.progress, color) {
            guide.dueInMs?.let {
                Text(
                    Format.timer(seconds(it)),
                    style = MaterialTheme.typography.displaySmall.merge(Tabular),
                    color = color,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Text(stringResource(R.string.guide_steep_text), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    } else if (step.kind.pours && target != null) {
        val fill by animateFloatAsState((state.weightGrams / target).coerceIn(0.0, 1.1).toFloat(), tween(100, easing = LinearEasing), label = "fill")
        val targetColors = rememberRingColors(targetBand(state.weightGrams, target))
        if (state.mode == BrewMode.FILTER) {
            ServerToTarget(fill, targetColors.stroke)
        } else {
            CupToTarget(fill, targetColors.stroke)
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
            stringResource(if (step.kind == StepKind.SWIRL) R.string.guide_swirl else R.string.guide_wait),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        guide.dueInMs?.let { Text(Format.timer(seconds(it)), style = MaterialTheme.typography.displaySmall.merge(Tabular), color = color) }
        state.steps.getOrNull(index + 1)?.let { next ->
            next.targetWeightGrams?.let { Note(stringResource(R.string.guide_next_pour, Format.grams(it, locale))) }
        }
    }
    guide.overshootGrams?.let { Note(stringResource(R.string.guide_over, Format.grams(it, locale)), colors.onSurfaceVariant) }
    Note(Format.timer((state.elapsedMs / 1000).toInt()))
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
    Canvas(Modifier.fillMaxWidth(0.9f).height(32.dp).testTag(if (target == null) "flow-chart-no-target" else "flow-chart-target")) {
        val left = 3.dp.toPx()
        val right = size.width - left
        val top = 3.dp.toPx()
        val bottom = size.height - top
        val maxFlow = maxOf(displayedHistory.maxOfOrNull { it.flow } ?: animatedFlow, band?.endInclusive ?: 0.0, 1.0)
        fun y(value: Double) = bottom - (value / maxFlow).coerceIn(0.0, 1.0).toFloat() * (bottom - top)
        band?.let { ideal ->
            val bandTop = y(ideal.endInclusive)
            val bandBottom = y(ideal.start)
            drawRect(
                colors.tertiaryContainer.copy(alpha = 0.75f),
                topLeft = Offset(left, bandTop),
                size = Size(right - left, bandBottom - bandTop),
            )
        }
        drawLine(colors.outlineVariant, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())
        val points = displayedHistory.mapIndexed { index, point ->
            val x = if (displayedHistory.size == 1) right else left + (right - left) * index / (displayedHistory.size - 1)
            Offset(x, y(point.flow))
        }
        if (points.size == 1) {
            drawCircle(colors.primary, radius = 3.dp.toPx(), center = points.single())
        } else {
            points.zipWithNext().forEach { (from, to) -> drawLine(colors.primary, from, to, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round) }
        }
    }
}

private data class FlowPoint(val elapsedMs: Long, val flow: Double)

@Composable
private fun Drawdown(state: BrewUiState) {
    val locale = LocalConfiguration.current.locales[0]
    ReachedCheck(null)
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
        Canvas(Modifier.fillMaxWidth().height(14.dp).testTag("drain-time-progress")) {
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

/** Shows the correct post-timer phase; the plunger-only animation accompanies the second phase. */
@Composable
private fun FrenchPressReady(state: BrewUiState) {
    Text(Format.timer((state.elapsedMs / 1000).toInt()), style = MaterialTheme.typography.displayMedium.merge(Tabular), color = MaterialTheme.colorScheme.primary)
    if (state.frenchPressRemoved) {
        Text(stringResource(R.string.guide_press_serve), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Note(stringResource(R.string.guide_press_serve_text))
        FrenchPressPlungerAnimation()
    } else {
        Text(stringResource(R.string.guide_remove_press), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    }
}

/** The carafe stays still; only the plunger lowers, after the user has removed the press from the scale. */
@Composable
private fun FrenchPressPlungerAnimation() {
    val still = animationsOff()
    val progress = remember { Animatable(if (still) 1f else 0f) }
    val spatialMotion = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(still) {
        if (!still) progress.animateTo(1f, spatialMotion)
    }
    val colors = MaterialTheme.colorScheme
    Canvas(Modifier.size(160.dp)) {
        scale(size.width / 120f, pivot = Offset.Zero) {
            val left = 31f
            val right = 89f
            val top = 28f
            val bottom = 105f
            val press = Rect(left, top, right, bottom)
            drawRoundRect(
                color = colors.surfaceContainerHighest,
                topLeft = Offset(press.left, press.top),
                size = Size(press.width, press.height),
                cornerRadius = CornerRadius(7f, 7f),
            )
            drawRect(
                colors.tertiary.copy(alpha = 0.45f),
                topLeft = Offset(left + 4f, 70f),
                size = Size(right - left - 8f, bottom - 74f),
            )
            drawRoundRect(
                color = colors.outline,
                topLeft = Offset(press.left, press.top),
                size = Size(press.width, press.height),
                cornerRadius = CornerRadius(7f, 7f),
                style = Stroke(width = 3f),
            )
            val handle = Path().apply {
                moveTo(right, 40f)
                cubicTo(101f, 40f, 109f, 44f, 109f, 53f)
                lineTo(109f, 77f)
                cubicTo(109f, 86f, 101f, 90f, right, 90f)
            }
            drawPath(
                handle,
                colors.outline,
                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            val plateY = 42f + 42f * progress.value
            drawLine(colors.primary, Offset(60f, 8f), Offset(60f, plateY), strokeWidth = 4f, cap = StrokeCap.Round)
            drawLine(colors.primary, Offset(42f, plateY), Offset(78f, plateY), strokeWidth = 6f, cap = StrokeCap.Round)
        }
    }
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

/** How far through the steps: a wave up to the current one. */
@Composable
private fun StepProgress(progress: Float) {
    val shown by animateFloatAsState(progress, Motion.standard(), label = "steps")
    // Only the wave, always moving: no track and no stop dot after it. With animations off, the wave holds still.
    val modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    if (animationsOff()) {
        LinearWavyProgressIndicator(
            progress = { shown },
            modifier = modifier,
            trackColor = Color.Transparent,
            stopSize = 0.dp,
            amplitude = { 1f },
            waveSpeed = 0.dp,
        )
    } else {
        LinearWavyProgressIndicator(
            progress = { shown },
            modifier = modifier,
            trackColor = Color.Transparent,
            stopSize = 0.dp,
            amplitude = { 1f },
        )
    }
}

/** A check that grows and fades when a step opens after its target was reached, with what was reached. */
@Composable
private fun ReachedCheck(label: String?) {
    val still = animationsOff()
    val t = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(900, easing = Motion.EmphasizedDecelerate)) }
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

/** The cup filling toward a dashed target line; the last target as a faint line below it. */
@Composable
private fun CupToTarget(fill: Float, color: Color) {
    val body = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(Modifier.size(160.dp).testTag("guided-cup")) {
        scale(size.width / 120f, pivot = Offset.Zero) {
            // A solid cup filling in the step's colour, the target a short tick on its side below the rim.
            val cup = Rect(26f, 20f, 94f, 100f)
            drawCup(cup, body, color, fill * 60f / 80f)
            val target = cup.bottom - 60f
            drawLine(color, Offset(94f, target), Offset(104f, target), strokeWidth = 5f, cap = StrokeCap.Round)
        }
    }
}

/** Filter pours fill the server while French press keeps its cup until the carafe art is ready (#226). */
@Composable
private fun ServerToTarget(fill: Float, water: Color) {
    val colors = MaterialTheme.colorScheme
    val targetMark = if (fill >= 1f) colors.surface else colors.onSurface
    Box(Modifier.size(160.dp).testTag("guided-cup")) {
        Canvas(Modifier.fillMaxSize().testTag("guided-server")) {
            drawServer(
                Rect(0f, 0f, size.width, size.height),
                body = colors.surfaceContainerHighest,
                handle = colors.outlineVariant,
                water = water,
                targetMark = targetMark,
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
