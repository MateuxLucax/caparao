package io.github.mateuxlucax.caparao.data.scale

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Feeds sessions recorded from a real Dot (src/test/resources/sessions) through [FrameDecoder], notification by
 * notification, exactly as they arrived.
 */
class RecordedSessionTest {
    private fun decode(name: String): List<ScaleMessage> {
        // Unit tests run in the module directory.
        val lines = File("src/test/resources/sessions/$name.jsonl").readLines().drop(1).map(::JSONObject)
        val decoder = FrameDecoder()
        return lines.filter { it.getString("dir") == "rx" }.flatMap { line ->
            decoder.feed(line.getString("hex").split(' ').map { it.toInt(16).toByte() }.toByteArray())
        }
    }

    @Test
    fun everyFrameOfARealSessionIsUnderstood() {
        for (name in listOf("press-ramp", "idle-5min")) {
            val unknown = decode(name).filterIsInstance<ScaleMessage.Unknown>()
            assertEquals("$name: frames the decoder did not understand", emptyList<ScaleMessage>(), unknown)
        }
    }

    @Test
    fun pressRampCoversTheWeightRangeFlowSaturationAndOverload() {
        val messages = decode("press-ramp")
        val readings = messages.filterIsInstance<ScaleMessage.Weight>().map { it.reading }
        assertEquals(1300.1, readings.maxOf { it.weightGrams }, 0.0)
        assertEquals(0.0, readings.last().weightGrams, 0.0)
        assertEquals("flow saturates at 99.9 g/s", 99.9, readings.maxOf { it.flowGramsPerSecond }, 0.0)
        assertTrue("flow never went negative", readings.all { it.flowGramsPerSecond >= 0.0 })
        assertTrue("the overload flag turned on", readings.any { it.overload })
        // The reply to the battery read plus two unsolicited pushes, all 3 bars and 100 %.
        val batteries = messages.filterIsInstance<ScaleMessage.Battery>().map { it.battery }
        assertEquals(3, batteries.size)
        assertTrue(batteries.all { it.percent == 100 && it.bars == 3 })
        assertEquals(listOf(DotProtocol.CMD_UNIT, DotProtocol.CMD_MODE), messages.filterIsInstance<ScaleMessage.Ack>().map { it.cmd })
    }

    @Test
    fun idleSessionStreamsTenReadingsPerSecond() {
        val readings = decode("idle-5min").filterIsInstance<ScaleMessage.Weight>().map { it.reading }
        // 5 min 42 s connected at ~10 Hz.
        assertTrue("got ${readings.size} readings", readings.size in 3300..3500)
        assertTrue(readings.all { it.weightGrams == 0.0 && it.flowGramsPerSecond == 0.0 && it.timerSeconds == 0 })
    }
}
