package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.ScaleReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewSummaryTest {
    /** 20 Hz: 2 s idle at 0 g, then 0 → 20 g over 10 s from t = 2000, then 2 s idle at 20 g. */
    private val readings = (0..280).map { k ->
        val at = k * 50L
        val grams = ((at - 2_000).coerceIn(0, 10_000) / 50L) / 10.0 // 2 g/s: 0.1 g per 50 ms
        TimedReading(at, ScaleReading(grams, 99.9, 0, false))
    }
    private val result = BrewResult(startedAtMs = 2_000, endedAtMs = 12_000, yieldGrams = 20.0, taredAtMs = 500, interrupted = false)

    @Test
    fun numbersForEspresso() {
        val summary = BrewSummary.of(BrewMode.ESPRESSO, readings, result)
        assertEquals(10_000L, summary.durationMs)
        assertEquals(20.0, summary.yieldGrams, 0.0)
        assertEquals(1_500L, summary.timeToFirstDropMs)
        assertEquals(2.0, summary.flowAvgGramsPerSecond, 1e-9)
        assertEquals(2.0, summary.flowPeakGramsPerSecond, 0.1)
    }

    @Test
    fun filterHasNoTimeToFirstDrop() {
        assertNull(BrewSummary.of(BrewMode.FILTER, readings, result).timeToFirstDropMs)
    }

    @Test
    fun espressoWithoutTareTimeHasNoTimeToFirstDrop() {
        assertNull(BrewSummary.of(BrewMode.ESPRESSO, readings, result.copy(taredAtMs = null)).timeToFirstDropMs)
    }

    @Test
    fun curveIsDownsampledToTenHzFromStartToEndInclusive() {
        val curve = BrewSummary.of(BrewMode.ESPRESSO, readings, result).curve
        assertEquals(0L, curve.first().atMs)
        assertEquals(10_000L, curve.last().atMs)
        assertEquals(101, curve.size)
        curve.zipWithNext().forEach { (a, b) -> assertTrue(b.atMs - a.atMs >= 100) }
        assertEquals(20.0, curve.last().weightGrams, 0.0)
        // Smoothed flow, not the scale's saturated 99.9.
        assertTrue(curve.all { it.flowGramsPerSecond in 0.0..2.2 })
    }

    @Test
    fun endIsKeptWhenItFallsBetweenSamples() {
        val curve = BrewSummary.of(BrewMode.FILTER, readings, result.copy(endedAtMs = 12_050)).curve
        assertEquals(10_050L, curve.last().atMs)
        curve.zipWithNext().forEach { (a, b) -> assertTrue(b.atMs - a.atMs >= 100) }
    }

    @Test
    fun toBrewCarriesTheNumbers() {
        val brew = BrewSummary.of(BrewMode.ESPRESSO, readings, result)
            .toBrew("id", BrewMode.ESPRESSO, startedAtEpochMs = 1_700_000_000_000, doseGrams = 10.0, interrupted = false)
        assertEquals(2.0, brew.ratio!!, 1e-9)
        assertEquals(10_000L, brew.durationMs)
        assertEquals(1_500L, brew.timeToFirstDropMs)
    }
}
