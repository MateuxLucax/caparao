package io.github.mateuxlucax.caparao.ui

import io.github.mateuxlucax.caparao.data.local.CoffeeBagDao
import io.github.mateuxlucax.caparao.data.local.CoffeeBagEntity
import io.github.mateuxlucax.caparao.data.local.CoffeeBagUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The bag DAO in memory, without the brews it counts: a bag's usage is whatever a test sets in [usages]. The real
 * queries are tested on a device and in CoffeeBagRepositoryTest.
 */
class FakeCoffeeBagDao : CoffeeBagDao {
    val usages = MutableStateFlow<List<CoffeeBagUsage>>(emptyList())

    override suspend fun upsert(bag: CoffeeBagEntity) {
        val usage = usages.value.find { it.bag.id == bag.id }
        usages.value = usages.value.filterNot { it.bag.id == bag.id } +
            (usage?.copy(bag = bag) ?: CoffeeBagUsage(bag, usedGrams = 0.0, brewCount = 0, lastBrewAt = null, averageRating = null))
    }

    override fun observeAll() = usages.map { list -> list.sortedWith(compareBy<CoffeeBagUsage> { it.bag.finished }.thenByDescending { it.bag.createdAt }) }

    override suspend fun get(id: String) = usages.value.find { it.bag.id == id }

    override suspend fun unlinkBrews(id: String) = Unit

    override suspend fun deleteBag(id: String) {
        usages.value = usages.value.filterNot { it.bag.id == id }
    }
}
