package io.github.mateuxlucax.caparao.model

/**
 * A finished brew. [id] is generated on the phone and is the upload's idempotency key (docs/adr/0007-idempotent-upload.md).
 * Times are epoch milliseconds; the curve is in [BrewSample]s relative to [startedAt].
 */
data class Brew(
    val id: String,
    val mode: BrewMode,
    /** The first drop (espresso) or the first water (filter). */
    val startedAt: Long,
    val durationMs: Long,
    /** Coffee dose, weighed or typed before the brew; always set (contract/README.md). */
    val doseGrams: Double,
    /** Espresso: the beverage in the cup. Filter: the water poured. */
    val yieldGrams: Double,
    /** Espresso only: from the tare with the cup on the scale to the first drop. */
    val timeToFirstDropMs: Long?,
    val flowAvgGramsPerSecond: Double,
    val flowPeakGramsPerSecond: Double,
    /** The scale disconnected before the brew ended; the numbers cover what was recorded. */
    val interrupted: Boolean,
    /** When the server confirmed it; null while only on the phone. */
    val syncedAt: Long? = null,
    /** The recipe it followed; the recipe may since have been edited or deleted (docs/adr/0018-recipes.md). */
    val recipeId: String? = null,
    /** A copy of the recipe's name at brew time, so history reads well after a rename or delete. */
    val recipeName: String? = null,
    /** The rest is filled in after the brew (docs/adr/0021-brew-notes.md). */
    val coffee: String? = null,
    val grind: String? = null,
    val waterTempCelsius: Int? = null,
    /** 1–5. */
    val rating: Int? = null,
    val notes: String? = null,
) {
    /** The X of 1:X. */
    val ratio: Double get() = yieldGrams / doseGrams
}

/** One point of a brew's curve, [atMs] after the brew started. */
data class BrewSample(val atMs: Long, val weightGrams: Double, val flowGramsPerSecond: Double)
