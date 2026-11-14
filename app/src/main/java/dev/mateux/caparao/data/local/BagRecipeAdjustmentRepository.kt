package dev.mateux.caparao.data.local

import dev.mateux.caparao.model.BagRecipeAdjustment
import dev.mateux.caparao.model.BagRecipeAdjustmentHistory
import dev.mateux.caparao.model.BagRecipeAdjustmentReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Local adjustment data for one (bag, recipe) pair. */
class BagRecipeAdjustmentRepository(private val dao: BagRecipeAdjustmentDao) {
    suspend fun get(bagId: String, recipeId: String): BagRecipeAdjustment? =
        dao.get(bagId, recipeId)?.toModel()

    fun observe(bagId: String, recipeId: String): Flow<BagRecipeAdjustment?> =
        dao.observe(bagId, recipeId).map { it?.toModel() }

    /** Every adjustment of one bag, for its detail screen (#462). */
    fun forBag(bagId: String): Flow<List<BagRecipeAdjustment>> =
        dao.observeForBag(bagId).map { rows -> rows.map { it.toModel() } }

    fun history(bagId: String, recipeId: String): Flow<List<BagRecipeAdjustmentHistory>> =
        dao.observeHistory(bagId, recipeId).map { rows -> rows.map { it.toModel() } }

    /** Saves a change and a history row; suggestions must identify the brew that prompted them. */
    suspend fun save(
        adjustment: BagRecipeAdjustment,
        changedAt: Long,
        reason: BagRecipeAdjustmentReason = BagRecipeAdjustmentReason.MANUAL,
        brewId: String? = null,
    ) {
        require((reason == BagRecipeAdjustmentReason.SUGGESTION) == (brewId != null)) {
            "Suggestions require a brew id; manual changes do not use one"
        }
        dao.save(adjustment.toEntity(), changedAt, reason, brewId)
    }
}
