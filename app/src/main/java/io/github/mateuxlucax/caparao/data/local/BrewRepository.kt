package io.github.mateuxlucax.caparao.data.local

import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Brews on the phone: the source of truth; the server only ever gets copies. */
class BrewRepository(
    private val dao: BrewDao,
    private val onChanged: () -> Unit = {},
) {
    val brews: Flow<List<Brew>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    /** The brews that used a coffee bag, newest first. */
    fun forBag(bagId: String): Flow<List<Brew>> = dao.observeForBag(bagId).map { rows -> rows.map { it.toModel() } }

    suspend fun save(brew: Brew, samples: List<BrewSample>) {
        dao.insertWithSamples(brew.toEntity(), samples.map { BrewSampleEntity(brew.id, it.atMs, it.weightGrams, it.flowGramsPerSecond) })
        refreshAfterChange()
    }

    suspend fun get(id: String): Brew? = dao.get(id)?.toModel()

    suspend fun latest(): Brew? = dao.latest()?.toModel()

    suspend fun samples(id: String): List<BrewSample> = dao.samples(id).map { BrewSample(it.atMs, it.weightGrams, it.flow) }

    suspend fun delete(id: String) {
        dao.delete(id)
        refreshAfterChange()
    }

    /** Saves [brew]'s notes (coffee and its bag, grind, temperature, rating, notes), marks it unsynced and bumps its revision. */
    suspend fun updateNotes(brew: Brew) =
        dao.updateNotes(brew.id, brew.coffee, brew.bagId, brew.grind, brew.waterTempCelsius, brew.rating, brew.notes)

    /**
     * The server confirmed [id] at [at] (epoch ms), unless it changed since [revision]; false means the upload carried
     * stale data and the brew needs another attempt (#315).
     */
    suspend fun markSynced(id: String, at: Long, revision: Int): Boolean = dao.markSynced(id, at, revision) > 0

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
        revision = revision,
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
        revision = revision,
    )
}
