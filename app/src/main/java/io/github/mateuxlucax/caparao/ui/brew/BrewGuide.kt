package io.github.mateuxlucax.caparao.ui.brew

import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.ui.components.CupArt.drawCup
import io.github.mateuxlucax.caparao.ui.components.CupArt.drawScale
import io.github.mateuxlucax.caparao.ui.components.MorphingShape
import io.github.mateuxlucax.caparao.ui.theme.Motion
import io.github.mateuxlucax.caparao.ui.theme.flex
import kotlin.math.PI
import kotlin.math.sin

/** The steps the brew screen walks through, one at a time like the onboarding. A step has text only when it says something the title and the screen don't. */
/** [label] names the stage next to "Step x of y". */
enum class GuideStage(@StringRes val title: Int, @StringRes val text: Int?, @StringRes val label: Int) {
    DOSE(R.string.dose_title, null, R.string.stage_ratio),
    BEANS(R.string.weigh_beans_title, R.string.weigh_beans_text, R.string.stage_beans),
    CUP(R.string.espresso_place_cup, R.string.espresso_place_cup_text, R.string.stage_cup),
    TARE(R.string.taring, null, R.string.stage_tare),
    FIRST_DROP(R.string.espresso_ready, R.string.espresso_ready_text, R.string.stage_first_drop),
    POUR(R.string.filter_ready, R.string.filter_ready_text, R.string.stage_pour),
    BREWING_ESPRESSO(R.string.brewing_espresso, null, R.string.stage_extracting),
    BREWING_FILTER(R.string.brewing_filter, R.string.brewing_filter_text, R.string.stage_brewing),
    DONE(R.string.brew_saved, null, R.string.stage_done),
}

/** The stages of [mode], in order. */
fun guideStages(mode: BrewMode): List<GuideStage> = when (mode) {
    BrewMode.ESPRESSO -> listOf(GuideStage.DOSE, GuideStage.BEANS, GuideStage.CUP, GuideStage.TARE, GuideStage.FIRST_DROP, GuideStage.BREWING_ESPRESSO, GuideStage.DONE)
    BrewMode.FILTER -> listOf(GuideStage.DOSE, GuideStage.BEANS, GuideStage.TARE, GuideStage.POUR, GuideStage.BREWING_FILTER, GuideStage.DONE)
}

/** Where the screen stands, from its step and the detector's phase. */
fun guideStage(mode: BrewMode, step: BrewStep, phase: BrewPhase): GuideStage = when (step) {
    BrewStep.DOSE -> GuideStage.DOSE
    BrewStep.WEIGH -> GuideStage.BEANS
    BrewStep.SUMMARY -> GuideStage.DONE
    BrewStep.LIVE -> when (phase) {
        BrewPhase.WAITING_FOR_CUP -> GuideStage.CUP
        BrewPhase.TARING -> GuideStage.TARE
        BrewPhase.READY -> if (mode == BrewMode.ESPRESSO) GuideStage.FIRST_DROP else GuideStage.POUR
        BrewPhase.BREWING, BrewPhase.DONE -> if (mode == BrewMode.ESPRESSO) GuideStage.BREWING_ESPRESSO else GuideStage.BREWING_FILTER
    }
}

