package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.RecipeStep

/**
 * The pour rate to aim for during a bloom or pour, in g/s: the water still to pour over the time left before the next
 * step starts, so a late pour asks for more. Without a next step, or once its start has passed, it falls back to
 * [DEFAULT] (the gentle spiral most recipes ask for). Clamped to what a kettle can pour.
 */
object PourRate {
    const val DEFAULT = 5.0
    val RANGE = 1.0..12.0
    private const val MIN_LEFT_MS = 3_000L

    fun target(step: RecipeStep, next: RecipeStep?, elapsedMs: Long, weightGrams: Double): Double? {
        val goal = step.targetWeightGrams?.takeIf { step.kind.pours } ?: return null
        val left = goal - weightGrams
        if (left <= 0) return null
        val leftMs = next?.targetAtMs?.minus(maxOf(elapsedMs, step.targetAtMs))
        if (leftMs == null || leftMs < MIN_LEFT_MS) return DEFAULT
        return (left / (leftMs / 1000.0)).coerceIn(RANGE)
    }

    /** Within a quarter of the target either way. */
    fun onPace(flow: Double, target: Double) = flow in target * 0.75..target * 1.25
}
