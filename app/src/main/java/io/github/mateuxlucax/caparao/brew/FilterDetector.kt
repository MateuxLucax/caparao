package io.github.mateuxlucax.caparao.brew

/** Thresholds for [FilterDetector]. Weights in grams, times in milliseconds. */
data class FilterConfig(
    val tare: TareConfig = TareConfig(),
    /** The first water: this weight on two consecutive readings. */
    val firstWaterGrams: Double = 1.0,
    /** A drop from the settled water weight this large, in grams... */
    val liftDropGrams: Double = 30.0,
    /** ...or this fraction of it (and at least [liftMinDropGrams], so noise early in the pour never counts)... */
    val liftDropFraction: Double = 0.12,
    val liftMinDropGrams: Double = 5.0,
    /** A new filter weight must stay above the previous water level without a large fall for this long. */
    val settledWeightHoldMs: Long = 1_000,
    /** A fall larger than this keeps a candidate provisional until it returns or a lift is confirmed. */
    val settledWeightToleranceGrams: Double = 1.0,
    /** Rises faster than this stay provisional longer; rate alone never rejects a rise. */
    val maxWaterRiseGramsPerSecond: Double = 25.0,
    /** A fast rise must stay above the previous water level this long before it is confirmed. */
    val fastRiseHoldMs: Long = 2_000,
    /** Filter recipe targets must be held this long before the guide advances. */
    val stepTargetHoldMs: Long = 1_000,
    /** A lifted dripper can stay off the scale this long before the brew ends at the lift. */
    val pauseTimeoutMs: Long = 30_000,
    /** Weight must return this close to the pre-lift water level to resume. */
    val pauseReturnToleranceGrams: Double = 3.0,
    /** The returned weight must hold before the timer resumes. */
    val pauseResumeHoldMs: Long = 1_000,
    /** Provisional handling settle window after a dripper return; tuned in #200. */
    val resumeSettleWindowMs: Long = 2_000,
    /** French press keeps running when the carafe is lifted to serve. */
    val liftDetectionEnabled: Boolean = true,
)

/**
 * Free-pour filter detection. The screen opens with dripper and server on the scale, so it tares straight away; the
 * first water starts the brew, and the user tapping done ends it. A filter dripper lift pauses at once and ends at the
 * lift after the timeout; French press reuses this detector with lift detection disabled.
 */
class FilterDetector(private val config: FilterConfig = FilterConfig()) : BrewDetector {
    override var phase: BrewPhase = BrewPhase.TARING
        private set
    override var result: BrewResult? = null
        private set
    override val tareTimedOut: Boolean get() = tare.timedOut

    private val tare = TareWatcher(config.tare)
    private var tareSent = false
    private var taredAtMs: Long? = null
    private var pendingWater = false
    private var firstWaterAtMs = 0L
    private var previous: TimedReading? = null
    var startedAtMs: Long? = null
        private set
    /** Last confirmed water weight. Brief peaks that return to this level are ignored. */
    private var maxGrams = 0.0
    private var candidateStartedAtMs: Long? = null
    private var candidateMaxGrams = 0.0
    private var candidateLastGrams = 0.0
    private var candidateFell = false
    private var candidateReturnedAtMs: Long? = null
    private var candidateRequiresFastRiseHold = false
    private var decreaseStartedAtMs: Long? = null
    private var decreaseMinGrams = 0.0
    /** Set while the weight is down from the max: when the drop began. */
    private var dropBeganAtMs: Long? = null
    private var dropYieldGrams = 0.0
    /** Physical start of the current pause; the live timer freezes here. */
    private var pauseStartedAtMs: Long? = null
        private set
    private var resumeCandidateAtMs: Long? = null
    private var resumeSettleUntilMs: Long? = null
    private var pausedDurationMs = 0L

    override val pauseState: BrewPauseState?
        get() = pauseStartedAtMs?.takeIf { phase == BrewPhase.PAUSED }?.let { BrewPauseState(it, dropYieldGrams) }

