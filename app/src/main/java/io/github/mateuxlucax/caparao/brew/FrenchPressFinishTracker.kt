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
