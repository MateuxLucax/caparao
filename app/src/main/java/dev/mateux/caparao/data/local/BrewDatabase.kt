package dev.mateux.caparao.data.local

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
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import java.math.BigDecimal
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
    /** No foreign key: deleting a recipe keeps its brews (#102). */
    @ColumnInfo(name = "recipe_id") val recipeId: String? = null,
    @ColumnInfo(name = "recipe_name") val recipeName: String? = null,
    val coffee: String? = null,
    val grind: String? = null,
    @ColumnInfo(name = "water_temp_celsius") val waterTempCelsius: Int? = null,
    val rating: Int? = null,
    val notes: String? = null,
    val taste: String? = null,
    @TypeConverters(TastingNotesConverter::class)
    @ColumnInfo(name = "tasting_notes_found", defaultValue = "'[]'") val tastingNotesFound: List<String> = emptyList(),
    @ColumnInfo(name = "rated_at") val ratedAt: Long? = null,
    /** The coffee bag this brew used; local only, never sent to the server (#288). No foreign key: deleting a bag unlinks its brews. */
    @ColumnInfo(name = "bag_id") val bagId: String? = null,
    /** Changes whenever a write alters what the server receives (#315). Local only, never uploaded. */
    @ColumnInfo(defaultValue = "0") val revision: Int = 0,
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

    /** The brews linked to a coffee bag (#288), newest first. */
    @Query("SELECT * FROM brews WHERE bag_id = :bagId ORDER BY started_at DESC")
    fun observeForBag(bagId: String): Flow<List<BrewEntity>>

    /** The newest saved brew for the home-screen widget. */
    @Query("SELECT * FROM brews ORDER BY started_at DESC, id DESC LIMIT 1")
    suspend fun latest(): BrewEntity?

    /** Every brew, oldest first. */
    @Query("SELECT * FROM brews ORDER BY started_at")
    suspend fun all(): List<BrewEntity>

    @Query("SELECT * FROM brews WHERE id = :id")
    suspend fun get(id: String): BrewEntity?

    /** One brew by id; null while it does not exist yet. */
    @Query("SELECT * FROM brews WHERE id = :id")
    fun observe(id: String): Flow<BrewEntity?>

    @Query("SELECT * FROM brew_samples WHERE brew_id = :brewId ORDER BY at_ms")
    suspend fun samples(brewId: String): List<BrewSampleEntity>

    @Query("SELECT * FROM brews WHERE synced_at IS NULL ORDER BY started_at")
    suspend fun unsynced(): List<BrewEntity>

    /** Brews that have not been rated yet, oldest first. */
    @Query("SELECT * FROM brews WHERE rated_at IS NULL ORDER BY started_at")
    fun notRatedYet(): Flow<List<BrewEntity>>

    /** Confirms [id] only if its revision is still [revision]; zero rows means it changed during the upload (#315). */
    @Query("UPDATE brews SET synced_at = :at WHERE id = :id AND revision = :revision")
    suspend fun markSynced(id: String, at: Long, revision: Int): Int

    @Query("DELETE FROM brews WHERE id = :id")
    suspend fun delete(id: String)

    /** The brew's coffee or recipe notes changed: its upload is stale and its revision grows (#105, #315). */
    @Query(
        "UPDATE brews SET coffee = :coffee, bag_id = :bagId, grind = :grind, water_temp_celsius = :waterTempCelsius, " +
            "synced_at = NULL, revision = revision + 1 WHERE id = :id",
    )
    suspend fun updateNotes(
        id: String,
        coffee: String?,
        bagId: String?,
        grind: String?,
        waterTempCelsius: Int?,
    )

    /** Saves a brew's rating locally and invalidates its previous upload. */
    @Query(
        "UPDATE brews SET rating = :rating, notes = :notes, taste = :taste, " +
            "tasting_notes_found = :tastingNotesFound, rated_at = COALESCE(rated_at, :ratedAt), synced_at = NULL, revision = revision + 1 " +
            "WHERE id = :id",
    )
    suspend fun saveRating(id: String, rating: Int?, notes: String?, taste: String?, tastingNotesFound: String, ratedAt: Long?)
}

/**
 * The app's database: brews, their curves and the recipes, only on the phone (#87).
 *
 * Version 9 is the compatibility baseline (#396). There are no migrations below it: a database older than the baseline
 * is not supported.
 */
@Database(
    entities = [
        BrewEntity::class,
        BrewSampleEntity::class,
        RecipeEntity::class,
        RecipeStepEntity::class,
        CoffeeBagEntity::class,
        BagRecipeAdjustmentEntity::class,
        BagRecipeAdjustmentHistoryEntity::class,
    ],
    version = 13,
)
abstract class BrewDatabase : RoomDatabase() {
    abstract fun brews(): BrewDao

    abstract fun recipes(): RecipeDao

    abstract fun coffeeBags(): CoffeeBagDao

    abstract fun bagRecipeAdjustments(): BagRecipeAdjustmentDao