    override fun onReading(reading: TimedReading): ScaleCommand? {
        val prev = previous
        previous = reading
        return when (phase) {
            BrewPhase.TARING -> taring(reading)
            BrewPhase.READY -> { awaitWater(reading); null }
            BrewPhase.BREWING -> { followBrew(reading, prev); null }
            BrewPhase.PAUSED -> { followPause(reading); null }
            BrewPhase.WAITING_FOR_CUP, BrewPhase.DONE -> null
        }
    }

    override fun retryTare(atMs: Long): ScaleCommand {
        tare.begin(atMs)
        return ScaleCommand.TARE
    }

    override fun stop(atMs: Long) = end(atMs, interrupted = false)

    override fun interrupt(atMs: Long) = end(atMs, interrupted = true)

    /** Stopped while paused: the brew ends at the lift, even if the scale is still reading a lower weight. */
    private fun end(atMs: Long, interrupted: Boolean) {
        val began = dropBeganAtMs
        when {
            phase == BrewPhase.PAUSED && began != null -> finish(began - pausedDurationMs, dropYieldGrams, interrupted)
            else -> finish(atMs - pausedDurationMs, maxGrams, interrupted)
        }
    }

    private fun finish(atMs: Long, yieldGrams: Double, interrupted: Boolean) {
        if (phase == BrewPhase.DONE) return
        result = if (phase == BrewPhase.BREWING || phase == BrewPhase.PAUSED) {
            BrewResult(requireNotNull(startedAtMs), atMs, yieldGrams, taredAtMs, interrupted)
        } else {
            null
        }
        phase = BrewPhase.DONE
    }

    private fun taring(reading: TimedReading): ScaleCommand? {
        if (!tareSent) {
            tareSent = true
            tare.begin(reading.atMs)
            return ScaleCommand.TARE
        }
        val command = tare.onReading(reading)
        if (tare.zeroedAtMs != null) {
            taredAtMs = tare.zeroedAtMs
            phase = BrewPhase.READY
        }
        return command
    }

    private fun awaitWater(reading: TimedReading) {
        val grams = reading.reading.weightGrams
        if (grams < config.firstWaterGrams - EPSILON) {
            pendingWater = false
            return
        }
        if (pendingWater) {
            phase = BrewPhase.BREWING
            startedAtMs = firstWaterAtMs
            maxGrams = grams
        } else {
            pendingWater = true
            firstWaterAtMs = reading.atMs
        }
    }

    private fun followBrew(reading: TimedReading, prev: TimedReading?) {
        if (config.liftDetectionEnabled) {
            followFilterBrew(reading, prev)
            return
        }

        // The live French press weight is the running maximum; the saved water is settled by FrenchPressWater (#214).
        val grams = reading.reading.weightGrams
        maxGrams = maxOf(maxGrams, grams)
    }

    private fun followFilterBrew(reading: TimedReading, prev: TimedReading?) {
        val settleUntil = resumeSettleUntilMs
        if (settleUntil != null) {
            if (reading.atMs < settleUntil) return
            resumeSettleUntilMs = null
        }

        val grams = reading.reading.weightGrams
        val previousGrams = prev?.reading?.weightGrams
        var fastRise = false
        if (previousGrams != null && grams > previousGrams) {
            val elapsedMs = (reading.atMs - prev.atMs).coerceAtLeast(1L)
            val maxRise = config.maxWaterRiseGramsPerSecond * elapsedMs / 1_000.0
            fastRise = grams - previousGrams > maxRise
        }

        val drop = maxGrams - grams
        val lifted = drop >= config.liftDropGrams ||
            (drop >= config.liftMinDropGrams && drop >= maxGrams * config.liftDropFraction)
        if (lifted) {
            clearDecreaseCandidate()
            if (candidateStartedAtMs != null && candidateReturnedAtMs == null && grams <= maxGrams) {
                // Keep a returning peak briefly so a lift crossing the old water level keeps its last pour.
                candidateReturnedAtMs = reading.atMs
            }
            val began = dropBeganAtMs ?: (prev?.atMs ?: reading.atMs).also {
                dropBeganAtMs = it
                dropYieldGrams = maxGrams
            }
            pauseStartedAtMs = began
            resumeCandidateAtMs = null
            phase = BrewPhase.PAUSED
            return
        }

        dropBeganAtMs = null
        dropYieldGrams = maxGrams
        if (grams <= maxGrams) {
            if (candidateRequiresFastRiseHold) {
                // A fast rise that returns to settled water was handling, even if a later drop is a real lift.
                clearCandidate()
            }
            if (candidateStartedAtMs != null && candidateReturnedAtMs == null) candidateReturnedAtMs = reading.atMs
            if (candidateReturnedAtMs?.let { reading.atMs - it >= config.settledWeightHoldMs } == true) clearCandidate()
            if (grams < maxGrams) updateSettledDecrease(reading) else clearDecreaseCandidate()
        } else {
            clearDecreaseCandidate()
            if (candidateReturnedAtMs != null) clearCandidate()
            if (fastRise) candidateRequiresFastRiseHold = true
            updateSettledCandidate(reading)
        }
    }

