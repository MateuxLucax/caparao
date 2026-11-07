package io.github.mateuxlucax.caparao.data.local

import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewSample
import io.github.mateuxlucax.caparao.model.BrewWithSamples
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Brews on the phone: the source of truth; the server only ever gets copies. */
class BrewRepository(
    private val dao: BrewDao,
    private val onChanged: () -> Unit = {},
) {
    val brews: Flow<List<Brew>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun save(brew: Brew, samples: List<BrewSample>) {
        dao.insertWithSamples(brew.toEntity(), samples.map { BrewSampleEntity(brew.id, it.atMs, it.weightGrams, it.flowGramsPerSecond) })
        refreshAfterChange()
    }

    suspend fun get(id: String): Brew? = dao.get(id)?.toModel()

    suspend fun latest(): Brew? = dao.latest()?.toModel()

    /** Saved brews whose end instants fall in [startInclusive, endExclusive), without loading their curves. */
    suspend fun endedBetween(startInclusive: Long, endExclusive: Long): List<Brew> =
        dao.endedBetween(startInclusive, endExclusive).map { it.toModel() }

    /** Every brew with its curve, oldest first, for the export (#76). */
    suspend fun allWithSamples(): List<BrewWithSamples> {
        val samples = dao.allSamples().groupBy { it.brewId }
        return dao.all().map { row ->
            BrewWithSamples(row.toModel(), samples[row.id].orEmpty().map { BrewSample(it.atMs, it.weightGrams, it.flow) })
        }
    }

    suspend fun samples(id: String): List<BrewSample> = dao.samples(id).map { BrewSample(it.atMs, it.weightGrams, it.flow) }

    suspend fun delete(id: String) {
        dao.delete(id)
        refreshAfterChange()
    }

    /** Saves [brew]'s notes (coffee and its bag, grind, temperature, rating, notes) and marks it unsynced. */
    suspend fun updateNotes(brew: Brew) =
        dao.updateNotes(brew.id, brew.coffee, brew.bagId, brew.grind, brew.waterTempCelsius, brew.rating, brew.notes)

    /** The server confirmed [id] at [at] (epoch ms). */
    suspend fun markSynced(id: String, at: Long) = dao.markSynced(id, at)

    /** Ids of the brews the server hasn't confirmed, oldest first. */
    suspend fun unsyncedIds(): List<String> = dao.unsynced().map { it.id }

    /** A widget refresh is best-effort and must not make a committed brew write fail. */
    private fun refreshAfterChange() {
        runCatching(onChanged)
    }

    private fun BrewEntity.toModel() = Brew(
        id = id,
        mode = BrewMode.valueOf(mode),
        startedAt = startedAt,
        durationMs = durationMs,
        doseGrams = doseGrams,
        yieldGrams = yieldGrams,
        timeToFirstDropMs = timeToFirstDropMs,
        flowAvgGramsPerSecond = flowAvg,
        flowPeakGramsPerSecond = flowPeak,
        interrupted = interrupted,
        syncedAt = syncedAt,
        recipeId = recipeId,
        recipeName = recipeName,
        coffee = coffee,
        bagId = bagId,
        grind = grind,
        waterTempCelsius = waterTempCelsius,
        rating = rating,
        notes = notes,
    )

    private fun Brew.toEntity() = BrewEntity(
        id = id,
        mode = mode.name,
        startedAt = startedAt,
        durationMs = durationMs,
        doseGrams = doseGrams,
        yieldGrams = yieldGrams,
        timeToFirstDropMs = timeToFirstDropMs,
        flowAvg = flowAvgGramsPerSecond,
        flowPeak = flowPeakGramsPerSecond,
        interrupted = interrupted,
        syncedAt = syncedAt,
        recipeId = recipeId,
        recipeName = recipeName,
        coffee = coffee,
        bagId = bagId,
        grind = grind,
        waterTempCelsius = waterTempCelsius,
        rating = rating,
        notes = notes,
    )
}
