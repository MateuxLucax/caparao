package io.github.mateuxlucax.caparao.brew

import kotlin.math.min

/**
 * The settled French press water (#214) as the brew runs (#322): the answer of [FrenchPressWater.of] over the readings
 * so far, kept as each one arrives instead of rescanning the whole brew.
 *
 * Feed every reading of the brew once, in time order. [waterGrams] is then what [FrenchPressWater.of] returns for the
 * readings fed so far and [startedAtMs]; readings before it are ignored, like that function's range. The saved yield
 * still comes from [FrenchPressWater.of] once the brew ends.
 *
 * Only the newest reading and the landings still standing move the cut: the start of the fall the newest reading ends,
 * the last reading above the off-scale threshold, and the oldest landing not gone back. A landing is a rise that has
 * not gone back to the level it rose from; readings only ever clear one, so the oldest one still standing is the one
 * the cut uses. The brew is never scanned again: a reading costs the same whatever the length of the steep.
 */
class FrenchPressWaterTracker(private val startedAtMs: Long) {
    /** The readings fed so far: the settled window sits at the cut, which may be far behind the newest reading. */
    private val readings = ArrayList<TimedReading>()

    /** The last reading above the off-scale threshold, and the first reading of the fall that ends at it. */
    private var topIndex = -1
    private var topFallStart = 0

    /** The first reading of the fall that ends at the newest reading. */
    private var fallStart = 0

    /** The landings still standing, oldest first. */
    private val landings = ArrayDeque<Landing>()

    /** The cut the water was last read at, and that water. */
    private var cut = 0
    private var cutWater: Double? = null

    /** The settled water of the readings fed so far, or null before the first one. */
    var waterGrams: Double? = null
        private set

    /** Feeds one reading of the brew, in time order, and returns [waterGrams]. */
    fun update(reading: TimedReading): Double? {
        if (reading.atMs < startedAtMs) return waterGrams
        val grams = reading.reading.weightGrams
        val previous = readings.lastOrNull()?.reading?.weightGrams
        readings += reading
        val index = readings.lastIndex

        // What the readings after a landing say: off the scale neither starts nor breaks its return.
        if (grams > FrenchPressWater.OFF_SCALE_GRAMS) {
            for (landing in landings) landing.onReading(reading.atMs, grams)
            while (landings.isNotEmpty() && landings.first().returned) landings.removeFirst()
        }

        // A rise this large from weight on the scale is set on the scale, not poured.
        var low = Double.POSITIVE_INFINITY
        var j = index - 1
        while (j >= 0 && reading.atMs - readings[j].atMs <= FrenchPressWater.LANDING_WINDOW_MS) {
            low = min(low, readings[j].reading.weightGrams)
            j--
        }
        if (low > FrenchPressWater.OFF_SCALE_GRAMS && grams - low >= FrenchPressWater.LANDING_GRAMS) {
            landings += Landing(index, low)
        }

        // The cut: the level before the newest fall or the removal, or before the oldest landing still standing.
        fallStart = if (previous != null && previous > grams + FrenchPressWater.SETTLED_TOLERANCE_GRAMS) fallStart else index
        if (grams > FrenchPressWater.OFF_SCALE_GRAMS) {
            topIndex = index
            topFallStart = fallStart
        }
        val fallCut = if (topIndex >= 0 && grams <= FrenchPressWater.OFF_SCALE_GRAMS) topFallStart + 1 else fallStart + 1
        val landingCut = landings.firstOrNull()?.index ?: Int.MAX_VALUE
        val next = min(fallCut, landingCut)
        if (next != cut) {
            cut = next
            cutWater = settledAt(cut - 1)
        }
        waterGrams = cutWater
        return waterGrams
    }

    /** The settled water just before the reading at [index], from the readings within the settled window. */
    private fun settledAt(index: Int): Double? {
        if (index < 0) return null
        val last = readings[index]
        val window = ArrayList<Double>()
        var k = index
        while (k >= 0 && readings[k].atMs >= last.atMs - FrenchPressWater.SETTLED_WINDOW_MS) {
            window += readings[k].reading.weightGrams
            k--
        }
        window.sort()
        return FrenchPressWater.settledLevel(window, last.reading.weightGrams)
    }

    /** A rise still standing: [index] is where it was read and [lowGrams] the level it rose from. */
    private class Landing(val index: Int, val lowGrams: Double) {
        private var runStartMs: Long? = null

        /** True once the weight has been back at the level it rose from for the whole hold: not a landing. */
        var returned = false
            private set

        fun onReading(atMs: Long, grams: Double) {
            if (returned) return
            if (grams > lowGrams + FrenchPressWater.LEFT_TOLERANCE_GRAMS) {
                runStartMs = null
                return
            }
            val start = runStartMs ?: atMs.also { runStartMs = it }
            if (atMs - start >= FrenchPressWater.LEFT_MS) returned = true
        }
    }
}
