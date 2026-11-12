package dev.mateux.caparao.brew

import dev.mateux.caparao.data.scale.FrameDecoder
import dev.mateux.caparao.data.scale.ScaleMessage
import dev.mateux.caparao.data.scale.ScriptedScaleSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.system.measureNanoTime

/**
 * The live settled water (#322) is the reference [FrenchPressWater.of] at every reading, over every French press
 * session and scripted scenario, and its cost does not grow with the steep.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FrenchPressWaterTrackerTest {
    @Test
    fun everyFrenchPressSessionMatchesTheReferenceAtEveryReading() {
        for (name in listOf("press-spoon-lift-removal", "synthetic-french-press", "synthetic-french-press-early-lift")) {
            assertMatchesReference(session(name), name)
        }
    }

    @Test
    fun everyScriptedScenarioMatchesTheReferenceAtEveryReading() {
        val scenarios = File("src/test/resources/scenarios").listFiles().orEmpty().filter { it.extension == "json" }
        assertTrue("the scenarios are in src/test/resources/scenarios", scenarios.isNotEmpty())
        var checked = 0
        for (file in scenarios) {
            // A scenario that fails before the first weight has no reading to feed.
            val readings = scenario(file.readText())
            if (readings.isEmpty()) continue
            assertMatchesReference(readings, file.name)
            checked++
        }
        assertTrue("the scenarios have readings", checked >= 4)
    }

    @Test
    fun aSpoonThatComesOffIsClearedAndTheNextPourIsWater() {
        val bloom = SyntheticSession()
            .ramp(0.0, 62.0, 4.0)
            .hold(62.0, 2.0)
            .hold(84.0, 0.5)
            .hold(62.0, 2.0)
            .ramp(62.0, 154.0, 4.0)
            .hold(154.0, 4.0)
            .readings
        val tracker = FrenchPressWaterTracker(0L)
        bloom.forEach { tracker.update(it) }
        assertEquals(154.0, tracker.waterGrams!!, 0.3)
    }

    /** The longest brew the ViewModel keeps is 20 minutes at 10 Hz; the tracker's cost must not grow with it. */
    @Test
    fun theCostOfAReadingDoesNotGrowWithTheSteep() {
        val readings = SyntheticSession().ramp(0.0, 154.0, 8.0).hold(154.0, 20 * 60.0).readings
        val head = readings.take(2_000)
        val tail = readings.takeLast(2_000)
        val beforeTail = readings.subList(0, readings.size - tail.size)

        fun nanosOf(prefix: List<TimedReading>, block: List<TimedReading>): Long = (1..3).minOf {
            val tracker = FrenchPressWaterTracker(0L)
            prefix.forEach(tracker::update)
            measureNanoTime { block.forEach(tracker::update) }
        }
        nanosOf(head, head) // warm the JIT on the same path
        val headNanos = nanosOf(emptyList(), head)
        val tailNanos = nanosOf(beforeTail, tail)
        assertTrue(
            "the last 2,000 readings of a 20 minute steep took $tailNanos ns, against $headNanos ns for the first 2,000",
            tailNanos <= headNanos * 3 + 1_000_000,
        )
    }

    /** Reads a recorded session exactly as it arrived; one line may hold several frames, all at the line's time. */
    private fun session(name: String): List<TimedReading> {
        val decoder = FrameDecoder()
        return File("src/test/resources/sessions/$name.jsonl").readLines().drop(1).map(::JSONObject)
            .filter { it.getString("dir") == "rx" }
            .flatMap { line ->
                val atMs = line.getLong("t_ms")
                decoder.feed(line.getString("hex").split(' ').map { it.toInt(16).toByte() }.toByteArray())
                    .filterIsInstance<ScaleMessage.Weight>()
                    .map { TimedReading(atMs, it.reading) }
            }
    }

    /** Plays a scripted scenario at the 10 Hz the scale sends, taking the reading on the scale at each tick. */
    private fun scenario(json: String): List<TimedReading> {
        val source = ScriptedScaleSource(
            CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
            json,
            autoRun = false,
            monotonicMs = { 0L },
        )
        source.advanceTo(0)
        val readings = mutableListOf<TimedReading>()
        source.reading.value?.let { readings += TimedReading(0L, it) }
        while (source.position.getInt("event") < source.position.getInt("total")) {
            source.advance(SyntheticSession.STEP_MS)
            source.reading.value?.let { readings += TimedReading(source.nowMs, it) }
        }
        return readings
    }

    private fun assertMatchesReference(readings: List<TimedReading>, name: String) {
        assertTrue("$name has readings", readings.isNotEmpty())
        // From the first reading and from the first water: the brew itself ignores the readings before it.
        val starts = listOfNotNull(readings.first().atMs, readings.firstOrNull { it.reading.weightGrams > 1.0 }?.atMs)
        for (startedAtMs in starts.distinct()) assertMatchesReference(readings, startedAtMs, name)
    }

    private fun assertMatchesReference(readings: List<TimedReading>, startedAtMs: Long, name: String) {
        val tracker = FrenchPressWaterTracker(startedAtMs)
        readings.forEachIndexed { index, reading ->
            tracker.update(reading)
            val expected = FrenchPressWater.of(readings.subList(0, index + 1), startedAtMs, reading.atMs)
            val at = "$name: reading $index at ${reading.atMs} ms, from $startedAtMs"
            if (expected == null) assertNull(at, tracker.waterGrams) else assertEquals(at, expected, tracker.waterGrams!!, 0.0)
        }
    }
}