    private fun followPause(reading: TimedReading) {
        val pausedAt = pauseStartedAtMs ?: return
        if (reading.atMs - pausedAt >= config.pauseTimeoutMs) {
            finish(pausedAt - pausedDurationMs, dropYieldGrams, interrupted = false)
            return
        }

        val returned = reading.reading.weightGrams >= maxGrams - config.pauseReturnToleranceGrams
        if (!returned) {
            resumeCandidateAtMs = null
            return
        }
        val candidate = resumeCandidateAtMs
        if (candidate == null) {
            resumeCandidateAtMs = reading.atMs
            return
        }
        if (reading.atMs - candidate >= config.pauseResumeHoldMs) {
            pausedDurationMs += reading.atMs - pausedAt
            pauseStartedAtMs = null
            resumeCandidateAtMs = null
            dropBeganAtMs = null
            dropYieldGrams = maxGrams
            clearCandidate()
            clearDecreaseCandidate()
            resumeSettleUntilMs = reading.atMs + config.resumeSettleWindowMs
            phase = BrewPhase.BREWING
        }
    }

    private fun updateSettledCandidate(reading: TimedReading) {
        val grams = reading.reading.weightGrams
        val startedAt = candidateStartedAtMs
        if (startedAt == null) {
            candidateStartedAtMs = reading.atMs
            candidateMaxGrams = grams
            candidateLastGrams = grams
            return
        }

        if (grams < candidateLastGrams - config.settledWeightToleranceGrams) {
            // Keep the peak while a weight drops: it may be a lift before the pour settling window completes.
            candidateFell = true
            candidateLastGrams = grams
            return
        }

        val nextMax = maxOf(candidateMaxGrams, grams)
        candidateMaxGrams = nextMax
        candidateLastGrams = grams
        val holdMs = if (candidateRequiresFastRiseHold) config.fastRiseHoldMs else config.settledWeightHoldMs
        if (!candidateFell && reading.atMs - startedAt >= holdMs) {
            maxGrams = maxOf(maxGrams, candidateMaxGrams)
            clearCandidate()
        }
    }

    private fun clearCandidate() {
        candidateStartedAtMs = null
        candidateMaxGrams = 0.0
        candidateLastGrams = 0.0
        candidateFell = false
        candidateReturnedAtMs = null
        candidateRequiresFastRiseHold = false
    }

    private fun updateSettledDecrease(reading: TimedReading) {
        val grams = reading.reading.weightGrams
        val startedAt = decreaseStartedAtMs
        if (startedAt == null || grams < decreaseMinGrams - config.settledWeightToleranceGrams) {
            decreaseStartedAtMs = reading.atMs
            decreaseMinGrams = grams
            return
        }

        if (grams - decreaseMinGrams > config.settledWeightToleranceGrams) {
            clearDecreaseCandidate()
            return
        }

        decreaseMinGrams = minOf(decreaseMinGrams, grams)
        if (reading.atMs - startedAt >= config.settledWeightHoldMs) {
            maxGrams = decreaseMinGrams
            clearDecreaseCandidate()
        }
    }

    private fun clearDecreaseCandidate() {
        decreaseStartedAtMs = null
        decreaseMinGrams = 0.0
    }
}
