package io.github.mateuxlucax.caparao.data.local

import io.github.mateuxlucax.caparao.model.CoffeeBag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Coffee bags on the phone; like recipes, they never leave it, only a brew's coffee name does. */
class CoffeeBagRepository(private val dao: CoffeeBagDao) {
    /** Open bags first, newest first, each with what its brews used. */
    val bags: Flow<List<CoffeeBag>> = dao.observeAll().map { rows -> rows.map { it.bag.toModel(it.usedGrams) } }

    suspend fun get(id: String): CoffeeBag? = dao.get(id)?.toModel(0.0)

    suspend fun save(bag: CoffeeBag) = dao.upsert(bag.toEntity())

    /** Brews keep their coffee name. */
    suspend fun delete(id: String) = dao.delete(id)
}

private fun CoffeeBagEntity.toModel(usedGrams: Double) = CoffeeBag(
    id = id, name = name, roaster = roaster, origin = origin, roastedAt = roastedAt, weightGrams = weightGrams,
    notes = notes, finished = finished, createdAt = createdAt, usedGrams = usedGrams,
)

internal fun CoffeeBag.toEntity() = CoffeeBagEntity(
    id = id, name = name, roaster = roaster, origin = origin, roastedAt = roastedAt, weightGrams = weightGrams,
    notes = notes, finished = finished, createdAt = createdAt,
)
