package dev.mateux.caparao.brew

import dev.mateux.caparao.data.scale.FrameDecoder
import dev.mateux.caparao.data.scale.ScaleMessage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Replays a real filter brew from the app's arm event through Done. */
class RealFilterLiftStirTest {
    private data class Capture(val readings: List<TimedReading>, val stoppedAtMs: Long)

    private fun capture(): Capture {
        val decoder = FrameDecoder()
        val readings = mutableListOf<TimedReading>()
        var armed = false
        var stoppedAtMs: Long? = null

        File("src/test/resources/sessions/real-filter-lift-stir.jsonl").readLines().drop(1).forEach { line ->
            val row = JSONObject(line)
            when {
                row.getString("dir") == "event" && row.optString("note") == "brew_armed" -> armed = true
                row.optString("note") == "brew_stop_requested" -> stoppedAtMs = row.getLong("t_ms")
                row.getString("dir") == "rx" && armed && stoppedAtMs == null -> {
                    val atMs = row.getLong("t_ms")
                    val bytes = row.getString("hex").split(' ').map { it.toInt(16).toByte() }.toByteArray()
                    decoder.feed(bytes).filterIsInstance<ScaleMessage.Weight>().forEach { message ->
                        readings += TimedReading(atMs, message.reading)
                    }
                }
            }
        }

        return Capture(readings, requireNotNull(stoppedAtMs))
    }

    @Test
    fun sustainedPourAfterLiftAndStirIsSaved() {
        val capture = capture()
        val detector = FilterDetector()
        val pauses = mutableListOf<BrewPauseState>()
        val resumes = mutableListOf<Long>()
        var brewStartedAtMs: Long? = null
        var returnSample: TimedReading? = null

        capture.readings.forEach { reading ->
            val before = detector.phase
            detector.onReading(reading)
            val after = detector.phase
            if (brewStartedAtMs == null && after == BrewPhase.BREWING) brewStartedAtMs = detector.startedAtMs
            if (before != BrewPhase.PAUSED && after == BrewPhase.PAUSED) pauses += requireNotNull(detector.pauseState)
            if (before == BrewPhase.PAUSED && after == BrewPhase.BREWING) resumes += reading.atMs
            val firstPauseAtMs = pauses.firstOrNull()?.startedAtMs
            if (returnSample == null && firstPauseAtMs != null && reading.atMs > firstPauseAtMs &&
                abs(reading.reading.weightGrams - 109.1) < 0.05
            ) {
                returnSample = reading
            }
            if (brewStartedAtMs != null) {
                assertTrue(
                    "the brew remains active through the lift and stir at ${reading.atMs}ms",
                    after == BrewPhase.BREWING || after == BrewPhase.PAUSED,
                )
            }
        }

        detector.stop(capture.stoppedAtMs)

        val startedAtMs = requireNotNull(brewStartedAtMs)
        assertEquals("one lift pauses once", 1, pauses.size)
        assertEquals("the dripper resumes once", 1, resumes.size)
        assertEquals(41_700.0, (pauses.single().startedAtMs - startedAtMs).toDouble(), 500.0)
        assertEquals(47_500.0, (resumes.single() - startedAtMs).toDouble(), 500.0)
        assertEquals(224_000.0, (detector.result!!.endedAtMs - detector.result!!.startedAtMs).toDouble(), 1_000.0)
        assertTrue(
            "the stir from 92 to 106 s does not pause the brew",
            pauses.none { (it.startedAtMs - startedAtMs) in 92_000L..106_000L },
        )
        assertEquals(320.0, detector.result!!.yieldGrams, 1.0)

        val returned = requireNotNull(returnSample) { "the 109.1 g reading just after the return is in the capture" }
        assertTrue(returned.atMs - startedAtMs in 46_000L..48_500L)

        // Stopping on the return sample must still use the settled water at the lift, not the transient 109.1 g.
        val partial = FilterDetector()
        capture.readings.takeWhile { it.atMs <= returned.atMs }.forEach(partial::onReading)
        partial.stop(returned.atMs)
        assertEquals(pauses.single().waterWeightGrams, partial.result!!.yieldGrams, 0.3)
    }
}