    companion object {
        /** The file name must never change. */
        const val NAME = "caparao.db"

        fun open(context: Context): BrewDatabase = Room.databaseBuilder(context, BrewDatabase::class.java, NAME)
            .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
            .addCallback(SeedBuiltInRecipes)
            .build()

        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE coffee_bags ADD COLUMN tasting_notes TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bag_recipe_adjustments (" +
                        "bag_id TEXT NOT NULL, recipe_id TEXT NOT NULL, grind INTEGER, " +
                        "water_temp_celsius INTEGER, dose_grams REAL, tuned INTEGER NOT NULL, " +
                        "PRIMARY KEY(bag_id, recipe_id), " +
                        "FOREIGN KEY(bag_id) REFERENCES coffee_bags(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(recipe_id) REFERENCES recipes(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bag_recipe_adjustment_history (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, bag_id TEXT NOT NULL, recipe_id TEXT NOT NULL, " +
                        "changed_at INTEGER NOT NULL, old_grind INTEGER, new_grind INTEGER, " +
                        "old_water_temp_celsius INTEGER, new_water_temp_celsius INTEGER, " +
                        "old_dose_grams REAL, new_dose_grams REAL, old_tuned INTEGER NOT NULL, new_tuned INTEGER NOT NULL, " +
                        "reason TEXT NOT NULL, brew_id TEXT, " +
                        "FOREIGN KEY(bag_id, recipe_id) REFERENCES bag_recipe_adjustments(bag_id, recipe_id) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_bag_recipe_adjustments_recipe_id " +
                        "ON bag_recipe_adjustments (recipe_id)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_bag_recipe_adjustment_history_bag_id_recipe_id_changed_at " +
                        "ON bag_recipe_adjustment_history (bag_id, recipe_id, changed_at)",
                )
            }
        }

        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE brews ADD COLUMN taste TEXT")
                db.execSQL("ALTER TABLE brews ADD COLUMN tasting_notes_found TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE brews ADD COLUMN rated_at INTEGER")
                db.execSQL("UPDATE brews SET rated_at = started_at + duration_ms")
            }
        }

        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rebuild both linked tables so Room sees INTEGER affinity and recipe_steps keeps its foreign key.
                db.execSQL("ALTER TABLE recipe_steps RENAME TO recipe_steps_old")
                db.execSQL("ALTER TABLE recipes RENAME TO recipes_old")
                db.execSQL(
                    "CREATE TABLE recipes (id TEXT NOT NULL, name TEXT NOT NULL, mode TEXT NOT NULL, method TEXT NOT NULL, " +
                        "dose_grams REAL NOT NULL, target_ratio REAL NOT NULL, grind INTEGER, water_temp_celsius INTEGER, notes TEXT, " +
                        "target_min_ms INTEGER, target_max_ms INTEGER, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, " +
                        "PRIMARY KEY(id))",
                )
                db.execSQL(
                    "INSERT INTO recipes (id, name, mode, method, dose_grams, target_ratio, grind, water_temp_celsius, notes, " +
                        "target_min_ms, target_max_ms, created_at, updated_at) " +
                        "SELECT id, name, mode, method, dose_grams, target_ratio, " +
                        "grind, " +
                        "water_temp_celsius, notes, target_min_ms, target_max_ms, created_at, updated_at FROM recipes_old",
                )
                val recipeGrinds = mutableListOf<Pair<String, Int?>>()
                db.query("SELECT id, grind FROM recipes").use { cursor ->
                    while (cursor.moveToNext()) {
                        val grinderNumber = if (cursor.isNull(1)) null else cursor.getString(1).toGrinderNumber()
                        recipeGrinds += cursor.getString(0) to grinderNumber
                    }
                }
                recipeGrinds.forEach { (id, grinderNumber) ->
                    db.execSQL("UPDATE recipes SET grind = ? WHERE id = ?", arrayOf(grinderNumber, id))
                }
                db.execSQL(
                    "CREATE TABLE recipe_steps (recipe_id TEXT NOT NULL, position INTEGER NOT NULL, kind TEXT NOT NULL, " +
                        "target_weight_grams REAL, target_at_ms INTEGER NOT NULL, target_flow_grams_per_second REAL, " +
                        "PRIMARY KEY(recipe_id, position), " +
                        "FOREIGN KEY(recipe_id) REFERENCES recipes(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "INSERT INTO recipe_steps (recipe_id, position, kind, target_weight_grams, target_at_ms, target_flow_grams_per_second) " +
                        "SELECT recipe_id, position, kind, target_weight_grams, target_at_ms, target_flow_grams_per_second FROM recipe_steps_old",
                )
                db.execSQL("DROP TABLE recipe_steps_old")
                db.execSQL("DROP TABLE recipes_old")
            }
        }

        private fun String.toGrinderNumber(): Int? = try {
            BigDecimal(trim().replace(',', '.')).toBigIntegerExact().intValueExact()
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }
}

/** A fresh install gets current built-ins; the baseline has no upgrade path that seeds them again. */
object SeedBuiltInRecipes : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) = BuiltInRecipes.insert(db, System.currentTimeMillis())
}
