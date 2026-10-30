package io.github.mateuxlucax.caparao.data.local

import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Brews on the phone: the source of truth; the server only ever gets copies. */
class BrewRepository(private val dao: BrewDao) {
    val brews: Flow<List<Brew>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun save(brew: Brew, samples: List<BrewSample>) =
        dao.insertWithSamples(brew.toEntity(), samples.map { BrewSampleEntity(brew.id, it.atMs, it.weightGrams, it.flowGramsPerSecond) })

    suspend fun get(id: String): Brew? = dao.get(id)?.toModel()

    suspend fun samples(id: String): List<BrewSample> = dao.samples(id).map { BrewSample(it.atMs, it.weightGrams, it.flow) }

    suspend fun delete(id: String) = dao.delete(id)

    /** Saves [brew]'s notes (coffee, grind, temperature, rating, notes) and marks it unsynced. */
    suspend fun updateNotes(brew: Brew) =
        dao.updateNotes(brew.id, brew.coffee, brew.grind, brew.waterTempCelsius, brew.rating, brew.notes)

    /** Past coffees, newest first, for the picker; the first is the one suggested for the next brew. */
    suspend fun recentCoffees(limit: Int = 8): List<String> = dao.recentCoffees(limit)

    /** The server confirmed [id] at [at] (epoch ms). */
    suspend fun markSynced(id: String, at: Long) = dao.markSynced(id, at)

    /** Ids of the brews the server hasn't confirmed, oldest first. */
    suspend fun unsyncedIds(): List<String> = dao.unsynced().map { it.id }

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
        grind = grind,
        waterTempCelsius = waterTempCelsius,
        rating = rating,
        notes = notes,
    )
}
