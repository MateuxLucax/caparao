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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** A brew row. Column names are the schema (app/schemas); change them only with a migration. */
@Entity(tableName = "brews", indices = [Index("started_at")])
data class BrewEntity(
    @PrimaryKey val id: String,
    /** `BrewMode` name. */
    val mode: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "dose_grams") val doseGrams: Double,
    @ColumnInfo(name = "yield_grams") val yieldGrams: Double,
    @ColumnInfo(name = "time_to_first_drop_ms") val timeToFirstDropMs: Long?,
    @ColumnInfo(name = "flow_avg") val flowAvg: Double,
    @ColumnInfo(name = "flow_peak") val flowPeak: Double,
    val interrupted: Boolean,
    @ColumnInfo(name = "synced_at") val syncedAt: Long?,
    /** No foreign key: deleting a recipe keeps its brews (docs/adr/0018-recipes.md). */
    @ColumnInfo(name = "recipe_id") val recipeId: String? = null,
    @ColumnInfo(name = "recipe_name") val recipeName: String? = null,
    val coffee: String? = null,
    val grind: String? = null,
    @ColumnInfo(name = "water_temp_celsius") val waterTempCelsius: Int? = null,
    val rating: Int? = null,
    val notes: String? = null,
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

/** The app's database: brews, their curves and the recipes, only on the phone (docs/adr/0003-offline-first.md). */
@Database(
    entities = [BrewEntity::class, BrewSampleEntity::class, RecipeEntity::class, RecipeStepEntity::class],
    version = 2,
)
abstract class BrewDatabase : RoomDatabase() {
    abstract fun brews(): BrewDao

    abstract fun recipes(): RecipeDao

    companion object {
        /** The file name must never change. */
        const val NAME = "caparao.db"

        fun open(context: Context): BrewDatabase = Room.databaseBuilder(context, BrewDatabase::class.java, NAME)
            .addMigrations(MIGRATION_1_2)
            .addCallback(SeedBuiltInRecipes)
            .build()
    }
}

/** A fresh install gets the built-in recipes; an upgrade gets them from [MIGRATION_1_2]. */
object SeedBuiltInRecipes : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) = BuiltInRecipes.insert(db, System.currentTimeMillis())
}

/** Recipes and brew metadata (docs/adr/0018-recipes.md, docs/adr/0021-brew-notes.md). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (column in listOf("recipe_id TEXT", "recipe_name TEXT", "coffee TEXT", "grind TEXT", "water_temp_celsius INTEGER", "rating INTEGER", "notes TEXT")) {
            db.execSQL("ALTER TABLE brews ADD COLUMN $column")
        }
        db.execSQL(CREATE_RECIPES)
        db.execSQL(CREATE_RECIPE_STEPS)
        BuiltInRecipes.insert(db, System.currentTimeMillis())
    }
}

/** As in app/schemas/…/2.json. */
private const val CREATE_RECIPES = "CREATE TABLE IF NOT EXISTS `recipes` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
    "`mode` TEXT NOT NULL, `method` TEXT NOT NULL, `dose_grams` REAL NOT NULL, `target_ratio` REAL NOT NULL, `grind` TEXT, " +
    "`water_temp_celsius` INTEGER, `notes` TEXT, `target_min_ms` INTEGER, `target_max_ms` INTEGER, " +
    "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"

private const val CREATE_RECIPE_STEPS = "CREATE TABLE IF NOT EXISTS `recipe_steps` (`recipe_id` TEXT NOT NULL, " +
    "`position` INTEGER NOT NULL, `kind` TEXT NOT NULL, `target_weight_grams` REAL, `target_at_ms` INTEGER NOT NULL, " +
    "PRIMARY KEY(`recipe_id`, `position`), FOREIGN KEY(`recipe_id`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
