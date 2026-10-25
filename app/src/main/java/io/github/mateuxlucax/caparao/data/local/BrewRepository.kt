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
    )
}
