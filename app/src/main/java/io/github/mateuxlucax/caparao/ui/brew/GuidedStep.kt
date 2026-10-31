package io.github.mateuxlucax.caparao.ui.brew

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Rect
import androidx.compose.material3.CircularWavyProgressIndicator
import io.github.mateuxlucax.caparao.ui.components.CupArt.drawCup
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.brew.Pace
import io.github.mateuxlucax.caparao.brew.PourRate
import io.github.mateuxlucax.caparao.brew.StepState
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.recipes.label
import io.github.mateuxlucax.caparao.ui.theme.Motion
import kotlin.math.abs
import kotlin.math.ceil

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/**
 * A guided filter brew, one full-screen step at a time (wireframes 3a–3e, docs/adr/0019-guided-brew.md): the cup
 * filling to the target line for a bloom or pour, a countdown ring for a wait or swirl, then the drawdown. Each kind has
 * its own colour; a late pour turns the error colour. Done stays at the bottom: the filter detector still ends the brew.
 */
@Composable
fun GuidedStep(state: BrewUiState, onDone: () -> Unit) {
    val guide = state.guide ?: return
    val steps = state.steps
    val density = LocalDensity.current
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        StepProgress(steps.size, guide.index)
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
                if (step == null) Drawdown(state) else Step(step, index, steps.size, live, state, previous)
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
    Text(
        "${stringResource(step.kind.label)} · ${stringResource(R.string.step_of, index + 1, count)}",
        style = MaterialTheme.typography.labelLarge,
        color = colors.onSurfaceVariant,
    )
    previousTarget?.let { ReachedCheck("${Format.grams(it, locale)} $g") }
    val target = step.targetWeightGrams
    if (step.kind.pours && target != null) {
        val fill by animateFloatAsState((state.weightGrams / target).coerceIn(0.0, 1.1).toFloat(), tween(100, easing = LinearEasing), label = "fill")
        CupToTarget(fill, color)
        Text(
            stringResource(if (step.kind == StepKind.BLOOM) R.string.guide_bloom else R.string.guide_pour, Format.grams(target, locale)),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            "${Format.grams(state.weightGrams, locale)} / ${Format.grams(target, locale)} $g",
            style = MaterialTheme.typography.displaySmall.merge(Tabular),
            color = color,
        )
        when {
            guide.dueInMs != null -> Note(stringResource(R.string.guide_starts_in, Format.timer(seconds(guide.dueInMs))))
            guide.paceMs != null -> Note(paceText(guide.pace, guide.paceMs), color)
        }
        if (guide.dueInMs == null) {
            PourRate.target(step, previousTarget, state.steps.getOrNull(index + 1), state.weightGrams)?.let { FlowBars(state.flowGramsPerSecond, it) }
        }
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
    Note("${Format.timer((state.elapsedMs / 1000).toInt())} · ${Format.flow(state.flowGramsPerSecond, locale)} ${stringResource(R.string.unit_grams_per_second)}")
}

/**
 * The flow as a row of solid bars rising left to right, like a signal meter: the bars up to the current flow are lit,
 * the one at the pour rate to aim for is a little taller. Lit bars take the tertiary colour on pace, primary otherwise. The scale reaches twice the target, so on pace fills about half.
 */
@Composable
private fun FlowBars(flow: Double, target: Double) {
    val locale = LocalConfiguration.current.locales[0]
    val colors = MaterialTheme.colorScheme
    val shown by animateFloatAsState(flow.toFloat(), tween(150, easing = LinearEasing), label = "flow")
    val lit = if (PourRate.onPace(flow, target)) colors.tertiary else colors.primary
    val litColor by animateColorAsState(lit, tween(300, easing = Motion.Standard), label = "flowColor")
    val targetIndex = FLOW_BARS / 2 - 1
    Row(
        Modifier.fillMaxWidth(0.8f).height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(FLOW_BARS) { i ->
            val level = (i + 1f) / FLOW_BARS * (target * 2).toFloat()
            val height = 0.3f + 0.6f * i / (FLOW_BARS - 1) + if (i == targetIndex) 0.1f else 0f
            Box(
                Modifier.weight(1f).fillMaxHeight(height).clip(MaterialTheme.shapes.small)
                    .background(if (shown >= level - 0.001f) litColor else colors.surfaceContainerHighest),
            )
        }
    }
    Note(
        stringResource(R.string.guide_flow, Format.flow(flow, locale), Format.flow(target, locale)),
        if (lit == colors.tertiary) colors.tertiary else colors.onSurfaceVariant,
    )
}

private const val FLOW_BARS = 10

@Composable
private fun Drawdown(state: BrewUiState) {
    val locale = LocalConfiguration.current.locales[0]
    ReachedCheck(null)
    Text(stringResource(R.string.guide_drawdown), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(Format.timer((state.elapsedMs / 1000).toInt()), style = MaterialTheme.typography.displayMedium.merge(Tabular), color = MaterialTheme.colorScheme.primary)
    Note(
        "${Format.grams(state.weightGrams, locale)} ${stringResource(R.string.unit_grams)} · " +
            stringResource(R.string.ratio_value, Format.ratio(state.ratio ?: 0.0, locale)),
    )
    Note(stringResource(R.string.guide_drawdown_text))
}

@Composable
private fun Note(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    Text(text, style = MaterialTheme.typography.titleMedium.merge(Tabular), color = color, textAlign = TextAlign.Center)

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

/** ● ● ○ ○: done and current steps filled. */
@Composable
private fun StepProgress(count: Int, current: Int) {
    val progress by animateFloatAsState((current + 1f) / count, Motion.standard(), label = "steps")
    LinearWavyProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().padding(bottom = 8.dp))
}

/** A check that grows and fades when a step opens after its target was reached, with what was reached. */
@Composable
private fun ReachedCheck(label: String?) {
    val still = stillAnimations()
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
    Canvas(Modifier.size(160.dp)) {
        scale(size.width / 120f, pivot = Offset.Zero) {
            // A solid cup filling in the step's colour, the target a short tick on its side below the rim.
            val cup = Rect(26f, 20f, 94f, 100f)
            drawCup(cup, body, color, fill * 60f / 80f)
            val target = cup.bottom - 60f
            drawLine(color, Offset(94f, target), Offset(104f, target), strokeWidth = 5f, cap = StrokeCap.Round)
        }
    }
}

/** A wavy ring that empties as the wait runs out. */
@Composable
private fun CountdownRing(progress: Float, color: Color) {
    val shown by animateFloatAsState(progress, tween(200, easing = LinearEasing), label = "ring")
    CircularWavyProgressIndicator(
        progress = { 1f - shown },
        modifier = Modifier.size(160.dp),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        stroke = Stroke(with(LocalDensity.current) { 12.dp.toPx() }, cap = StrokeCap.Round),
        trackStroke = Stroke(with(LocalDensity.current) { 12.dp.toPx() }, cap = StrokeCap.Round),
    )
}

@Composable
private fun stillAnimations(): Boolean {
    val context = LocalContext.current
    return remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}
