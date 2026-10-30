package io.github.mateuxlucax.caparao.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A recipe row (docs/adr/0018-recipes.md). Column names are the schema (app/schemas); change them only with a migration. */
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** `BrewMode` name. */
    val mode: String,
    val method: String,
    @ColumnInfo(name = "dose_grams") val doseGrams: Double,
    @ColumnInfo(name = "target_ratio") val targetRatio: Double,
    val grind: String?,
    @ColumnInfo(name = "water_temp_celsius") val waterTempCelsius: Int?,
    val notes: String?,
    @ColumnInfo(name = "target_min_ms") val targetMinMs: Long?,
    @ColumnInfo(name = "target_max_ms") val targetMaxMs: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** One step of a recipe, at [position] from 0; deleted with its recipe. */
@Entity(
    tableName = "recipe_steps",
    primaryKeys = ["recipe_id", "position"],
    foreignKeys = [ForeignKey(RecipeEntity::class, ["id"], ["recipe_id"], onDelete = ForeignKey.CASCADE)],
)
data class RecipeStepEntity(
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    val position: Int,
    /** `StepKind` name. */
    val kind: String,
    @ColumnInfo(name = "target_weight_grams") val targetWeightGrams: Double?,
    @ColumnInfo(name = "target_at_ms") val targetAtMs: Long,
)

@Dao
interface RecipeDao {
    @Upsert
    suspend fun upsert(recipe: RecipeEntity)

    @Upsert
    suspend fun insertSteps(steps: List<RecipeStepEntity>)

    @Query("DELETE FROM recipe_steps WHERE recipe_id = :recipeId")
    suspend fun deleteSteps(recipeId: String)

    /** A recipe and its steps, replacing the old steps, all or nothing. */
    @Transaction
    suspend fun save(recipe: RecipeEntity, steps: List<RecipeStepEntity>) {
        upsert(recipe)
        deleteSteps(recipe.id)
        insertSteps(steps)
    }

    /** By mode, then name. */
    @Query("SELECT * FROM recipes ORDER BY mode, name COLLATE NOCASE")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipe_steps ORDER BY recipe_id, position")
    fun observeAllSteps(): Flow<List<RecipeStepEntity>>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun get(id: String): RecipeEntity?

    @Query("SELECT * FROM recipe_steps WHERE recipe_id = :recipeId ORDER BY position")
    suspend fun steps(recipeId: String): List<RecipeStepEntity>

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun delete(id: String)
}
