package io.github.mateuxlucax.caparao.brew

/** Detects that the French press has been removed after the timed steep, without ending the brew. */
class FrenchPressFinishTracker(
    private val removalThresholdGrams: Double = REMOVAL_THRESHOLD_GRAMS,
    private val removalHoldMs: Long = REMOVAL_HOLD_MS,
) {
    init {
        require(removalThresholdGrams >= 0.0)
        require(removalHoldMs > 0)
    }

    /** First low reading in the current continuous removal candidate; null when the weight is back on the scale. */
    var removalStartedAtMs: Long? = null
        private set

    /** First low reading in the confirmed removal; this is where French press curve samples stop. */
    var removedAtMs: Long? = null
        private set

    /**
     * Starts the candidate at the first low reading of the run that ends at the newest of [readings], not before
     * [sinceMs] (#279). The tracker is made at the deadline, so a removal that began earlier would otherwise start
     * counting only from the deadline. Does nothing when the newest reading is not low.
     */
    fun backdateFrom(readings: List<TimedReading>, sinceMs: Long) {
        if (removedAtMs != null) return
        var began: Long? = null
        for (timed in readings.asReversed()) {
            val weight = timed.reading.weightGrams
            if (timed.atMs < sinceMs || !weight.isFinite() || weight > removalThresholdGrams) break
            began = timed.atMs
        }
        removalStartedAtMs = began ?: removalStartedAtMs
    }

    /** True once the weight has stayed at or below the tare for [removalHoldMs]. It never changes brew completion. */
    fun update(atMs: Long, weightGrams: Double): Boolean {
        if (removedAtMs != null) return true
        if (!weightGrams.isFinite() || weightGrams > removalThresholdGrams) {
            removalStartedAtMs = null
            return false
        }
        val began = removalStartedAtMs ?: atMs.also { removalStartedAtMs = it }
        if (atMs - began < removalHoldMs) return false
        removedAtMs = began
        return true
    }

    companion object {
        /** Tare noise and a few tenths of a gram are still effectively empty. */
        const val REMOVAL_THRESHOLD_GRAMS = 5.0
        /** Ignore a brief dip or bump; the carafe must remain off the scale. */
        const val REMOVAL_HOLD_MS = 5_000L
    }
}
