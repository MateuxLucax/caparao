package io.github.mateuxlucax.caparao.model

/**
 * A reusable way to brew (docs/adr/0018-recipes.md). Filter recipes carry [steps]; espresso recipes have none and are
 * guided by the target window [targetMinMs]..[targetMaxMs] instead. [id] is generated on the phone.
 */
data class Recipe(
    val id: String,
    val name: String,
    val mode: BrewMode,
    /** Free text with suggestions: V60, Melitta, Kalita, AeroPress, Espresso… */
    val method: String,
    val doseGrams: Double,
    /** The X of 1:X. */
    val targetRatio: Double,
    /** Free text: grinders have no common scale. */
    val grind: String? = null,
    val waterTempCelsius: Int? = null,
    val notes: String? = null,
    /** Espresso only: the shot time window, from the first drop. */
    val targetMinMs: Long? = null,
    val targetMaxMs: Long? = null,
    /** In order; empty for espresso. */
    val steps: List<RecipeStep> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** Espresso: the beverage to stop at. Filter: the water to pour. */
    val targetYieldGrams: Double get() = doseGrams * targetRatio
}

/**
 * One step of a filter recipe. [targetWeightGrams] is cumulative, as the scale shows it, and null for [StepKind.WAIT]
 * and [StepKind.SWIRL]; [targetAtMs] is when the step should start, from the first water.
 */
data class RecipeStep(val kind: StepKind, val targetWeightGrams: Double?, val targetAtMs: Long)

enum class StepKind {
    BLOOM, POUR, WAIT, SWIRL;

    /** Bloom and pour end on a weight; wait and swirl on a time. */
    val pours: Boolean get() = this == BLOOM || this == POUR
}
