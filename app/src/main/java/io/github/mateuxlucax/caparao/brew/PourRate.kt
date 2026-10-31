package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.RecipeStep

/**
 * The pour rate to aim for during a bloom or pour, in g/s, fixed for the whole step: the step's water (from the last
 * target to this one) over the time until the next step starts. Without a next step it is [DEFAULT] (the gentle spiral
 * most recipes ask for). Clamped to what a kettle can pour.
 */
object PourRate {
    const val DEFAULT = 5.0
    val RANGE = 1.0..12.0

    fun target(step: RecipeStep, previousGrams: Double?, next: RecipeStep?, weightGrams: Double): Double? {
        val goal = step.targetWeightGrams?.takeIf { step.kind.pours } ?: return null
        if (weightGrams >= goal) return null
        val ms = next?.targetAtMs?.minus(step.targetAtMs)?.takeIf { it > 0 } ?: return DEFAULT
        return ((goal - (previousGrams ?: 0.0)) / (ms / 1000.0)).coerceIn(RANGE)
    }

    /** Within a quarter of the target either way. */
    fun onPace(flow: Double, target: Double) = flow in target * 0.75..target * 1.25
}
