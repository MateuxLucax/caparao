package io.github.mateuxlucax.caparao.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
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

/** A bag with what its linked brews add up to (#288). */
data class CoffeeBagUsage(
    @androidx.room.Embedded val bag: CoffeeBagEntity,
    @ColumnInfo(name = "used_grams") val usedGrams: Double,
    @ColumnInfo(name = "brew_count") val brewCount: Int,
    @ColumnInfo(name = "last_brew_at") val lastBrewAt: Long?,
    /** Brews with a rating only; null when none has one. */
    @ColumnInfo(name = "average_rating") val averageRating: Double?,
)

@Dao
interface CoffeeBagDao {
    @Upsert
    suspend fun upsert(bag: CoffeeBagEntity)

    /** Open bags first, newest first. */
    @Query(USAGE_QUERY + " GROUP BY coffee_bags.id ORDER BY coffee_bags.finished, coffee_bags.created_at DESC")
    fun observeAll(): Flow<List<CoffeeBagUsage>>

    @Query(USAGE_QUERY + " WHERE coffee_bags.id = :id GROUP BY coffee_bags.id")
    suspend fun get(id: String): CoffeeBagUsage?

    @Query("UPDATE brews SET bag_id = NULL WHERE bag_id = :id")
    suspend fun unlinkBrews(id: String)

    @Query("DELETE FROM coffee_bags WHERE id = :id")
    suspend fun deleteBag(id: String)

    /** The brews keep their coffee name; only their link to the bag goes. */
    @Transaction
    suspend fun delete(id: String) {
        unlinkBrews(id)
        deleteBag(id)
    }
}

private const val USAGE_QUERY = "SELECT coffee_bags.*, COALESCE(SUM(brews.dose_grams), 0) AS used_grams, " +
    "COUNT(brews.id) AS brew_count, MAX(brews.started_at) AS last_brew_at, AVG(brews.rating) AS average_rating " +
    "FROM coffee_bags LEFT JOIN brews ON brews.bag_id = coffee_bags.id"
