package io.github.mateuxlucax.caparao.data.export

import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewSample
import io.github.mateuxlucax.caparao.model.BrewWithSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.Locale
import java.util.zip.ZipInputStream

class BrewExporterCsvTest {
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
        recipeName = "Oster",
        coffee = "Caparaó",
        grind = "10",
        waterTempCelsius = 92,
        rating = 4,
        notes = "Sweet",
    )
    private val samples = listOf(BrewSample(0, 0.0, 0.0), BrewSample(15_000, 17.9, 2.1), BrewSample(28_600, 36.4, 1.1))
    private val brews = listOf(BrewWithSamples(brew, samples))

    @Test
    fun brewsCsvHasTheHeaderAndOneRowPerBrew() {
        val lines = BrewExporter.brewsCsv(brews).lines().filter { it.isNotEmpty() }

        assertEquals(
            "id,mode,started_at,duration_s,dose_g,yield_g,time_to_first_drop_s,flow_avg_gps,flow_peak_gps,interrupted," +
                "recipe_name,coffee,grind,water_temp_c,rating,notes",
            lines.first(),
        )
        assertEquals(2, lines.size)
        val row = lines[1].split(",")
        assertEquals(brew.id, row[0])
        assertEquals("espresso", row[1])
        assertEquals(Instant.ofEpochMilli(brew.startedAt).toString(), row[2])
        assertEquals("28.6", row[3])
        assertEquals("18", row[4])
        assertEquals("36.4", row[5])
        assertEquals("6.2", row[6])
        assertEquals("Oster", row[10])
        assertEquals("4", row[14])
        assertEquals("Sweet", row[15])
    }

    @Test
    fun csvQuotesSeparatorsQuotesAndNewlines() {
        val tricky = brew.copy(notes = "Sweet, \"bright\"\nnext")

        val csv = BrewExporter.brewsCsv(listOf(BrewWithSamples(tricky, emptyList())))

        // The field spans lines, so assert on the whole document rather than a split line.
        assertTrue(csv, csv.contains(",\"Sweet, \"\"bright\"\"\nnext\"\n"))
    }

    @Test
    fun numbersUseDotsWhateverTheLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("pt", "BR"))
            val csv = BrewExporter.brewsCsv(brews)

            assertTrue(csv, csv.contains(",28.6,18,36.4,6.2,1.3,2.1,"))
            assertFalse(csv, csv.contains("36,4"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun samplesCsvListsEveryPointInOrder() {
        val lines = BrewExporter.samplesCsv(brews).lines().filter { it.isNotEmpty() }

        assertEquals("brew_id,at_ms,weight_g,flow_gps", lines.first())
        assertEquals(listOf("0", "15000", "28600"), lines.drop(1).map { it.split(",")[1] })
    }

    @Test
    fun zipHasJsonAndBothCsvs() {
        val entries = zipEntries(includeSamples = true)

        assertEquals(setOf("brews.json", "brews.csv", "samples.csv"), entries.keys)
        assertTrue(entries.getValue("brews.json").trimStart().startsWith("["))
        assertTrue(entries.getValue("brews.csv").startsWith("id,mode,"))
        assertTrue(entries.getValue("samples.csv").startsWith("brew_id,"))
    }

    @Test
    fun zipCanSkipTheSamplesCsv() {
        assertEquals(setOf("brews.json", "brews.csv"), zipEntries(includeSamples = false).keys)
    }

    @Test
    fun emptyHistoryStillWritesTheHeaders() {
        val empty = emptyList<BrewWithSamples>()

        assertEquals("id,mode,", BrewExporter.brewsCsv(empty).take(8))
        assertEquals("brew_id,at_ms,", BrewExporter.samplesCsv(empty).take(14))
        assertEquals("[]", BrewExporter.json(empty))
    }

    private fun zipEntries(includeSamples: Boolean): Map<String, String> {
        val out = ByteArrayOutputStream()
        BrewExporter.writeZip(out, brews, includeSamples)
        val entries = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        return entries
    }
}
