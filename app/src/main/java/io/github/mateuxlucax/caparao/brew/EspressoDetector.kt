package io.github.mateuxlucax.caparao.brew

import kotlin.math.abs

/** Thresholds for [EspressoDetector]. Weights in grams, times in milliseconds. */
data class EspressoConfig(
    /** How much heavier than the starting weight the scale must get to count as a cup. */
    val cupMinGrams: Double = 20.0,
    /** Max spread (max − min) over [stableWindowMs] for the weight to count as settled. */
    val stableSpreadGrams: Double = 0.3,
    val stableWindowMs: Long = 1_000,
    val tare: TareConfig = TareConfig(),
    /** The first drop: this weight on two consecutive readings, the second not lower. */
    val firstDropGrams: Double = 0.3,
    /** In READY, a weight this far below zero means the cup was taken away. */
    val cupLiftedGrams: Double = -5.0,
    /** The brew ends when the weight rose at most [flatRiseGrams] over [flatWindowMs]... */
    val flatRiseGrams: Double = 0.2,
    val flatWindowMs: Long = 4_000,
    /** ...and at least this long passed since the first drop. */
    val minBrewMs: Long = 5_000,
    /** Mid-brew, a drop of this much within [liftWindowMs] means the cup was lifted. */
    val liftDropGrams: Double = 5.0,
    val liftWindowMs: Long = 500,
)

/** How a tare is confirmed; shared by both modes. */
data class TareConfig(
    /** |weight| at or below this counts as zero. */
    val zeroToleranceGrams: Double = 0.3,
    /** Zero must hold this long. */
    val zeroHoldMs: Long = 500,
    /** Without zero after this long, the tare is sent once more. */
    val retryAfterMs: Long = 3_000,
)

/**
 * Fully automatic espresso detection, modeled on the Timemore auto-timer: put the cup down, it tares, the first drop
 * starts the brew and the weight going flat ends it. Works on weights only: the scale's flow saturates at 99.9 and the
 * overload flag fires when the cup lands, so neither is trusted.
 */
class EspressoDetector(private val config: EspressoConfig = EspressoConfig()) : BrewDetector {
    override var phase: BrewPhase = BrewPhase.WAITING_FOR_CUP
        private set
    override var result: BrewResult? = null
        private set

    private val tare = TareWatcher(config.tare)
    private val recent = ArrayDeque<TimedReading>()
    private var firstAtMs: Long? = null
    private var baselineGrams: Double? = null
    private var checkedAlreadyPlaced = false
    private var pendingDrop: TimedReading? = null
    private var taredAtMs: Long? = null
    private var startedAtMs = 0L
    private var maxGrams = 0.0
    private var lastGrams = 0.0

    /** The cup is already on the scale and tared, or the user wants to skip waiting: tare now and get ready. */
    fun armNow(atMs: Long): ScaleCommand {
        startTaring(atMs)
        return ScaleCommand.TARE
    }

    override fun onReading(reading: TimedReading): ScaleCommand? {
        val grams = reading.reading.weightGrams
        lastGrams = grams
        remember(reading)
        return when (phase) {
            BrewPhase.WAITING_FOR_CUP -> waitForCup(reading)
            BrewPhase.TARING -> tare.onReading(reading).also { if (tare.zeroedAtMs != null) becomeReady() }
            BrewPhase.READY -> { awaitFirstDrop(reading); null }
            BrewPhase.BREWING -> { followBrew(reading); null }
            BrewPhase.DONE -> null
        }
    }

    override fun stop(atMs: Long) = finish(atMs, endingGrams(), interrupted = false)

    override fun interrupt(atMs: Long) = finish(atMs, endingGrams(), interrupted = true)

    /** The cup taken off too slowly to count as a lift still leaves the weight far below what was poured. */
    private fun endingGrams() = if (maxGrams - lastGrams >= config.liftDropGrams) maxGrams else lastGrams

    private fun finish(atMs: Long, yieldGrams: Double, interrupted: Boolean) {
        if (phase == BrewPhase.DONE) return
        result = if (phase == BrewPhase.BREWING) {
            BrewResult(startedAtMs, atMs, yieldGrams, taredAtMs, interrupted)
        } else {
            null
        }
        phase = BrewPhase.DONE
    }

    private fun remember(reading: TimedReading) {
        recent.addLast(reading)
        // Keep a little more than the longest window we look back over.
        val keepMs = maxOf(config.flatWindowMs, config.stableWindowMs) + 1_000
        while (reading.atMs - recent.first().atMs > keepMs) recent.removeFirst()
    }

