package io.github.mateuxlucax.caparao.ui.brew

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.caparao.ui.components.CupArt.drawCup
import io.github.mateuxlucax.caparao.ui.components.CupArt.drawScale
import io.github.mateuxlucax.caparao.ui.theme.Motion
import io.github.mateuxlucax.caparao.ui.theme.animationsOff

/** Seconds between drops at the start of the wait, and once the wait has run [DROP_RAMP_S] seconds. */
private const val DROP_SLOW_S = 1.6
private const val DROP_FAST_S = 0.5
private const val DROP_RAMP_S = 20.0

/** Drops in one wait before the drip stops; an endless loop would keep the screen busy. */
private const val MAX_DROPS = 40

/** Seconds between drops while waiting for the first one: slow when the wait starts, quicker the longer it runs (#194). */
internal fun dropPeriodSeconds(waitedSeconds: Double): Double {
    val ramp = (waitedSeconds / DROP_RAMP_S).coerceIn(0.0, 1.0)
    return DROP_SLOW_S + (DROP_FAST_S - DROP_SLOW_S) * ramp
}

/**
 * Espresso's guide picture (#194), driven by the scale's steps instead of a loop: the cup lands on the scale when the
 * cup is detected, the LED lights when the tare is confirmed, and drops fall into the cup only while the shot waits for
 * its first drop, quicker the longer the wait runs. With animations off it is a still frame of the same state, with no
 * drops.
 */
@Composable
internal fun EspressoPicture(stage: GuideStage) {
    val still = animationsOff()
    val colors = MaterialTheme.colorScheme
    // 1 while the cup hovers above the scale, 0 once it has landed on it.
    val hover by animateFloatAsState(
        if (stage == GuideStage.CUP) 1f else 0f,
        if (still) snap() else spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "cupHover",
    )
    // The LED is lit from the confirmed tare on, which is the first-drop stage.
    val led by animateFloatAsState(
        if (stage == GuideStage.FIRST_DROP) 1f else 0f,
        if (still) snap() else tween(Motion.MEDIUM_2, easing = Motion.Standard),
        label = "led",
    )
    val waiting = stage == GuideStage.FIRST_DROP && !still
    // One drop per cycle, each falling for the period the wait has reached so far; read only while drawing.
    val drop = remember { Animatable(0f) }
    LaunchedEffect(waiting) {
        if (!waiting) return@LaunchedEffect
        var waited = 0.0
        // A bounded run of drops, so the screen settles once the wait has gone on for a while.
        repeat(MAX_DROPS) {
            val period = dropPeriodSeconds(waited)
            drop.snapTo(0f)
            drop.animateTo(1f, tween((period * 1_000).toInt(), easing = LinearEasing))
            waited += period
        }
    }
    Canvas(Modifier.size(72.dp)) {
        // Drawn in a 120-unit square: the scale at the bottom, the cup on it, the drops above the cup's mouth.
        scale(size.width / 120f, pivot = Offset.Zero) {
            val lift = 40f * hover
            val cup = Rect(36f, 62f - lift, 84f, 98f - lift)
            drawScale(Rect(16f, 100f, 104f, 114f), colors.secondary, colors.onSecondary, 0.25f + 0.75f * led)
            drawCup(cup, colors.primary, colors.tertiary, 0f)
            if (waiting) {
                for (i in 0..1) {
                    val p = (drop.value + i * 0.5f) % 1f
                    drawCircle(colors.tertiary, radius = 4f, center = Offset(60f, 26f + p * 34f), alpha = 1f - p * 0.3f)
                }
            }
        }
    }
}
