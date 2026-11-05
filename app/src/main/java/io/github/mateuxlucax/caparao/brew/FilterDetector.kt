package io.github.mateuxlucax.caparao.brew

/** Thresholds for [FilterDetector]. Weights in grams, times in milliseconds. */
data class FilterConfig(
    val tare: TareConfig = TareConfig(),
    /** The first water: this weight on two consecutive readings. */
    val firstWaterGrams: Double = 1.0,
    /** A drop from the running max this large, in grams... */
    val liftDropGrams: Double = 30.0,
    /** ...or this fraction of it (and at least [liftMinDropGrams], so noise early in the pour never counts)... */
    val liftDropFraction: Double = 0.12,
    val liftMinDropGrams: Double = 5.0,
    /** ...held this long means the dripper was lifted, not bumped. */
    val liftHoldMs: Long = 5_000,
    /** A new filter weight must stay above the previous water level without a large fall for this long. */
    val settledWeightHoldMs: Long = 1_000,
    /** A fall larger than this restarts the settling window; returning to the prior water level discards the peak. */
    val settledWeightToleranceGrams: Double = 1.0,
    /** Filter recipe targets must be held this long before the guide advances. */
    val stepTargetHoldMs: Long = 1_000,
    /** French press keeps running when the carafe is lifted to serve. */
    val liftDetectionEnabled: Boolean = true,
)

/**
 * Free-pour filter detection. The screen opens with dripper and server on the scale, so it tares straight away; the
 * first water starts the brew, and the user tapping done or lifting the dripper off ends it. French press reuses this
 * detector with lift detection disabled, so serving from the carafe cannot finish the brew.
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
    /** Highest confirmed water weight. Brief peaks that return to this level are ignored. */
    private var maxGrams = 0.0
    private var candidateStartedAtMs: Long? = null
    private var candidateMaxGrams = 0.0
    private var candidateLastGrams = 0.0
    private var lastGrams = 0.0
    /** Set while the weight is down from the max: when the drop began. */
    private var dropBeganAtMs: Long? = null

    override fun onReading(reading: TimedReading): ScaleCommand? {
        val prev = previous
        previous = reading
        lastGrams = reading.reading.weightGrams
        return when (phase) {
            BrewPhase.TARING -> taring(reading)
            BrewPhase.READY -> { awaitWater(reading); null }
            BrewPhase.BREWING -> { followBrew(reading, prev); null }
            BrewPhase.WAITING_FOR_CUP, BrewPhase.DONE -> null
        }
    }

    override fun retryTare(atMs: Long): ScaleCommand {
        tare.begin(atMs)
        return ScaleCommand.TARE
    }

    override fun stop(atMs: Long) = end(atMs, interrupted = false)

    override fun interrupt(atMs: Long) = end(atMs, interrupted = true)

    /** Stopped while the weight is down (server or dripper off before the lift hold ran out): it ended at the drop. */
    private fun end(atMs: Long, interrupted: Boolean) {
        val began = dropBeganAtMs
        when {
            began != null -> finish(began, maxGrams, interrupted)
            !config.liftDetectionEnabled -> finish(atMs, maxGrams, interrupted)
            else -> finish(atMs, lastGrams, interrupted)
        }
    }

    private fun finish(atMs: Long, yieldGrams: Double, interrupted: Boolean) {
        if (phase == BrewPhase.DONE) return
        result = if (phase == BrewPhase.BREWING) {
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

        // French press intentionally keeps the original running maximum behavior until its own review (#214).
        val grams = reading.reading.weightGrams
        val drop = maxGrams - grams
        val lifted = config.liftDetectionEnabled && (
            drop >= config.liftDropGrams ||
                (drop >= config.liftMinDropGrams && drop >= maxGrams * config.liftDropFraction)
            )
        if (!lifted) {
            // Back up (or never down): a bump, keep brewing.
            dropBeganAtMs = null
            maxGrams = maxOf(maxGrams, grams)
            return
        }
        // The drop began at the last reading before the weight fell.
        val began = dropBeganAtMs ?: (prev?.atMs ?: reading.atMs).also { dropBeganAtMs = it }
        if (reading.atMs - began >= config.liftHoldMs) finish(began, maxGrams, interrupted = false)
    }

    private fun followFilterBrew(reading: TimedReading, prev: TimedReading?) {
        val grams = reading.reading.weightGrams
        val returnedToWater = grams <= maxGrams
        if (returnedToWater) {
            // A utensil or hand that leaves the scale returns to the previous water level; discard its peak.
            clearCandidate()
        } else {
            updateSettledCandidate(reading)
        }

        val drop = maxGrams - grams
        val lifted = drop >= config.liftDropGrams ||
            (drop >= config.liftMinDropGrams && drop >= maxGrams * config.liftDropFraction)
        if (!lifted) {
            dropBeganAtMs = null
            return
        }

        // The drop began at the last reading before the weight fell.
        val began = dropBeganAtMs ?: (prev?.atMs ?: reading.atMs).also { dropBeganAtMs = it }
        if (reading.atMs - began >= config.liftHoldMs) finish(began, maxGrams, interrupted = false)
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
            // A changing pour can wobble; restart the settling window at its latest level.
            candidateStartedAtMs = reading.atMs
            candidateMaxGrams = grams
            candidateLastGrams = grams
            return
        }

        val nextMax = maxOf(candidateMaxGrams, grams)
        candidateMaxGrams = nextMax
        candidateLastGrams = grams
        if (reading.atMs - startedAt >= config.settledWeightHoldMs) {
            maxGrams = maxOf(maxGrams, candidateMaxGrams)
            clearCandidate()
        }
    }

    private fun clearCandidate() {
        candidateStartedAtMs = null
        candidateMaxGrams = 0.0
        candidateLastGrams = 0.0
    }
}