    private fun waitForCup(reading: TimedReading): ScaleCommand? {
        val grams = reading.reading.weightGrams
        val first = firstAtMs ?: reading.atMs.also { firstAtMs = it }
        val baseline = baselineGrams ?: grams.also { baselineGrams = it }
        if (!stableOver(reading.atMs, config.stableWindowMs)) return null
        // The very first full second decides whether the cup was already there when the screen opened.
        val alreadyPlaced = !checkedAlreadyPlaced && reading.atMs - first >= config.stableWindowMs &&
            grams >= config.cupMinGrams
        if (reading.atMs - first >= config.stableWindowMs) checkedAlreadyPlaced = true
        if (alreadyPlaced || grams - baseline >= config.cupMinGrams) {
            startTaring(reading.atMs)
            return ScaleCommand.TARE
        }
        return null
    }

    /** True when the readings cover the whole window ending at [nowMs] and spread at most the stable spread. */
    private fun stableOver(nowMs: Long, windowMs: Long): Boolean {
        if (nowMs - recent.first().atMs < windowMs) return false
        val window = recent.filter { it.atMs >= nowMs - windowMs }.map { it.reading.weightGrams }
        return window.max() - window.min() <= config.stableSpreadGrams + EPSILON
    }

    private fun startTaring(atMs: Long) {
        phase = BrewPhase.TARING
        tare.begin(atMs)
    }

    private fun becomeReady() {
        taredAtMs = tare.zeroedAtMs
        pendingDrop = null
        phase = BrewPhase.READY
    }

    private fun awaitFirstDrop(reading: TimedReading) {
        val grams = reading.reading.weightGrams
        if (grams <= config.cupLiftedGrams) {
            // Cup taken away before brewing: start over with what is on the scale now as the baseline.
            phase = BrewPhase.WAITING_FOR_CUP
            baselineGrams = grams
            firstAtMs = reading.atMs
            checkedAlreadyPlaced = true
            taredAtMs = null
            recent.clear()
            recent.addLast(reading)
            return
        }
        val pending = pendingDrop
        when {
            grams < config.firstDropGrams - EPSILON -> pendingDrop = null
            pending != null && grams >= pending.reading.weightGrams -> startBrew(pending)
            else -> pendingDrop = reading
        }
    }

    private fun startBrew(first: TimedReading) {
        phase = BrewPhase.BREWING
        startedAtMs = first.atMs
        maxGrams = first.reading.weightGrams
    }

    private fun followBrew(reading: TimedReading) {
        val now = reading.atMs
        val grams = reading.reading.weightGrams

        // Lifted: the weight fell by a lot within a fraction of a second.
        val liftWindow = recent.filter { it.atMs >= now - config.liftWindowMs && it.atMs >= startedAtMs }
        val peak = liftWindow.maxBy { it.reading.weightGrams }
        if (peak.reading.weightGrams - grams >= config.liftDropGrams) {
            finish(peak.atMs, maxGrams, interrupted = false)
            return
        }
        maxGrams = maxOf(maxGrams, grams)

        if (now - startedAtMs < config.minBrewMs) return
        // The flat window starts at the latest reading at or before now − flatWindowMs; that reading is taken as
        // the last moment the weight was still rising, so it is endedAtMs.
        val windowStart = recent.lastOrNull { it.atMs <= now - config.flatWindowMs } ?: return
        if (windowStart.atMs < startedAtMs) return
        val windowMax = recent.filter { it.atMs >= windowStart.atMs }.maxOf { it.reading.weightGrams }
        if (windowMax - windowStart.reading.weightGrams <= config.flatRiseGrams + EPSILON) {
            finish(windowStart.atMs, grams, interrupted = false)
        }
    }
}

/** Weights arrive in 0.1 g steps as doubles; this keeps 0.2999999 from failing a 0.3 threshold. */
internal const val EPSILON = 1e-6

/**
 * Waits for the scale to read zero after a tare, asking for it once more if it does not. Shared by both detectors.
 */
internal class TareWatcher(private val config: TareConfig) {
    private var requestedAtMs = 0L
    private var retried = false
    private var zeroSinceMs: Long? = null

    /** When zero was first read in the run that confirmed the tare; null until then. */
    var zeroedAtMs: Long? = null
        private set

    fun begin(atMs: Long) {
        requestedAtMs = atMs
        retried = false
        zeroSinceMs = null
        zeroedAtMs = null
    }

    fun onReading(reading: TimedReading): ScaleCommand? {
        val now = reading.atMs
        if (abs(reading.reading.weightGrams) <= config.zeroToleranceGrams + EPSILON) {
            val since = zeroSinceMs ?: now.also { zeroSinceMs = it }
            if (now - since >= config.zeroHoldMs) zeroedAtMs = since
            return null
        }
        zeroSinceMs = null
        if (!retried && now - requestedAtMs >= config.retryAfterMs) {
            retried = true
            return ScaleCommand.TARE
        }
        return null
    }
}
