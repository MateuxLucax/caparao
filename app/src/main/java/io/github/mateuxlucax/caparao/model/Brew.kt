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
    /** Coffee dose; null when the user skipped it. */
    val doseGrams: Double?,
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
) {
    /** 1:X, or null without a dose. */
    val ratio: Double? get() = doseGrams?.takeIf { it > 0 }?.let { yieldGrams / it }
}

/** One point of a brew's curve, [atMs] after the brew started. */
data class BrewSample(val atMs: Long, val weightGrams: Double, val flowGramsPerSecond: Double)
