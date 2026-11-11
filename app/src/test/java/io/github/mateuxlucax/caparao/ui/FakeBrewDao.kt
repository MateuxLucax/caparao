package io.github.mateuxlucax.caparao.ui

import io.github.mateuxlucax.caparao.data.local.BrewDao
import io.github.mateuxlucax.caparao.data.local.BrewEntity
import io.github.mateuxlucax.caparao.data.local.BrewSampleEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** The DAO's behaviour in memory; the real queries are tested on a device (BrewDaoTest). */
class FakeBrewDao : BrewDao {
    val brews = MutableStateFlow<List<BrewEntity>>(emptyList())
    val samples = HashMap<String, List<BrewSampleEntity>>()

    override suspend fun insert(brew: BrewEntity) {
        check(brews.value.none { it.id == brew.id }) { "duplicate id" }
        brews.value += brew
    }

    override suspend fun insertSamples(samples: List<BrewSampleEntity>) {
        samples.groupBy { it.brewId }.forEach { (id, list) -> this.samples[id] = list }
    }

    override fun observeAll() = brews.map { list -> list.sortedByDescending { it.startedAt } }
    override fun observeForBag(bagId: String) = brews.map { list -> list.filter { it.bagId == bagId }.sortedByDescending { it.startedAt } }
    override suspend fun all() = brews.value.sortedBy { it.startedAt }
    override suspend fun latest() = brews.value.maxWithOrNull(compareBy<BrewEntity> { it.startedAt }.thenBy { it.id })
    override suspend fun allSamples() = samples.values.flatten().sortedBy { it.atMs }
    override suspend fun get(id: String) = brews.value.find { it.id == id }
    override suspend fun samples(brewId: String) = samples[brewId].orEmpty()
    override suspend fun unsynced() = brews.value.filter { it.syncedAt == null }.sortedBy { it.startedAt }

    override suspend fun markSynced(id: String, at: Long, revision: Int): Int {
        var updated = 0
        brews.value = brews.value.map {
            if (it.id == id && it.revision == revision) {
                updated++
                it.copy(syncedAt = at)
            } else {
                it
            }
        }
        return updated
    }

    override suspend fun delete(id: String) {
        brews.value = brews.value.filterNot { it.id == id }
        samples.remove(id)
    }

    override suspend fun updateNotes(id: String, coffee: String?, bagId: String?, grind: String?, waterTempCelsius: Int?, rating: Int?, notes: String?) {
        brews.value = brews.value.map {
            if (it.id == id) {
                it.copy(
                    coffee = coffee,
                    bagId = bagId,
                    grind = grind,
                    waterTempCelsius = waterTempCelsius,
                    rating = rating,
                    notes = notes,
                    syncedAt = null,
                    revision = it.revision + 1,
                )
            } else {
                it
            }
        }
    }
}
