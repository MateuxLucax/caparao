package io.github.mateuxlucax.caparao.brew

import kotlin.math.abs

/**
 * The water a French press saves (#214): the settled weight just before the press comes off the scale for good, or
 * before something heavy is set on it (a lid). A spoon in the bloom comes off again and a lift comes back, so neither
 * counts; the highest weight read does not count either, since a landing or a pour overshoot passes through it.
 *
 * Pure: the readings of a brew in order, from its first water to its end.
 */
object FrenchPressWater {
    /**
     * A rise this large within [LANDING_WINDOW_MS] is set on the scale, not poured: pours here reach about 12 g in half a
     * second, a spoon about 22 g in under a third of one.
     */
    private const val LANDING_GRAMS = 18.0
    private const val LANDING_WINDOW_MS = 500L

    /** Something set on the scale stays until the weight is back at its level for [LEFT_MS]; a spoon that comes off does. */
    private const val LEFT_TOLERANCE_GRAMS = 2.0
    private const val LEFT_MS = 1_000L

    /** The same threshold that ends a French press brew on removal: at or below it the press is off the scale. */
    private const val OFF_SCALE_GRAMS = FrenchPressFinishTracker.REMOVAL_THRESHOLD_GRAMS

    /** Pour overshoot and scale noise, and how long a level must hold: the filter detector's settled-water rule (#201). */
    private val settled = FilterConfig()
    private val SETTLED_TOLERANCE_GRAMS = settled.settledWeightToleranceGrams
    private val SETTLED_WINDOW_MS = settled.settledWeightHoldMs

    /**
     * The water in [readings] from [startedAtMs] to [endedAtMs], or null when nothing was read on the scale. The brew is
     * cut at the first landing and at the final removal; the water is the settled level just before the cut.
     */
    fun of(readings: List<TimedReading>, startedAtMs: Long, endedAtMs: Long): Double? {
        val brew = readings.filter { it.atMs in startedAtMs..endedAtMs }
        var cut = brew.size
        // A fall in progress or a removal at the end: step back over the fall to the level before it.
        var top = brew.indexOfLast { it.grams() > OFF_SCALE_GRAMS }
        if (top >= 0 && brew.last().grams() <= OFF_SCALE_GRAMS) {
            while (top > 0 && brew[top - 1].grams() > brew[top].grams() + SETTLED_TOLERANCE_GRAMS) top--
            cut = top + 1
        } else if (brew.size >= 2) {
            var k = brew.size - 1
            while (k > 0 && brew[k - 1].grams() > brew[k].grams() + SETTLED_TOLERANCE_GRAMS) k--
            cut = k + 1
        }
        landing(brew)?.let { cut = minOf(cut, it) }
        if (cut <= 0) return null
        val before = brew.subList(0, cut)
        val last = before.last()
        val window = before.filter { it.atMs >= last.atMs - SETTLED_WINDOW_MS }.map { it.grams() }.sorted()
        val median = window[window.size / 2]
        val settled = window.size >= 3 && window.all { abs(it - median) <= SETTLED_TOLERANCE_GRAMS }
        return if (settled) median else last.grams()
    }

    /**
     * The index of the first reading that rises [LANDING_GRAMS] within [LANDING_WINDOW_MS] from weight on the scale and is
     * still there: the weight has not gone back to that level for [LEFT_MS], and the last reading is above it.
     */
    private fun landing(brew: List<TimedReading>): Int? {
        val last = brew.lastOrNull() ?: return null
        for (i in brew.indices) {
            val at = brew[i].atMs
            var low = Double.POSITIVE_INFINITY
            var j = i - 1
            while (j >= 0 && at - brew[j].atMs <= LANDING_WINDOW_MS) {
                low = minOf(low, brew[j].grams())
                j--
            }
            // A low reading is the press coming back after a lift, not something set on the scale.
            if (low <= OFF_SCALE_GRAMS || brew[i].grams() - low < LANDING_GRAMS) continue
            if (!leftAfter(brew, i, low)) return i
        }
        return null
    }

    /**
     * True once the weight has been back at [low] for [LEFT_MS] after [index]. Off the scale does not count: a removal
     * at the end takes the lid with it.
     */
    private fun leftAfter(brew: List<TimedReading>, index: Int, low: Double): Boolean {
        var runStart: Long? = null
        for (k in index + 1 until brew.size) {
            val r = brew[k]
            if (r.grams() <= OFF_SCALE_GRAMS) continue
            if (r.grams() <= low + LEFT_TOLERANCE_GRAMS) {
                val start = runStart ?: r.atMs.also { runStart = it }
                if (r.atMs - start >= LEFT_MS) return true
            } else {
                runStart = null
            }
        }
        return false
    }

    private fun TimedReading.grams() = reading.weightGrams
}
