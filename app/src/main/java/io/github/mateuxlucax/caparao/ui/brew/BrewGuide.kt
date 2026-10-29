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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.ui.theme.Motion
import kotlin.math.PI
import kotlin.math.sin

/** The steps the brew screen walks through, one at a time like the onboarding. A step has text only when it says something the title and the screen don't. */
enum class GuideStage(@StringRes val title: Int, @StringRes val text: Int?) {
    DOSE(R.string.dose_title, null),
    CUP(R.string.espresso_place_cup, R.string.espresso_place_cup_text),
    TARE(R.string.taring, null),
    FIRST_DROP(R.string.espresso_ready, R.string.espresso_ready_text),
    POUR(R.string.filter_ready, R.string.filter_ready_text),
    BREWING_ESPRESSO(R.string.brewing_espresso, null),
    BREWING_FILTER(R.string.brewing_filter, R.string.brewing_filter_text),
    DONE(R.string.brew_saved, null),
}

/** The stages of [mode], in order. */
fun guideStages(mode: BrewMode): List<GuideStage> = when (mode) {
    BrewMode.ESPRESSO -> listOf(GuideStage.DOSE, GuideStage.CUP, GuideStage.TARE, GuideStage.FIRST_DROP, GuideStage.BREWING_ESPRESSO, GuideStage.DONE)
    BrewMode.FILTER -> listOf(GuideStage.DOSE, GuideStage.TARE, GuideStage.POUR, GuideStage.BREWING_FILTER, GuideStage.DONE)
}

/** Where the screen stands, from its step and the detector's phase. */
fun guideStage(mode: BrewMode, step: BrewStep, phase: BrewPhase): GuideStage = when (step) {
    BrewStep.DOSE -> GuideStage.DOSE
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
            stringResource(R.string.step_of, index + 1, stages.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The current stage's animation, title and instruction, moving along the shared X axis when the stage changes. */
@Composable
fun GuideHeader(stage: GuideStage, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    AnimatedContent(
        stage,
        modifier.fillMaxWidth(),
        transitionSpec = { Motion.sharedAxisX(targetState.ordinal > initialState.ordinal, density) },
        label = "stage",
    ) { shown ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StageAnimation(shown)
            Text(stringResource(shown.title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            shown.text?.let {
                Text(stringResource(it), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

private const val LOOP_MS = 2_400

/**
 * A small looping picture of what to do: the cup coming down onto the scale, the LED pulsing while it tares, drops or
 * a stream falling into the cup. Nothing for the brewing stages, where the live readout is the picture. With animations
 * off, a still frame.
 */
@Composable
private fun StageAnimation(stage: GuideStage) {
    if (stage == GuideStage.DOSE || stage == GuideStage.DONE || stage == GuideStage.BREWING_ESPRESSO || stage == GuideStage.BREWING_FILTER) return
    val context = LocalContext.current
    val still = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val t = if (still) {
        0.7f
    } else {
        val transition = rememberInfiniteTransition(label = "guide")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(LOOP_MS, easing = LinearEasing)), label = "t").value
    }
    val colors = MaterialTheme.colorScheme
    Canvas(Modifier.size(112.dp)) {
        // Drawn in a 120-unit square.
        scale(size.width / 120f, pivot = Offset.Zero) {
            val cupDrop = if (stage == GuideStage.CUP) (1f - (t / 0.5f).coerceIn(0f, 1f)).let { it * it } * 40f else 0f
            drawCup(colors.onSurface, cupDrop)
            drawRoundRect(colors.surfaceContainerHighest, Offset(18f, 95f), Size(84f, 11f), CornerRadius(5.5f))
            drawRoundRect(colors.outlineVariant, Offset(18f, 95f), Size(84f, 11f), CornerRadius(5.5f), style = Stroke(1f))
            val led = when (stage) {
                GuideStage.TARE -> 0.35f + 0.65f * (0.5f + 0.5f * sin(2 * PI * t * 2).toFloat())
                GuideStage.CUP -> if (t > 0.55f) 1f else 0.2f
                else -> 1f
            }
            drawCircle(colors.primary.copy(alpha = led), radius = 2.5f, center = Offset(60f, 100.5f))
            when (stage) {
                GuideStage.FIRST_DROP -> for (i in 0..1) {
                    val p = (t * 2 + i * 0.5f) % 1f
                    drawCircle(colors.primary, radius = 2.5f, center = Offset(60f, 8f + p * 78f))
                }
                GuideStage.POUR -> {
                    val wobble = 0.6f * sin(t * 2 * PI.toFloat() * 6)
                    drawLine(colors.primary.copy(alpha = 0.9f), Offset(60f + wobble, 4f), Offset(60f, 86f), strokeWidth = 2f)
                }
                else -> Unit
            }
        }
    }
}

private fun DrawScope.drawCup(color: androidx.compose.ui.graphics.Color, lift: Float) {
    val bowl = Path().apply {
        moveTo(39f, 55f - lift)
        lineTo(81f, 55f - lift)
        lineTo(77f, 82f - lift)
        quadraticTo(75f, 93f - lift, 64f, 93f - lift)
        lineTo(56f, 93f - lift)
        quadraticTo(45f, 93f - lift, 43f, 82f - lift)
        close()
    }
    drawPath(bowl, color, style = Stroke(width = 2f))
}
