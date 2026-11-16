package dev.mateux.caparao.ui

import dev.mateux.caparao.data.local.CoffeeBagDao
import dev.mateux.caparao.data.local.CoffeeBagEntity
import dev.mateux.caparao.data.local.CoffeeBagUsage
import dev.mateux.caparao.data.local.CoffeeUseEntry
import dev.mateux.caparao.data.local.CoffeeUseEntryEntity
import dev.mateux.caparao.data.local.BagRecipeAdjustmentEntity
import dev.mateux.caparao.data.local.BagRecipeAdjustmentHistoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The bag DAO in memory, without the brews it counts: a bag's usage is whatever a test sets in [usages]. The real
 * queries are tested on a device and in CoffeeBagRepositoryTest.
 */
class FakeCoffeeBagDao : CoffeeBagDao {
    val usages = MutableStateFlow<List<CoffeeBagUsage>>(emptyList())
    private val useEntries = mutableListOf<CoffeeUseEntryEntity>()

    override suspend fun upsert(bag: CoffeeBagEntity) {
        val usage = usages.value.find { it.bag.id == bag.id }
        usages.value = usages.value.filterNot { it.bag.id == bag.id } +
            (usage?.copy(bag = bag) ?: CoffeeBagUsage(bag, usedGrams = 0.0, brewCount = 0, lastBrewAt = null, averageRating = null))
    }

    override suspend fun finish(id: String) = Unit
    override suspend fun insertUseEntry(entry: CoffeeUseEntryEntity) { useEntries += entry }
    override suspend fun deleteUseEntry(bagId: String, entryId: String) { useEntries.removeAll { it.bagId == bagId && it.id == entryId } }
    override fun observeUseEntries(bagId: String) = MutableStateFlow(useEntries.filter { it.bagId == bagId }.map { CoffeeUseEntry(it.id, it.grams, it.createdAt) })
    override suspend fun adjustments(id: String): List<BagRecipeAdjustmentEntity> = emptyList()
    override suspend fun copyAdjustment(bagId: String, recipeId: String, grind: Int?, waterTempCelsius: Int?, doseGrams: Double?) = Unit
    override suspend fun insertCopiedHistory(row: BagRecipeAdjustmentHistoryEntity) = Unit
    override suspend fun openAnotherBag(bag: CoffeeBagEntity, sourceBagId: String, finishSource: Boolean, changedAt: Long) = upsert(bag)

    override fun observeAll() = usages.map { list -> list.sortedWith(compareBy<CoffeeBagUsage> { it.bag.finished }.thenByDescending { it.bag.createdAt }) }

    override suspend fun get(id: String) = usages.value.find { it.bag.id == id }

    override suspend fun unlinkBrews(id: String) = Unit

    override suspend fun deleteBag(id: String) {
        usages.value = usages.value.filterNot { it.bag.id == id }
    }
}
