package dev.mateux.caparao.data.local

import dev.mateux.caparao.model.CoffeeBag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Coffee bags on the phone; like recipes, they never leave it. A brew links to its bag locally (#288). */
class CoffeeBagRepository(private val dao: CoffeeBagDao) {
    /** Open bags first, newest first, each with what its brews used. */
    val bags: Flow<List<CoffeeBag>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun get(id: String): CoffeeBag? = dao.get(id)?.toModel()

    /** The bag with [id] as it is now; null once it is deleted. */
    fun observe(id: String): Flow<CoffeeBag?> = bags.map { all -> all.firstOrNull { it.id == id } }

    suspend fun save(bag: CoffeeBag) = dao.upsert(bag.toEntity())

    fun useEntries(id: String) = dao.observeUseEntries(id)

    suspend fun logUsed(id: String, grams: Int, createdAt: Long) =
        dao.insertUseEntry(CoffeeUseEntryEntity(UUID.randomUUID().toString(), id, grams, createdAt))

    suspend fun restoreUseEntry(id: String, entry: CoffeeUseEntry) =
        dao.insertUseEntry(CoffeeUseEntryEntity(entry.id, id, entry.grams, entry.createdAt))

    suspend fun removeUseEntry(bagId: String, entryId: String) = dao.deleteUseEntry(bagId, entryId)

    suspend fun openAnotherBag(bag: CoffeeBag, sourceBagId: String, finishSource: Boolean, changedAt: Long) =
        dao.openAnotherBag(bag.toEntity(), sourceBagId, finishSource, changedAt)

    /** Brews keep their coffee name but lose their bag. */
    suspend fun delete(id: String) = dao.delete(id)
}

private fun CoffeeBagUsage.toModel() = CoffeeBag(
    id = bag.id, name = bag.name, roaster = bag.roaster, origin = bag.origin, roastedAt = bag.roastedAt,
    weightGrams = bag.weightGrams, notes = bag.notes, finished = bag.finished, createdAt = bag.createdAt,
    tastingNotes = bag.tastingNotes,
    caffeineMgPerGram = bag.caffeineMgPerGram,
    usedGrams = usedGrams, brewCount = brewCount, lastBrewedAt = lastBrewAt, averageRating = averageRating,
)

internal fun CoffeeBag.toEntity() = CoffeeBagEntity(
    id = id, name = name, roaster = roaster, origin = origin, roastedAt = roastedAt, weightGrams = weightGrams,
    notes = notes, finished = finished, createdAt = createdAt,
    tastingNotes = tastingNotes,
    caffeineMgPerGram = caffeineMgPerGram,
)
