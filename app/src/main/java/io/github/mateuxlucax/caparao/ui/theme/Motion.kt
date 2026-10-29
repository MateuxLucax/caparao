package io.github.mateuxlucax.caparao.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * Material 3 motion tokens (m3.material.io/styles/motion/easing-and-duration/tokens-specs) and the transition patterns
 * built from them, so every screen moves the same way (docs/material3-review.md).
 */
object Motion {
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val SHORT_4 = 200
    const val MEDIUM_2 = 300
    const val MEDIUM_4 = 400

    /** For values that change in place: colors, progress. */
    fun <T> standard(durationMs: Int = SHORT_4): FiniteAnimationSpec<T> = tween(durationMs, easing = Standard)

    /**
     * Shared axis X: forward slides in from the right, back from the left, both fading. For steps in a flow and for
     * opening a screen on top of another.
     */
    fun sharedAxisX(forward: Boolean, density: Density): ContentTransform {
        val offset = with(density) { 30.dp.roundToPx() }
        val enter: EnterTransition = slideInHorizontally(tween(MEDIUM_2, easing = EmphasizedDecelerate)) { if (forward) offset else -offset } +
            fadeIn(tween(MEDIUM_2 / 2, delayMillis = MEDIUM_2 / 4, easing = EmphasizedDecelerate))
        val exit: ExitTransition = slideOutHorizontally(tween(MEDIUM_2, easing = EmphasizedAccelerate)) { if (forward) -offset else offset } +
            fadeOut(tween(MEDIUM_2 / 4, easing = EmphasizedAccelerate))
        return enter togetherWith exit
    }

    /** Fade through: between top-level destinations that aren't related, like the tabs. */
    fun fadeThrough(): ContentTransform =
        (fadeIn(tween(210, delayMillis = 90, easing = EmphasizedDecelerate)) + scaleIn(tween(210, delayMillis = 90, easing = EmphasizedDecelerate), initialScale = 0.92f)) togetherWith
            fadeOut(tween(90, easing = EmphasizedAccelerate))
}
