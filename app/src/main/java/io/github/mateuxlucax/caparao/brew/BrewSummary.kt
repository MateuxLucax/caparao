package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewSample

/** The numbers for a [Brew], computed from the readings of a finished brew. Pure. */
data class BrewSummary(
    val durationMs: Long,
    val yieldGrams: Double,
    /** Espresso only, from tare to first drop; null for filter or when the tare time is unknown. */
    val timeToFirstDropMs: Long?,
    /** Yield over duration. */
    val flowAvgGramsPerSecond: Double,
    /** Highest [FlowSmoother] value during the brew. */
    val flowPeakGramsPerSecond: Double,
    /** Start to end inclusive, at most [MAX_SAMPLE_HZ], [BrewSample.atMs] relative to the start. */
    val curve: List<BrewSample>,
) {
    /** The [Brew] to save; [startedAtEpochMs] is the wall-clock time of [BrewResult.startedAtMs]. */
    fun toBrew(id: String, mode: BrewMode, startedAtEpochMs: Long, doseGrams: Double?, interrupted: Boolean) = Brew(
        id = id,
        mode = mode,
        startedAt = startedAtEpochMs,
        durationMs = durationMs,
        doseGrams = doseGrams,
        yieldGrams = yieldGrams,
        timeToFirstDropMs = timeToFirstDropMs,
        flowAvgGramsPerSecond = flowAvgGramsPerSecond,
        flowPeakGramsPerSecond = flowPeakGramsPerSecond,
        interrupted = interrupted,
    )

    companion object {
        const val MAX_SAMPLE_HZ = 10
        private const val MIN_SAMPLE_GAP_MS = 1_000L / MAX_SAMPLE_HZ

        /** Builds the summary from [readings] (in order; those outside the brew are ignored) and the detector's [result]. */
        fun of(mode: BrewMode, readings: List<TimedReading>, result: BrewResult): BrewSummary {
            val start = result.startedAtMs
            val end = result.endedAtMs
            val durationMs = end - start
            val smoother = FlowSmoother()
            var peak = 0.0
            val curve = ArrayList<BrewSample>()
            val lastInBrew = readings.lastOrNull { it.atMs in start..end }
            for (r in readings) {
                if (r.atMs < start || r.atMs > end) continue
                val flow = smoother.add(r)
                peak = maxOf(peak, flow)
                val sample = BrewSample(r.atMs - start, r.reading.weightGrams, flow)
                val last = curve.lastOrNull()
                when {
                    last == null || sample.atMs - last.atMs >= MIN_SAMPLE_GAP_MS -> curve.add(sample)
                    // Too close to the previous sample, but the end must be in: it takes the previous one's place.
                    r === lastInBrew && curve.size > 1 -> curve[curve.lastIndex] = sample
                }
            }
            return BrewSummary(
                durationMs = durationMs,
                yieldGrams = result.yieldGrams,
                timeToFirstDropMs = if (mode == BrewMode.ESPRESSO) result.taredAtMs?.let { start - it } else null,
                flowAvgGramsPerSecond = if (durationMs > 0) result.yieldGrams / (durationMs / 1000.0) else 0.0,
                flowPeakGramsPerSecond = peak,
                curve = curve,
            )
        }
    }
}
