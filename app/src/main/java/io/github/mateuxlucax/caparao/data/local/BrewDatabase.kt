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
    /** No foreign key: deleting a recipe keeps its brews (#102). */
    @ColumnInfo(name = "recipe_id") val recipeId: String? = null,
    @ColumnInfo(name = "recipe_name") val recipeName: String? = null,
    val coffee: String? = null,
    val grind: String? = null,
    @ColumnInfo(name = "water_temp_celsius") val waterTempCelsius: Int? = null,
    val rating: Int? = null,
    val notes: String? = null,
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

    /** Every brew, oldest first, for the export (#76). */
    @Query("SELECT * FROM brews ORDER BY started_at")
    suspend fun all(): List<BrewEntity>

    /** Every sample, ordered so each brew's curve reads chronologically when grouped. */
    @Query("SELECT * FROM brew_samples ORDER BY brew_id, at_ms")
    suspend fun allSamples(): List<BrewSampleEntity>

    @Query("SELECT * FROM brews WHERE id = :id")
    suspend fun get(id: String): BrewEntity?

    @Query("SELECT * FROM brew_samples WHERE brew_id = :brewId ORDER BY at_ms")
    suspend fun samples(brewId: String): List<BrewSampleEntity>

    @Query("SELECT * FROM brews WHERE synced_at IS NULL ORDER BY started_at")
    suspend fun unsynced(): List<BrewEntity>

    /** Confirms [id] only if its revision is still [revision]; zero rows means it changed during the upload (#315). */
    @Query("UPDATE brews SET synced_at = :at WHERE id = :id AND revision = :revision")
    suspend fun markSynced(id: String, at: Long, revision: Int): Int

    @Query("DELETE FROM brews WHERE id = :id")
    suspend fun delete(id: String)

    /** The notes changed: the server's copy is stale, so the brew is unsynced again and its revision grows (#105, #315). */
    @Query(
        "UPDATE brews SET coffee = :coffee, bag_id = :bagId, grind = :grind, water_temp_celsius = :waterTempCelsius, rating = :rating, " +
            "notes = :notes, synced_at = NULL, revision = revision + 1 WHERE id = :id",
    )
    suspend fun updateNotes(id: String, coffee: String?, bagId: String?, grind: String?, waterTempCelsius: Int?, rating: Int?, notes: String?)
}

/** The app's database: brews, their curves and the recipes, only on the phone (#87). */
@Database(
    entities = [BrewEntity::class, BrewSampleEntity::class, RecipeEntity::class, RecipeStepEntity::class, CoffeeBagEntity::class],
    version = 9,
)
abstract class BrewDatabase : RoomDatabase() {
    abstract fun brews(): BrewDao

    abstract fun recipes(): RecipeDao

    abstract fun coffeeBags(): CoffeeBagDao

    companion object {
        /** The file name must never change. */
        const val NAME = "caparao.db"

        fun open(context: Context): BrewDatabase = Room.databaseBuilder(context, BrewDatabase::class.java, NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            .addCallback(SeedBuiltInRecipes)
            .build()
    }
}

/** A fresh install gets current built-ins; upgrades get each set through the migration that introduced it. */
object SeedBuiltInRecipes : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) = BuiltInRecipes.insert(db, System.currentTimeMillis())
}

/** Recipes and brew metadata (#102, #105). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (column in listOf("recipe_id TEXT", "recipe_name TEXT", "coffee TEXT", "grind TEXT", "water_temp_celsius INTEGER", "rating INTEGER", "notes TEXT")) {
            db.execSQL("ALTER TABLE brews ADD COLUMN $column")
        }
        db.execSQL(CREATE_RECIPES)
        db.execSQL(CREATE_RECIPE_STEPS)
        BuiltInRecipes.insert(db, BuiltInRecipes.beforeFrenchPress(System.currentTimeMillis()))
    }
}

/** A new set of built-in recipes: the old ones the user never edited are replaced, edited ones stay. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val ids = BuiltInRecipes.FIRST_IDS.joinToString(",") { "'$it'" }
        val unedited = "SELECT id FROM recipes WHERE id IN ($ids) AND created_at = updated_at"
        db.execSQL("DELETE FROM recipe_steps WHERE recipe_id IN ($unedited)")
        db.execSQL("DELETE FROM recipes WHERE id IN ($unedited)")
        BuiltInRecipes.insert(db, BuiltInRecipes.beforeFrenchPress(System.currentTimeMillis()))
    }
}

/** Coffee bags (#110). */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) = db.execSQL(CREATE_COFFEE_BAGS)
}

