package dev.mateux.caparao.brew

import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.ScaleReading
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays the weight/time curves of user-reviewed Prometheus brews (#79) after explicit synthetic detector setup.
 *
 * These are partial regression curves, not BLE recordings: the fixture starts at the first saved water/drop sample and
 * has no cup placement, tare or pre-start readings. Each test therefore initializes an armed/tared detector before
 * feeding the untouched curve; that setup is test initialization and does not claim to validate cup, tare or start
 * detection. Prometheus summaries are detector outputs, so assertions only cover sample processing and internal
 * consistency, never the accuracy of an observed start/end or yield.
 */
class PrometheusCurveRegressionTest {
    private data class Provenance(
        val brewId: String,
        val points: Int,
        val mode: String,
        val classification: String,
    )

    private data class Curve(
        val mode: String,
        val points: List<Pair<Long, Double>>,
    ) {
        val peakGrams = points.maxOf { it.second }
    }

    private data class Replay(
        val curve: Curve,
        val result: BrewResult,
        val summary: BrewSummary,
        val lastReadingAtMs: Long,
    )

    /** The reviewed cases selected in #122, with the metadata embedded in each public fixture. */
    private val reviewed = mapOf(
        "p9-filter" to Provenance(
            brewId = "70cbedf1-3111-4fa9-9aeb-6c0ffe37613d",
            points = 1277,
            mode = "filter",
            classification = "user_confirmed_normal",
        ),
        "p11-filter-three-pour" to Provenance(
            brewId = "88409fd2-ccdc-40a7-8f29-3190c3490f64",
            points = 1297,
            mode = "filter",
            classification = "user_confirmed_normal",
        ),
        "p13-espresso-preinfusion" to Provenance(
            brewId = "20f0b49b-9f21-47fb-b30a-631b96dd04d7",
            points = 185,
            mode = "espresso",
            classification = "user_confirmed_normal_preinfusion",
        ),
        "p14-filter-five-pour" to Provenance(
            brewId = "cee72a90-f1ae-444a-81e4-fb1db7283f76",
            points = 1444,
            mode = "filter",
            classification = "multi_pour_context",
        ),
        "p16-filter-handling" to Provenance(
            brewId = "df1349fb-f2c2-45cf-81b5-03dcf40079c6",
            points = 1384,
            mode = "filter",
            classification = "handling_and_known_bad_summary",
        ),
        "p18-espresso" to Provenance(
            brewId = "fc32ade3-61c8-4712-ae00-72c6b5afb7a2",
            points = 197,
            mode = "espresso",
            classification = "user_confirmed_normal",
        ),
    )

    private fun curve(case: String): Curve {
        val json = JSONObject(javaClass.getResource("/sessions/$case.json")!!.readText())
        val expected = requireNotNull(reviewed[case]) { "unknown reviewed case $case" }
        assertEquals("Prometheus partial regression curve", json.getString("source"))
        assertEquals(expected.brewId, json.getString("source_brew_id"))
        assertEquals(expected.mode, json.getString("mode"))
        assertEquals(expected.classification, json.getString("user_classification"))
        val firstSampleKind = if (expected.mode == "espresso") "first_drop" else "first_water"
        assertEquals(firstSampleKind, json.getString("curve_starts_at"))

        val samples = json.getJSONArray("samples_ms_g")
        assertEquals("original sampling count", expected.points, json.getInt("original_point_count"))
        assertEquals("no resampled or thinned points", expected.points, samples.length())
        val points = (0 until samples.length()).map { i ->
            val point = samples.getJSONObject(i)
            point.getLong("t") to point.getDouble("g")
        }
        assertEquals(0L, points.first().first)
        assertTrue("timestamps increase", points.zipWithNext().all { (a, b) -> b.first > a.first })
        assertTrue("weights are finite", points.all { it.second.isFinite() })
        return Curve(expected.mode, points)
    }

    private fun reading(atMs: Long, grams: Double) = TimedReading(atMs, ScaleReading(grams, 0.0, 0, false))

    /**
     * Synthetic harness state only: an espresso detector armed past cup detection and confirmed tared. The retained
     * curve begins at the first drop, so this is the minimum state needed to feed it.
     */
    private fun armedEspresso(): EspressoDetector {
        val detector = EspressoDetector()
        detector.armNow(0)
        detector.onReading(reading(0, 0.0))
        detector.onReading(reading(HARNESS_ZERO_HOLD_MS, 0.0))
        assertEquals(BrewPhase.READY, detector.phase)
        return detector
    }

    /**
     * Synthetic harness state only: zero readings complete the filter detector's tare, so the retained curve begins
     * from a tared, READY scale. Cup/handling before the saved interval is not replayed.
     */
    private fun taredFilter(): FilterDetector {
        val detector = FilterDetector()
        var atMs = 0L
        while (atMs <= HARNESS_ZERO_HOLD_MS) {
            detector.onReading(reading(atMs, 0.0))
            atMs += HARNESS_STEP_MS
        }
        assertEquals(BrewPhase.READY, detector.phase)
        return detector
    }

    /** Feeds every retained point, in order, then stops at the saved end; the fixture itself is never modified. */
    private fun replay(case: String): Replay {
        val curve = curve(case)
        val detector: BrewDetector = if (curve.mode == "espresso") armedEspresso() else taredFilter()
        val readings = curve.points.map { (t, grams) -> reading(HARNESS_MS + t, grams) }
        readings.forEach { detector.onReading(it) }

        val expectedPhase = if (case == "p16-filter-handling") BrewPhase.PAUSED else BrewPhase.BREWING
        assertEquals("reviewed curve stays active or pauses at its removal", expectedPhase, detector.phase)
        detector.stop(readings.last().atMs)
        val result = requireNotNull(detector.result)
        val mode = if (curve.mode == "espresso") BrewMode.ESPRESSO else BrewMode.FILTER
        val summary = BrewSummary.of(mode, readings, result)

        assertEquals(HARNESS_MS, result.startedAtMs)
        assertFalse(result.interrupted)
        assertNotNull("the synthetic tare must be known", result.taredAtMs)
        assertTrue(result.taredAtMs!! < result.startedAtMs)
        assertTrue(result.endedAtMs >= result.startedAtMs)
        assertEquals(result.endedAtMs - result.startedAtMs, summary.durationMs)
        assertEquals(result.yieldGrams, summary.yieldGrams, 0.0)
        assertEquals(result.yieldGrams / (summary.durationMs / 1000.0), summary.flowAvgGramsPerSecond, 1e-9)
        assertTrue(summary.curve.isNotEmpty())
        return Replay(curve, result, summary, readings.last().atMs)
    }

    /** Every retained sample must survive into the saved curve; the Prometheus cadence is slower than 10 Hz. */
    private fun assertSummaryKeepsEverySample(replay: Replay) {
        val points = replay.curve.points
        assertEquals(points.size, replay.summary.curve.size)
        points.forEachIndexed { i, (t, grams) ->
            assertEquals(t, replay.summary.curve[i].atMs)
            assertEquals(grams, replay.summary.curve[i].weightGrams, 0.0)
        }
    }

    @Test
    fun reviewedFixturesKeepEveryOriginalSampleAndProvenance() {
        // The original defect thinned these to one point per second; all six files must keep every saved sample.
        for (case in reviewed.keys) {
            val curve = curve(case)
            assertEquals(reviewed.getValue(case).points, curve.points.size)
            assertTrue(curve.points.size > 100)
            // The reviewed cadence is about 0.15 s; a 1 s resample would break this too.
            val gaps = curve.points.zipWithNext { a, b -> b.first - a.first }.sorted()
            val medianGap = gaps[gaps.size / 2]
            assertTrue("$case keeps the original ~150 ms sampling, median ${medianGap}ms", medianGap < 250)
        }
    }

    @Test
    fun confirmedEspressoCurvesStayBrewingThroughThePreinfusionRampAndKeepEverySample() {
        for (case in listOf("p13-espresso-preinfusion", "p18-espresso")) {
            val replay = replay(case)
            // Stopped at the last saved sample: the normal curves never end themselves.
            assertEquals(replay.lastReadingAtMs, replay.result.endedAtMs)
            assertEquals(replay.curve.peakGrams, replay.result.yieldGrams, 0.01)
            assertSummaryKeepsEverySample(replay)
        }

        // P13's slow initial rise is legitimate preinfusion: it stays in the curve and does not end the shot early.
        val p13 = replay("p13-espresso-preinfusion")
        assertEquals(0.3, p13.summary.curve.first().weightGrams, 0.0)
        assertTrue(p13.summary.curve.take(20).last().weightGrams < 6.0)
        assertTrue("shot ran the full reviewed interval", p13.result.endedAtMs - p13.result.startedAtMs > 25_000)
    }

    @Test
    fun confirmedFilterCurvesStayBrewingThroughMultiPourWaitsAndKeepEverySample() {
        for (case in listOf("p9-filter", "p11-filter-three-pour", "p14-filter-five-pour")) {
            val replay = replay(case)
            // A pour pause is not an end: the detector stops only where the fixture's saved interval stops, and the
            // server is still on the scale, so the last stable weight is the poured water.
            assertEquals(replay.lastReadingAtMs, replay.result.endedAtMs)
            assertEquals(replay.curve.points.last().second, replay.result.yieldGrams, 0.01)
            assertSummaryKeepsEverySample(replay)
        }

        // P11 and P14 keep long pour waits in the input without treating them as a lift/end.
        for (case in listOf("p11-filter-three-pour", "p14-filter-five-pour")) {
            val replay = replay(case)
            val longest = longestQuietWindowMs(replay.curve.points)
            assertTrue("$case keeps a multi-pour wait (longest ${longest}ms)", longest >= 5_000)
        }
    }

    @Test
    fun p16RemovalTailDoesNotBecomeTheSavedYield() {
        val replay = replay("p16-filter-handling")
        val points = replay.curve.points
        val firstNegative = points.indexOfFirst { it.second < 0.0 }
        assertTrue("P16 contains the removed-filter tail", firstNegative > 0)
        val beforeRemoval = points[firstNegative - 1]

        // The brief 267.2 g handling peak returns to the 255.5 g water level before the filter is lifted.
        assertEquals(255.5, replay.result.yieldGrams, 0.3)
        assertTrue(replay.result.yieldGrams >= beforeRemoval.second)
        assertEquals("brew ends when the weight falls away", HARNESS_MS + beforeRemoval.first, replay.result.endedAtMs)
        assertTrue("saved end excludes the negative tail", replay.result.endedAtMs < HARNESS_MS + points[firstNegative].first)
        assertEquals(firstNegative, replay.summary.curve.size)
        assertTrue(replay.summary.curve.none { it.weightGrams < 0.0 })
        assertEquals(beforeRemoval.second, replay.summary.curve.last().weightGrams, 0.0)
    }

    /** Longest stretch of the retained curve whose weight stays within 1 g, i.e. a pour wait or the drawdown. */
    private fun longestQuietWindowMs(points: List<Pair<Long, Double>>): Long {
        var longest = 0L
        for (start in points.indices) {
            var low = points[start].second
            var high = low
            var end = start
            while (end + 1 < points.size) {
                val grams = points[end + 1].second
                if (maxOf(high, grams) - minOf(low, grams) > 1.0) break
                low = minOf(low, grams)
                high = maxOf(high, grams)
                end++
            }
            longest = maxOf(longest, points[end].first - points[start].first)
        }
        return longest
    }

    private companion object {
        /** Clock offset of the first retained sample, clear of the synthetic harness readings. */
        const val HARNESS_MS = 10_000L
        const val HARNESS_ZERO_HOLD_MS = 600L
        const val HARNESS_STEP_MS = 100L
    }
}
