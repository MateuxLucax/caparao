package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.ScaleReading

/** A scale reading stamped with the phone's monotonic clock, in milliseconds. */
data class TimedReading(val atMs: Long, val reading: ScaleReading)

/** Where a brew stands, for the screen. */
enum class BrewPhase {
    /** Espresso: waiting for a cup to be put on the scale and settle. */
    WAITING_FOR_CUP,

    /** A tare was asked for; waiting for the scale to read zero. */
    TARING,

    /** Tared and waiting for the first drop (espresso) or the first water (filter). */
    READY,

    BREWING,

    /** Filter only: the dripper is lifted and the brew clock is stopped. */
    PAUSED,

    /** Ended; [BrewDetector.result] holds the outcome. */
    DONE,
}

/** What the detector wants done on the scale, returned from [BrewDetector.onReading]. */
enum class ScaleCommand { TARE }

/** How a brew ended. */
data class BrewResult(
    /** Clock time of the first drop or first water. */
    val startedAtMs: Long,
    val endedAtMs: Long,
    val yieldGrams: Double,
    /** Clock time the scale read zero after the tare; null if unknown. */
    val taredAtMs: Long?,
    val interrupted: Boolean,
)

/** The filter-only readings the live screen needs while the dripper is away. */
data class BrewPauseState(val startedAtMs: Long, val waterWeightGrams: Double)

/**
 * Pure brew logic fed with readings, one implementation per mode (#90). Not
 * thread-safe; feed it in order from one thread.
 */
interface BrewDetector {
    val phase: BrewPhase

    /** Non-null while a filter brew is paused. */
    val pauseState: BrewPauseState? get() = null

    /** Non-null once [phase] is [BrewPhase.DONE] with something worth saving. */
    val result: BrewResult?

    /** Feeds the next reading; returns a command for the scale, if any. */
    fun onReading(reading: TimedReading): ScaleCommand?

    /** While [phase] is [BrewPhase.TARING]: the scale did not read zero even after the retry. A late zero clears it. */
    val tareTimedOut: Boolean

    /** The user asked for the tare again from the screen; the zero wait starts over at [atMs]. */
    fun retryTare(atMs: Long): ScaleCommand

    /** The user tapped stop or done. Ends a brew in progress; before it started, nothing is saved. */
    fun stop(atMs: Long)

    /** The scale disconnected: a brew in progress ends as interrupted. */
    fun interrupt(atMs: Long)
}
