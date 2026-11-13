package dev.mateux.caparao.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.mateux.caparao.model.BagRecipeAdjustment
import dev.mateux.caparao.model.BagRecipeAdjustmentHistory
import dev.mateux.caparao.model.BagRecipeAdjustmentReason
import kotlinx.coroutines.flow.Flow

/** One coffee bag's overrides for one recipe (#460). Deleting either parent removes the adjustment. */
@Entity(
    tableName = "bag_recipe_adjustments",
    primaryKeys = ["bag_id", "recipe_id"],
    foreignKeys = [
        ForeignKey(CoffeeBagEntity::class, ["id"], ["bag_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(RecipeEntity::class, ["id"], ["recipe_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("recipe_id")],
)
data class BagRecipeAdjustmentEntity(
    @ColumnInfo(name = "bag_id") val bagId: String,
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    val grind: Int?,
    @ColumnInfo(name = "water_temp_celsius") val waterTempCelsius: Int?,
    @ColumnInfo(name = "dose_grams") val doseGrams: Double?,
    val tuned: Boolean,
)

/** Previous and new adjustment values, retained only while the bag and recipe exist. */
@Entity(
    tableName = "bag_recipe_adjustment_history",
    foreignKeys = [
        ForeignKey(
            BagRecipeAdjustmentEntity::class,
            parentColumns = ["bag_id", "recipe_id"],
            childColumns = ["bag_id", "recipe_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["bag_id", "recipe_id", "changed_at"])],
)
data class BagRecipeAdjustmentHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "bag_id") val bagId: String,
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    @ColumnInfo(name = "changed_at") val changedAt: Long,
    @ColumnInfo(name = "old_grind") val oldGrind: Int?,
    @ColumnInfo(name = "new_grind") val newGrind: Int?,
    @ColumnInfo(name = "old_water_temp_celsius") val oldWaterTempCelsius: Int?,
    @ColumnInfo(name = "new_water_temp_celsius") val newWaterTempCelsius: Int?,
    @ColumnInfo(name = "old_dose_grams") val oldDoseGrams: Double?,
    @ColumnInfo(name = "new_dose_grams") val newDoseGrams: Double?,
    @ColumnInfo(name = "old_tuned") val oldTuned: Boolean,
    @ColumnInfo(name = "new_tuned") val newTuned: Boolean,
    val reason: String,
    /** No foreign key: the brew id is historical context even if the brew is deleted. */
    @ColumnInfo(name = "brew_id") val brewId: String?,
)

@Dao
interface BagRecipeAdjustmentDao {
    @Query("SELECT * FROM bag_recipe_adjustments WHERE bag_id = :bagId AND recipe_id = :recipeId")
    suspend fun get(bagId: String, recipeId: String): BagRecipeAdjustmentEntity?

    @Query("SELECT * FROM bag_recipe_adjustments WHERE bag_id = :bagId AND recipe_id = :recipeId")
    fun observe(bagId: String, recipeId: String): Flow<BagRecipeAdjustmentEntity?>

    @Upsert
    suspend fun upsert(adjustment: BagRecipeAdjustmentEntity)

    @Insert
    suspend fun insertHistory(history: BagRecipeAdjustmentHistoryEntity)

    @Query(
        "SELECT * FROM bag_recipe_adjustment_history WHERE bag_id = :bagId AND recipe_id = :recipeId " +
            "ORDER BY changed_at DESC, id DESC",
    )
    fun observeHistory(bagId: String, recipeId: String): Flow<List<BagRecipeAdjustmentHistoryEntity>>

    /** Store the adjustment and its before/after record atomically. No history is added for an unchanged value. */
    @Transaction
    suspend fun save(adjustment: BagRecipeAdjustmentEntity, changedAt: Long, reason: BagRecipeAdjustmentReason, brewId: String?) {
        val previous = get(adjustment.bagId, adjustment.recipeId)
        if (previous == adjustment) return
        upsert(adjustment)
        insertHistory(
            BagRecipeAdjustmentHistoryEntity(
                bagId = adjustment.bagId,
                recipeId = adjustment.recipeId,
                changedAt = changedAt,
                oldGrind = previous?.grind,
                newGrind = adjustment.grind,
                oldWaterTempCelsius = previous?.waterTempCelsius,
                newWaterTempCelsius = adjustment.waterTempCelsius,
                oldDoseGrams = previous?.doseGrams,
                newDoseGrams = adjustment.doseGrams,
                oldTuned = previous?.tuned ?: false,
                newTuned = adjustment.tuned,
                reason = reason.name.lowercase(),
                brewId = brewId,
            ),
        )
    }
}

internal fun BagRecipeAdjustment.toEntity() = BagRecipeAdjustmentEntity(
    bagId = bagId,
    recipeId = recipeId,
    grind = grind,
    waterTempCelsius = waterTempCelsius,
    doseGrams = doseGrams,
    tuned = tuned,
)

internal fun BagRecipeAdjustmentEntity.toModel() = BagRecipeAdjustment(
    bagId = bagId,
    recipeId = recipeId,
    grind = grind,
    waterTempCelsius = waterTempCelsius,
    doseGrams = doseGrams,
    tuned = tuned,
)

internal fun BagRecipeAdjustmentHistoryEntity.toModel() = BagRecipeAdjustmentHistory(
    id = id,
    bagId = bagId,
    recipeId = recipeId,
    changedAt = changedAt,
    oldGrind = oldGrind,
    newGrind = newGrind,
    oldWaterTempCelsius = oldWaterTempCelsius,
    newWaterTempCelsius = newWaterTempCelsius,
    oldDoseGrams = oldDoseGrams,
    newDoseGrams = newDoseGrams,
    oldTuned = oldTuned,
    newTuned = newTuned,
    reason = BagRecipeAdjustmentReason.valueOf(reason.uppercase()),
    brewId = brewId,
)
