package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.data.scale.FrameDecoder
import io.github.mateuxlucax.caparao.data.scale.ScaleMessage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Replays a real filter brew from the app's arm event through Done. */
class RealFilterLiftStirTest {
    @Test
    fun sustainedPourAfterLiftAndStirIsSaved() {
        val detector = FilterDetector()
        val decoder = FrameDecoder()
        var armed = false
        var brewing = false
        var stopped = false

        File("src/test/resources/sessions/real-filter-lift-stir.jsonl").readLines().drop(1).forEach { line ->
            val row = JSONObject(line)
            when {
                row.getString("dir") == "event" && row.optString("note") == "brew_armed" -> armed = true
                row.getString("dir") == "rx" && armed && !stopped -> {
                    val atMs = row.getLong("t_ms")
                    val bytes = row.getString("hex").split(' ').map { it.toInt(16).toByte() }.toByteArray()
                    decoder.feed(bytes).filterIsInstance<ScaleMessage.Weight>().forEach { message ->
                        detector.onReading(TimedReading(atMs, message.reading))
                        brewing = brewing || detector.phase == BrewPhase.BREWING
                        if (brewing) assertEquals(
                            "brew stays active until Done at ${atMs}ms after ${message.reading.weightGrams} g; " +
                                "saved ${detector.result?.yieldGrams} g",
                            BrewPhase.BREWING,
                            detector.phase,
                        )
                    }
                }

                row.optString("note") == "brew_stop_requested" -> {
                    detector.stop(row.getLong("t_ms"))
                    stopped = true
                }
            }
        }

        assertTrue("capture reaches the Done event", stopped)
        assertNotNull(detector.result)
        assertEquals(320.0, detector.result!!.yieldGrams, 1.0)
    }
}