/** French press (#125): add only its recipe, leaving earlier built-ins the user deleted untouched. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        BuiltInRecipes.insert(db, listOf(BuiltInRecipes.frenchPress(System.currentTimeMillis())))
    }
}

/** Optional pour-rate targets (#207). */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE recipe_steps ADD COLUMN target_flow_grams_per_second REAL")
        db.execSQL(
            "UPDATE recipe_steps SET target_flow_grams_per_second = 5.0 " +
                "WHERE recipe_id = '${BuiltInRecipes.V60_BIG_ID}' AND position = 0 AND kind = 'BLOOM' " +
                "AND recipe_id IN (SELECT id FROM recipes WHERE created_at = updated_at)",
        )
    }
}

/** Ideal total drain times for untouched filter built-ins (#206). */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val defaults = listOf(
            BuiltInRecipes.V60_ID to (120_000L to 150_000L),
            BuiltInRecipes.V60_BIG_ID to (120_000L to 150_000L),
            BuiltInRecipes.MELITTA_ID to (160_000L to 170_000L),
            BuiltInRecipes.V60_FOUR_SIX_ID to (180_000L to 210_000L),
            BuiltInRecipes.MELITTA_LITER_ID to (255_000L to 285_000L),
        )
        for ((id, range) in defaults) {
            db.execSQL(
                "UPDATE recipes SET target_min_ms = ${range.first}, target_max_ms = ${range.second} " +
                    "WHERE id = '$id' AND mode = 'FILTER' AND created_at = updated_at",
            )
        }
    }
}

/** Brews link to the bag they used (#288). Existing brews are matched once, by coffee name, as the used grams always were. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE brews ADD COLUMN bag_id TEXT")
        db.execSQL(
            "UPDATE brews SET bag_id = (SELECT coffee_bags.id FROM coffee_bags WHERE coffee_bags.name = brews.coffee " +
                "AND brews.started_at >= coffee_bags.created_at ORDER BY coffee_bags.finished, coffee_bags.created_at DESC LIMIT 1) " +
                "WHERE brews.coffee IS NOT NULL",
        )
    }
}

/** Upload revisions, so a brew edited while a job is in flight is sent again (#315). */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE brews ADD COLUMN revision INTEGER NOT NULL DEFAULT 0")
    }
}

/** As in app/schemas/…/4.json. */
private const val CREATE_COFFEE_BAGS = "CREATE TABLE IF NOT EXISTS `coffee_bags` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
    "`roaster` TEXT, `origin` TEXT, `roasted_at` INTEGER, `weight_grams` REAL NOT NULL, `notes` TEXT, " +
    "`finished` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"

/** As in app/schemas/…/2.json. */
private const val CREATE_RECIPES = "CREATE TABLE IF NOT EXISTS `recipes` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
    "`mode` TEXT NOT NULL, `method` TEXT NOT NULL, `dose_grams` REAL NOT NULL, `target_ratio` REAL NOT NULL, `grind` TEXT, " +
    "`water_temp_celsius` INTEGER, `notes` TEXT, `target_min_ms` INTEGER, `target_max_ms` INTEGER, " +
    "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"

private const val CREATE_RECIPE_STEPS = "CREATE TABLE IF NOT EXISTS `recipe_steps` (`recipe_id` TEXT NOT NULL, " +
    "`position` INTEGER NOT NULL, `kind` TEXT NOT NULL, `target_weight_grams` REAL, `target_at_ms` INTEGER NOT NULL, " +
    "PRIMARY KEY(`recipe_id`, `position`), FOREIGN KEY(`recipe_id`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
