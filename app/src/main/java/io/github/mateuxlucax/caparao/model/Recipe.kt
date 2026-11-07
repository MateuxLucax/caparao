package io.github.mateuxlucax.caparao.model

/**
 * A reusable way to brew (#102). Filter and French press recipes carry [steps]; espresso recipes have none and are
 * guided by the target window [targetMinMs]..[targetMaxMs] instead; filter recipes use that window for the ideal total
 * time from the first pour. [id] is generated on the phone.
 */
data class Recipe(
    val id: String,
    val name: String,
    val mode: BrewMode,
    /** Free text with suggestions: V60, Melitta, Kalita, AeroPress, Espresso, French press… */
    val method: String,
    val doseGrams: Double,
    /** The X of 1:X. */
    val targetRatio: Double,
    /** Free text: grinders have no common scale. */
    val grind: String? = null,
    val waterTempCelsius: Int? = null,
    val notes: String? = null,
    /** Espresso shot time from first drop, or filter total time from first pour; null for French press. */
    val targetMinMs: Long? = null,
    val targetMaxMs: Long? = null,
    /** In order; empty for espresso. */
    val steps: List<RecipeStep> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** Espresso: the beverage to stop at. Filter or French press: the water to pour. */
    val targetYieldGrams: Double get() = doseGrams * targetRatio
}

/**
 * One step of a filter or French press recipe. [targetWeightGrams] is cumulative, as the scale shows it, and null for
 * [StepKind.WAIT], [StepKind.SWIRL], [StepKind.STIR] and [StepKind.PRESS]; [targetAtMs] is when the step should start,
 * from the first water.
 */
data class RecipeStep(
    val kind: StepKind,
    val targetWeightGrams: Double?,
    val targetAtMs: Long,
    /** Optional ideal pour rate, in grams per second. */
    val targetFlowGramsPerSecond: Double? = null,
)

enum class StepKind {
    BLOOM, POUR, WAIT, SWIRL, STIR, PRESS;

    /** Bloom and pour end on a weight; wait, swirl and stir on a time. */
    val pours: Boolean get() = this == BLOOM || this == POUR
}
