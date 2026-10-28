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
)

/**
 * Free-pour filter detection. The screen opens with dripper and server on the scale, so it tares straight away; the
 * first water starts the brew, and the user tapping done or lifting the dripper off ends it.
 */
class FilterDetector(private val config: FilterConfig = FilterConfig()) : BrewDetector {
    override var phase: BrewPhase = BrewPhase.TARING
        private set
    override var result: BrewResult? = null
        private set

    private val tare = TareWatcher(config.tare)
    private var tareSent = false
    private var taredAtMs: Long? = null
    private var pendingWater = false
    private var firstWaterAtMs = 0L
    private var previous: TimedReading? = null
    private var startedAtMs = 0L
    private var maxGrams = 0.0
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

    override fun stop(atMs: Long) = end(atMs, interrupted = false)

    override fun interrupt(atMs: Long) = end(atMs, interrupted = true)

    /** Stopped while the weight is down (server or dripper off before the lift hold ran out): it ended at the drop. */
    private fun end(atMs: Long, interrupted: Boolean) {
        val began = dropBeganAtMs
        if (began != null) finish(began, maxGrams, interrupted) else finish(atMs, lastGrams, interrupted)
    }

    private fun finish(atMs: Long, yieldGrams: Double, interrupted: Boolean) {
        if (phase == BrewPhase.DONE) return
        result = if (phase == BrewPhase.BREWING) {
            BrewResult(startedAtMs, atMs, yieldGrams, taredAtMs, interrupted)
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
        val grams = reading.reading.weightGrams
        val drop = maxGrams - grams
        val lifted = drop >= config.liftDropGrams ||
            (drop >= config.liftMinDropGrams && drop >= maxGrams * config.liftDropFraction)
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
}
