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
    override suspend fun get(id: String) = brews.value.find { it.id == id }
    override suspend fun samples(brewId: String) = samples[brewId].orEmpty()
    override suspend fun unsynced() = brews.value.filter { it.syncedAt == null }.sortedBy { it.startedAt }

    override suspend fun markSynced(id: String, at: Long) {
        brews.value = brews.value.map { if (it.id == id) it.copy(syncedAt = at) else it }
    }

    override suspend fun delete(id: String) {
        brews.value = brews.value.filterNot { it.id == id }
        samples.remove(id)
    }
}