/** One segment per stage, the done and current ones filled, with "Step x of y" under it. */
@Composable
fun GuideIndicator(mode: BrewMode, stage: GuideStage, modifier: Modifier = Modifier) {
    val stages = guideStages(mode)
    val index = stages.indexOf(stage)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            stages.forEachIndexed { i, _ ->
                val color by animateColorAsState(
                    if (i <= index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    Motion.standard(),
                    label = "stage",
                )
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(color))
            }
        }
        Text(
            stringResource(R.string.step_of_named, index + 1, stages.size, stringResource(stage.label)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The current stage's animation, title and instruction, moving along the shared X axis when the stage changes. */
@Composable
fun GuideHeader(stage: GuideStage, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The picture sits on a shape that morphs from stage to stage: taring, cup, first drop.
        if (stage.pictured) {
            MorphingShape(stage.ordinal, Modifier.padding(bottom = 20.dp).size(128.dp)) {
                AnimatedContent(stage, transitionSpec = { fadeIn(tween(Motion.MEDIUM_2)) togetherWith fadeOut(tween(Motion.SHORT_4)) }, label = "picture") { StageAnimation(it) }
            }
        }
        AnimatedContent(
            stage,
            Modifier.fillMaxWidth(),
            transitionSpec = { Motion.springAxisX(targetState.ordinal > initialState.ordinal, density) },
            label = "stage",
        ) { shown ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Condensed and heavy in Roboto Flex: the step to do now.
                Text(stringResource(shown.title), style = MaterialTheme.typography.headlineSmall.merge(flex(700, 85)), textAlign = TextAlign.Center)
                shown.text?.let {
                    Text(stringResource(it), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Whether the stage has a picture; in the brewing stages the live readout is the picture. */
private val GuideStage.pictured get() = this == GuideStage.CUP || this == GuideStage.TARE || this == GuideStage.FIRST_DROP || this == GuideStage.POUR

private const val LOOP_MS = 2_400

/**
 * A small looping picture of what to do: the cup coming down onto the scale, the LED pulsing while it tares, drops, or
 * a stream filling the cup. Nothing for the brewing stages, where the live readout is the picture. With animations
 * off, a still frame.
 */
@Composable
private fun StageAnimation(stage: GuideStage) {
    if (!stage.pictured) return
    val context = LocalContext.current
    val still = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val t = if (still) {
        0.7f
    } else {
        val transition = rememberInfiniteTransition(label = "guide")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(LOOP_MS, easing = LinearEasing)), label = "t").value
    }
    val colors = MaterialTheme.colorScheme
    // Inset in the 128 dp shape, so the scale and the stream never touch its edges.
    Canvas(Modifier.size(72.dp)) {
        // Drawn in a 120-unit square: the scale at the bottom, the cup on it, the stream or drops above. Without anything
        // above the cup, the pair moves up to the middle.
        val shift = if (stage == GuideStage.CUP || stage == GuideStage.TARE) -18f else 0f
        scale(size.width / 120f, pivot = Offset.Zero) { translate(top = shift) {
            val cupDrop = if (stage == GuideStage.CUP) (1f - (t / 0.5f).coerceIn(0f, 1f)).let { it * it } * 40f else 0f
            // The cup fills while the stream falls, then empties as the loop starts over.
            val fill = if (stage == GuideStage.POUR) 0.75f * Motion.Standard.transform(t) else 0f
            val cup = Rect(36f, 62f - cupDrop, 84f, 98f - cupDrop)
            val led = when (stage) {
                GuideStage.TARE -> 0.35f + 0.65f * (0.5f + 0.5f * sin(2 * PI * t * 2).toFloat())
                GuideStage.CUP -> if (t > 0.55f) 1f else 0.25f
                else -> 1f
            }
            drawScale(Rect(16f, 100f, 104f, 114f), colors.secondary, colors.onSecondary, led)
            drawCup(cup, colors.primary, colors.tertiary, fill)
            when (stage) {
                GuideStage.FIRST_DROP -> for (i in 0..1) {
                    val p = (t * 2 + i * 0.5f) % 1f
                    drawCircle(colors.tertiary, radius = 4f, center = Offset(60f, 26f + p * 34f), alpha = 1f - p * 0.3f)
                }
                GuideStage.POUR -> {
                    val surface = cup.bottom - cup.height * fill
                    // A still stream: the rising coffee already says the cup is filling.
                    drawLine(colors.tertiary, Offset(60f, 22f), Offset(60f, surface), strokeWidth = 4f, cap = StrokeCap.Round)
                }
                else -> Unit
            }
        } }
    }
}
