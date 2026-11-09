package io.github.mateuxlucax.caparao.data.export

import io.github.mateuxlucax.caparao.data.local.BrewDao
import io.github.mateuxlucax.caparao.data.local.BrewEntity
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.BrewSampleEntity
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewSample
import io.github.mateuxlucax.caparao.model.BrewWithSamples
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind
import io.github.mateuxlucax.caparao.ui.FakeBrewDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

/**
 * The streamed archive of #319 must be byte-for-byte what the in-memory export wrote, while holding a single brew's
 * curve instead of every curve in the history.
 */
class BrewExporterStreamTest {
    private val espresso = Brew(
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
        notes = "Sweet, \"bright\"\nnext",
    )
    private val filter = Brew(
        id = "b7c1a2d3-4e5f-4a6b-8c9d-0e1f2a3b4c5d",
        mode = BrewMode.FILTER,
        startedAt = 1_792_931_500_000,
        durationMs = 150_000,
        doseGrams = 15.0,
        yieldGrams = 240.0,
        timeToFirstDropMs = 0,
        flowAvgGramsPerSecond = 1.6,
        flowPeakGramsPerSecond = 3.2,
        interrupted = true,
        notes = "Even",
    )
    private val press = Brew(
        id = "c8d2b3e4-5f60-4b7c-9d0e-1f2a3b4c5d6e",
        mode = BrewMode.FRENCH_PRESS,
        startedAt = 1_792_931_600_000,
        durationMs = 240_000,
        doseGrams = 30.0,
        yieldGrams = 500.0,
        timeToFirstDropMs = null,
        flowAvgGramsPerSecond = 2.1,
        flowPeakGramsPerSecond = 4.4,
        interrupted = false,
    )
    private val recipe = Recipe(
        id = "5d2b9f60-1c4e-4a8b-8e37-c0a1f6d3b7e5",
        name = "V60",
        mode = BrewMode.FILTER,
        method = "V60",
        doseGrams = 15.0,
        targetRatio = 16.0,
        grind = "22",
        waterTempCelsius = 94,
        notes = "Bright",
        steps = listOf(RecipeStep(StepKind.BLOOM, 30.0, 0), RecipeStep(StepKind.POUR, 240.0, 30_000)),
        createdAt = 1_792_000_000_000,
        updatedAt = 1_792_000_000_000,
    )
    private val recipes = listOf(recipe)

    @Test
    fun streamedArchiveMatchesTheInMemoryExport() = runBlocking {
        val brews = listOf(
            BrewWithSamples(espresso, listOf(BrewSample(0, 0.0, 0.0), BrewSample(15_000, 17.9, 2.1), BrewSample(28_600, 36.4, 1.1))),
            BrewWithSamples(filter, listOf(BrewSample(0, 15.0, 0.0), BrewSample(150_000, 240.0, 1.6))),
            BrewWithSamples(press, emptyList()),
        )

        val entries = zipEntries(brews)

        assertEquals(BrewExporter.json(brews), entries.getValue("brews.json"))
        assertEquals(BrewExporter.brewsCsv(brews), entries.getValue("brews.csv"))
        assertEquals(BrewExporter.recipesCsv(recipes), entries.getValue("recipes.csv"))
        assertEquals(BrewExporter.samplesCsv(brews), entries.getValue("samples.csv"))
    }

    @Test
    fun singleBrewArchiveMatchesTheInMemoryExport() = runBlocking {
        // org.json prints a one-element array on one line, so the single-brew archive takes its own path.
        val brews = listOf(BrewWithSamples(espresso, listOf(BrewSample(0, 0.0, 0.0))))

        val entries = zipEntries(brews)

        assertEquals(BrewExporter.json(brews), entries.getValue("brews.json"))
        assertEquals(BrewExporter.samplesCsv(brews), entries.getValue("samples.csv"))
    }

    @Test
    fun emptyArchiveMatchesTheInMemoryExport() = runBlocking {
        val empty = emptyList<BrewWithSamples>()

        val entries = zipEntries(empty)

        assertEquals("[]", entries.getValue("brews.json"))
        assertEquals(BrewExporter.brewsCsv(empty), entries.getValue("brews.csv"))
        assertEquals(BrewExporter.samplesCsv(empty), entries.getValue("samples.csv"))
    }

    /**
     * 500 brews of 2,400 samples inside Gradle's fixed test heap: the fake DAO fails the test if the export asks for
     * every curve at once, and each curve is read once for the JSON entry and once for `samples.csv`.
     */
    @Test
    fun fiveHundredLargeCurvesStreamOneAtATime() = runBlocking {
        val dao = GeneratedCurvesDao(brewCount = 500, samplesPerBrew = 2_400)
        val repository = BrewRepository(dao)
        val sink = CountingOutputStream()

        BrewExporter.writeZip(sink, repository.all(), repository::samples, emptyList(), includeSamples = true)

        assertEquals(1_000, dao.curvesRead)
        assertEquals(500, dao.ids.size)
        assertTrue("the archive is written", sink.bytes > 0)
    }

    private suspend fun zipEntries(brews: List<BrewWithSamples>, includeSamples: Boolean = true): Map<String, String> {
        val out = ByteArrayOutputStream()
        BrewExporter.writeZip(out, brews.map { it.brew }, { id -> brews.first { it.brew.id == id }.samples }, recipes, includeSamples)
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

    /** The real DAO's summary query, with curves generated on demand instead of held in memory. */
    private class GeneratedCurvesDao(
        private val delegate: FakeBrewDao = FakeBrewDao(),
        brewCount: Int,
        private val samplesPerBrew: Int,
    ) : BrewDao by delegate {
        init {
            delegate.brews.value = (0 until brewCount).map { index ->
                BrewEntity(
                    id = "brew-$index",
                    mode = BrewMode.FILTER.name,
                    startedAt = index.toLong(),
                    durationMs = 240_000,
                    doseGrams = 15.0,
                    yieldGrams = 240.0,
                    timeToFirstDropMs = 0,
                    flowAvg = 1.6,
                    flowPeak = 3.2,
                    interrupted = false,
                    syncedAt = null,
                )
            }
        }

        var curvesRead = 0
        val ids = mutableSetOf<String>()

        override suspend fun samples(brewId: String): List<BrewSampleEntity> {
            curvesRead++
            ids += brewId
            return List(samplesPerBrew) { index -> BrewSampleEntity(brewId, index * 100L, index * 0.1, index * 0.01) }
        }

        override suspend fun allSamples(): List<BrewSampleEntity> =
            error("the export must not load every curve at once")
    }

    private class CountingOutputStream : OutputStream() {
        var bytes = 0L
            private set

        override fun write(b: Int) {
            bytes++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            bytes += len
        }
    }
}
