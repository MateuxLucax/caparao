package io.github.mateuxlucax.caparao.data.local

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** A brew row. Column names are the schema (app/schemas); change them only with a migration. */
@Entity(tableName = "brews", indices = [Index("started_at")])
data class BrewEntity(
    @PrimaryKey val id: String,
    /** `BrewMode` name. */
    val mode: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "dose_grams") val doseGrams: Double?,
    @ColumnInfo(name = "yield_grams") val yieldGrams: Double,
    @ColumnInfo(name = "time_to_first_drop_ms") val timeToFirstDropMs: Long?,
    @ColumnInfo(name = "flow_avg") val flowAvg: Double,
    @ColumnInfo(name = "flow_peak") val flowPeak: Double,
    val interrupted: Boolean,
    @ColumnInfo(name = "synced_at") val syncedAt: Long?,
)

/** One point of a brew's curve; deleted with its brew. */
@Entity(
    tableName = "brew_samples",
    primaryKeys = ["brew_id", "at_ms"],
    foreignKeys = [ForeignKey(BrewEntity::class, ["id"], ["brew_id"], onDelete = ForeignKey.CASCADE)],
)
data class BrewSampleEntity(
    @ColumnInfo(name = "brew_id") val brewId: String,
    @ColumnInfo(name = "at_ms") val atMs: Long,
    @ColumnInfo(name = "weight_grams") val weightGrams: Double,
    @ColumnInfo(name = "flow") val flow: Double,
)

@Dao
interface BrewDao {
    @Insert
    suspend fun insert(brew: BrewEntity)

    @Insert
    suspend fun insertSamples(samples: List<BrewSampleEntity>)

    /** A brew and its curve, all or nothing. */
    @Transaction
    suspend fun insertWithSamples(brew: BrewEntity, samples: List<BrewSampleEntity>) {
        insert(brew)
        insertSamples(samples)
    }

    /** Newest first. */
    @Query("SELECT * FROM brews ORDER BY started_at DESC")
    fun observeAll(): Flow<List<BrewEntity>>

    @Query("SELECT * FROM brews WHERE id = :id")
    suspend fun get(id: String): BrewEntity?

    @Query("SELECT * FROM brew_samples WHERE brew_id = :brewId ORDER BY at_ms")
    suspend fun samples(brewId: String): List<BrewSampleEntity>

    @Query("SELECT * FROM brews WHERE synced_at IS NULL ORDER BY started_at")
    suspend fun unsynced(): List<BrewEntity>

    @Query("UPDATE brews SET synced_at = :at WHERE id = :id")
    suspend fun markSynced(id: String, at: Long)

    @Query("DELETE FROM brews WHERE id = :id")
    suspend fun delete(id: String)
}

/** The app's database: brews and their curves, only on the phone (docs/adr/0003-offline-first.md). */
@Database(entities = [BrewEntity::class, BrewSampleEntity::class], version = 1)
abstract class BrewDatabase : RoomDatabase() {
    abstract fun brews(): BrewDao

    companion object {
        /** The file name must never change. */
        const val NAME = "caparao.db"

        fun open(context: Context): BrewDatabase = Room.databaseBuilder(context, BrewDatabase::class.java, NAME).build()
    }
}
