package io.github.mateuxlucax.caparao.data.export

import io.github.mateuxlucax.caparao.data.exporter.ExporterClient
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewSample
import io.github.mateuxlucax.caparao.model.BrewWithSamples
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class BrewExporterTest {
    private val brew = Brew(
        id = "0f8e1c2a-5b7d-4c3e-9a10-2f6d8b4e7c91",
        mode = BrewMode.ESPRESSO,
        startedAt = 1_792_931_400_000,
        durationMs = 28_600,
        doseGrams = 18.0,
        yieldGrams = 36.4,
        timeToFirstDropMs = 6_200,
        flowAvgGramsPerSecond = 1.3,
        flowPeakGramsPerSecond = 2.1,
        interrupted = false,
    )
    private val samples = listOf(
        BrewSample(0, 0.0, 0.0),
        BrewSample(15_000, 17.9, 2.1),
        BrewSample(28_600, 36.4, 1.1),
    )

    @Test
    fun jsonIsAnArrayOfContractBodies() {
        val exported = JSONArray(BrewExporter.json(listOf(BrewWithSamples(brew, samples))))

        assertEquals(1, exported.length())
        val element = exported.getJSONObject(0)
        val expected = ExporterClient.body(brew, samples)
        assertEquals(expected.keys().asSequence().toSet(), element.keys().asSequence().toSet())
        assertEquals(brew.id, element.getString("id"))
        assertEquals("espresso", element.getString("mode"))
        assertEquals(brew.startedAt, element.getLong("started_at_ms"))
        assertEquals(3, element.getJSONArray("samples").length())
        assertEquals(15_000, element.getJSONArray("samples").getJSONObject(1).getLong("t_ms"))
    }

    @Test
    fun emptyHistoryIsAnEmptyArray() {
        assertEquals(0, JSONArray(BrewExporter.json(emptyList())).length())
    }

    @Test
    fun aBrewWithoutSamplesStillExports() {
        val element = JSONArray(BrewExporter.json(listOf(BrewWithSamples(brew, emptyList())))).getJSONObject(0)
        assertEquals(0, element.getJSONArray("samples").length())
        assertEquals(1_792_931_400_000, element.getLong("started_at_ms"))
    }

    @Test
    fun frenchPressJsonKeepsItsContractMode() {
        val press = brew.copy(mode = BrewMode.FRENCH_PRESS, timeToFirstDropMs = null)

        val element = JSONArray(BrewExporter.json(listOf(BrewWithSamples(press, emptyList())))).getJSONObject(0)

        assertEquals("french_press", element.getString("mode"))
        assertEquals(org.json.JSONObject.NULL, element.get("time_to_first_drop_s"))
    }
}
