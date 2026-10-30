package io.github.mateuxlucax.caparao.data.exporter

import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewSample
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.roundToLong

/**
 * The body [ExporterClient] builds against the shared files in contract/ (the same ones the stub's `go test` posts):
 * a brew equal to each valid example must serialize to that example, and the keys must be the schema's.
 */
class ContractTest {
    // Unit tests run in the app module directory.
    private val contract = File("../contract")

    private fun read(file: File) = JSONObject(file.readText())

    // Compiled against android.jar's org.json, which has keys() but not keySet().
    private fun JSONObject.keyNames(): Set<String> = keys().asSequence().toSet()

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }.toSet()

    /** The brew and curve a contract example describes, as the app stores them. */
    private fun brewOf(json: JSONObject): Pair<Brew, List<BrewSample>> {
        val started = json.getLong("started_at_ms")
        val brew = Brew(
            id = json.getString("id"),
            mode = if (json.getString("mode") == "espresso") BrewMode.ESPRESSO else BrewMode.FILTER,
            startedAt = started,
            durationMs = json.getLong("ended_at_ms") - started,
            doseGrams = json.getDouble("dose_g"),
            yieldGrams = json.getDouble("yield_g"),
            timeToFirstDropMs = if (json.isNull("time_to_first_drop_s")) null else (json.getDouble("time_to_first_drop_s") * 1000).roundToLong(),
            flowAvgGramsPerSecond = json.getDouble("flow_avg_gps"),
            flowPeakGramsPerSecond = json.getDouble("flow_peak_gps"),
            interrupted = json.getBoolean("interrupted"),
            recipeName = json.optString("recipe_name").ifEmpty { null },
            coffee = json.optString("coffee").ifEmpty { null },
            grind = json.optString("grind").ifEmpty { null },
            waterTempCelsius = if (json.has("water_temp_c")) json.getInt("water_temp_c") else null,
            rating = if (json.has("rating")) json.getInt("rating") else null,
            notes = json.optString("notes").ifEmpty { null },
        )
        val samples = json.getJSONArray("samples").let { array ->
            (0 until array.length()).map { array.getJSONObject(it) }
                .map { BrewSample(it.getLong("t_ms"), it.getDouble("weight_g"), it.getDouble("flow_gps")) }
        }
        return brew to samples
    }

    /** Structural equality; numbers by value, so `0`, `0.0` and a BigDecimal `0.0` are equal. */
    private fun assertJsonEquals(path: String, expected: Any?, actual: Any?) {
        when (expected) {
            is JSONObject -> {
                assertTrue("$path: expected an object, got $actual", actual is JSONObject)
                actual as JSONObject
                assertEquals("$path keys", expected.keyNames(), actual.keyNames())
                expected.keyNames().forEach { assertJsonEquals("$path.$it", expected.get(it), actual.get(it)) }
            }
            is JSONArray -> {
                assertTrue("$path: expected an array, got $actual", actual is JSONArray)
                actual as JSONArray
                assertEquals("$path length", expected.length(), actual.length())
                (0 until expected.length()).forEach { assertJsonEquals("$path[$it]", expected.get(it), actual.get(it)) }
            }
            is Number -> {
                assertTrue("$path: expected a number, got $actual", actual is Number)
                assertEquals(path, expected.toDouble(), (actual as Number).toDouble(), 1e-9)
            }
            else -> assertEquals(path, expected, actual)
        }
    }

    @Test
    fun bodiesMatchTheValidExamples() {
        val files = File(contract, "valid").listFiles { f -> f.extension == "json" }!!.sorted()
        assertTrue("no contract files found", files.size >= 4)
        files.forEach { file ->
            val expected = read(file)
            val (brew, samples) = brewOf(expected)
            // Through a string, as on the wire.
            val actual = JSONObject(ExporterClient.body(brew, samples).toString())
            assertJsonEquals(file.name, expected, actual)
        }
    }

    @Test
    fun filterBodyHasExactlyTheSchemaKeys() {
        val schema = read(File(contract, "brew.schema.json"))
        val brew = Brew("a3c1d7e4-8f20-4b6a-b5d9-1e7c3a9f0b24", BrewMode.FILTER, 1_792_935_000_000, 195_000, 15.0, 250.0, null, 1.3, 4.8, false)
        val body = JSONObject(ExporterClient.body(brew, listOf(BrewSample(0, 0.0, 0.0))).toString())

        // Without notes, exactly the required keys: servers that predate the notes still accept it.
        assertEquals(schema.getJSONArray("required").strings(), body.keyNames())

        val noted = brew.copy(recipeName = "V60", coffee = "Caparaó", grind = "22", waterTempCelsius = 94, rating = 4, notes = "Sweet")
        val all = JSONObject(ExporterClient.body(noted, emptyList()).toString())
        assertEquals(schema.getJSONObject("properties").keyNames(), all.keyNames())
        assertTrue("null, not left out", body.isNull("time_to_first_drop_s"))
        val sampleSchema = schema.getJSONObject("properties").getJSONObject("samples").getJSONObject("items")
        assertEquals(sampleSchema.getJSONArray("required").strings(), body.getJSONArray("samples").getJSONObject(0).keyNames())
        assertTrue(body.get("started_at_ms") is Number)
    }

    @Test
    fun valuesTheServerWouldRejectForeverAreClamped() {
        val brew = Brew("5d2b9f60-1c4e-4a8b-8e37-c0a1f6d3b7e5", BrewMode.ESPRESSO, 1_000, 0, 18.0, -0.2, -5, Double.NaN, -1.0, true)
        val body = ExporterClient.body(brew, listOf(BrewSample(0, Double.NaN, Double.POSITIVE_INFINITY)))
        assertEquals(1_001L, body.getLong("ended_at_ms"))
        assertEquals(0.0, body.getDouble("yield_g"), 0.0)
        assertEquals(0.0, body.getDouble("time_to_first_drop_s"), 0.0)
        assertEquals(0.0, body.getDouble("flow_avg_gps"), 0.0)
        assertEquals(0.0, body.getDouble("flow_peak_gps"), 0.0)
        assertEquals(0.0, body.getJSONArray("samples").getJSONObject(0).getDouble("weight_g"), 0.0)
    }
}
