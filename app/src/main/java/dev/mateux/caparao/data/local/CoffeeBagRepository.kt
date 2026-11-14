package dev.mateux.caparao.data.local

import dev.mateux.caparao.model.CoffeeBag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Coffee bags on the phone; like recipes, they never leave it. A brew links to its bag locally (#288). */
class CoffeeBagRepository(private val dao: CoffeeBagDao) {
    /** Open bags first, newest first, each with what its brews used. */
    val bags: Flow<List<CoffeeBag>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun get(id: String): CoffeeBag? = dao.get(id)?.toModel()

    /** The bag with [id] as it is now; null once it is deleted. */
    fun observe(id: String): Flow<CoffeeBag?> = bags.map { all -> all.firstOrNull { it.id == id } }

    suspend fun save(bag: CoffeeBag) = dao.upsert(bag.toEntity())

    /** Brews keep their coffee name but lose their bag. */
    suspend fun delete(id: String) = dao.delete(id)
}

private fun CoffeeBagUsage.toModel() = CoffeeBag(
    id = bag.id, name = bag.name, roaster = bag.roaster, origin = bag.origin, roastedAt = bag.roastedAt,
    weightGrams = bag.weightGrams, notes = bag.notes, finished = bag.finished, createdAt = bag.createdAt,
    tastingNotes = bag.tastingNotes,
    usedGrams = usedGrams, brewCount = brewCount, lastBrewedAt = lastBrewAt, averageRating = averageRating,
)

internal fun CoffeeBag.toEntity() = CoffeeBagEntity(
    id = id, name = name, roaster = roaster, origin = origin, roastedAt = roastedAt, weightGrams = weightGrams,
    notes = notes, finished = finished, createdAt = createdAt,
    tastingNotes = tastingNotes,
)
