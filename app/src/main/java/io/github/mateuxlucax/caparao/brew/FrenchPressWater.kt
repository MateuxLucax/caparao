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
    /** A rise this large within [LANDING_WINDOW_MS] is set on the scale, not poured: pours here run at 25 g/s or less. */
    const val LANDING_GRAMS = 40.0
    const val LANDING_WINDOW_MS = 1_000L

    /** At or below this the press is off the scale; a lift that comes back is not the end of the brew. */
    const val OFF_SCALE_GRAMS = 5.0

    /** Pour overshoot and scale noise: readings within this of each other are one level. */
    const val SETTLED_TOLERANCE_GRAMS = 1.0
    const val SETTLED_WINDOW_MS = 1_000L

    /**
     * The water in [readings] from [startedAtMs] to [endedAtMs], or null when nothing was read on the scale. The brew is
     * cut at the first landing and at the final removal; the water is the settled level just before the cut.
     */
    fun of(readings: List<TimedReading>, startedAtMs: Long, endedAtMs: Long): Double? {
        val brew = readings.filter { it.atMs in startedAtMs..endedAtMs }
        var cut = brew.size
        if (brew.lastOrNull()?.grams()?.let { it <= OFF_SCALE_GRAMS } == true) {
            // The removal falls from the last level on the scale: step back over the fall to that level.
            var last = brew.indexOfLast { it.grams() > OFF_SCALE_GRAMS }
            while (last > 0 && brew[last - 1].grams() > brew[last].grams() + SETTLED_TOLERANCE_GRAMS) last--
            cut = last + 1
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

    /** The index of the first reading that rises [LANDING_GRAMS] within [LANDING_WINDOW_MS] from weight on the scale. */
    private fun landing(brew: List<TimedReading>): Int? {
        for (i in brew.indices) {
            val at = brew[i].atMs
            var low = Double.POSITIVE_INFINITY
            var j = i - 1
            while (j >= 0 && at - brew[j].atMs <= LANDING_WINDOW_MS) {
                low = minOf(low, brew[j].grams())
                j--
            }
            // A low reading is the press coming back after a lift, not something set on the scale.
            if (low > OFF_SCALE_GRAMS && brew[i].grams() - low >= LANDING_GRAMS) return i
        }
        return null
    }

    private fun TimedReading.grams() = reading.weightGrams
}
