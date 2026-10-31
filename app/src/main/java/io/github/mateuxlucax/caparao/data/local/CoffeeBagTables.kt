package io.github.mateuxlucax.caparao.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A coffee bag row. Column names are the schema (app/schemas); change them only with a migration. */
@Entity(tableName = "coffee_bags")
data class CoffeeBagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val roaster: String?,
    val origin: String?,
    @ColumnInfo(name = "roasted_at") val roastedAt: Long?,
    @ColumnInfo(name = "weight_grams") val weightGrams: Double,
    val notes: String?,
    val finished: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** A bag with the dose of the brews that used it since it was added. */
data class CoffeeBagUsage(
    @androidx.room.Embedded val bag: CoffeeBagEntity,
    @ColumnInfo(name = "used_grams") val usedGrams: Double,
)

@Dao
interface CoffeeBagDao {
    @Upsert
    suspend fun upsert(bag: CoffeeBagEntity)

    /** Open bags first, newest first. */
    @Query(
        "SELECT coffee_bags.*, COALESCE((SELECT SUM(dose_grams) FROM brews WHERE brews.coffee = coffee_bags.name " +
            "AND brews.started_at >= coffee_bags.created_at), 0) AS used_grams FROM coffee_bags ORDER BY finished, created_at DESC",
    )
    fun observeAll(): Flow<List<CoffeeBagUsage>>

    @Query("SELECT * FROM coffee_bags WHERE id = :id")
    suspend fun get(id: String): CoffeeBagEntity?

    @Query("DELETE FROM coffee_bags WHERE id = :id")
    suspend fun delete(id: String)
}
