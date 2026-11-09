package io.github.mateuxlucax.caparao.data.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.RecipeRepository
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewSample
import io.github.mateuxlucax.caparao.model.BrewWithSamples
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind
import io.github.mateuxlucax.caparao.ui.FakeBrewDao
import io.github.mateuxlucax.caparao.ui.FakeRecipeDao
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
class BrewExportFilesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dao = FakeBrewDao()
    private val repository = BrewRepository(dao)
    private val recipeRepository = RecipeRepository(FakeRecipeDao())
    private val files = BrewExportFiles(context, repository, recipeRepository)

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
    private val samples = listOf(BrewSample(0, 0.0, 0.0), BrewSample(15_000, 17.9, 2.1))
    private val filter = brew.copy(
        id = "b7c1a2d3-4e5f-4a6b-8c9d-0e1f2a3b4c5d",
        mode = BrewMode.FILTER,
        startedAt = 1_792_931_500_000,
        durationMs = 150_000,
        doseGrams = 15.0,
        yieldGrams = 240.0,
        timeToFirstDropMs = 0,
    )
    private val filterSamples = listOf(BrewSample(0, 15.0, 0.0), BrewSample(150_000, 240.0, 1.6))
    private val brews = listOf(BrewWithSamples(brew, samples), BrewWithSamples(filter, filterSamples))

    /**
     * One test, not two: FileProvider caches its path strategy per authority for the process, and Robolectric gives
     * each test a fresh data directory, so a second test would look for the first one's roots.
     */
    @Test
    fun writesTheZipAndTheSingleBrewJsonThroughTheProvider() = runBlocking {
        repository.save(brew, samples)
        repository.save(filter, filterSamples)
        recipeRepository.save(
            Recipe(
                id = "5d2b9f60-1c4e-4a8b-8e37-c0a1f6d3b7e5",
                name = "V60",
                mode = BrewMode.FILTER,
                method = "V60",
                doseGrams = 15.0,
                targetRatio = 16.0,
                steps = listOf(RecipeStep(StepKind.BLOOM, 30.0, 0), RecipeStep(StepKind.POUR, 240.0, 30_000)),
                createdAt = 1_792_000_000_000,
                updatedAt = 1_792_000_000_000,
            ),
        )

        val all = files.all(includeSamples = true)
        assertEquals("application/zip", all.mimeType)
        assertEquals("content", all.uri.scheme)
        assertEquals("${context.packageName}.fileprovider", all.uri.authority)
        val bytes = context.contentResolver.openInputStream(all.uri)!!.use { it.readBytes() }
        val entries = zipEntries(bytes)
        assertEquals(setOf("brews.json", "brews.csv", "recipes.csv", "samples.csv"), entries.keys)
        assertTrue(entries.getValue("recipes.csv").contains("V60"))
        // The streamed archive must be what the in-memory export writes, with the platform's own org.json.
        assertEquals(BrewExporter.json(brews), entries.getValue("brews.json"))
        assertEquals(BrewExporter.brewsCsv(brews), entries.getValue("brews.csv"))
        assertEquals(BrewExporter.samplesCsv(brews), entries.getValue("samples.csv"))

        val one = files.one(brew, samples)
        assertEquals("application/json", one.mimeType)
        val json = context.contentResolver.openInputStream(one.uri)!!.bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        assertEquals(1, array.length())
        assertEquals(brew.id, array.getJSONObject(0).getString("id"))
        assertEquals(2, array.getJSONObject(0).getJSONArray("samples").length())
    }

    private fun zipEntries(bytes: ByteArray): Map<String, String> {
        val entries = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        return entries
    }
}
