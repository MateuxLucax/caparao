package io.github.mateuxlucax.caparao.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.StepKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Each schema version upgrades to the current one and keeps the brews. The old database is written with the SQL from
 * app/schemas; Room checks every table against the current schema when it opens it after the migration.
 * (room-testing's MigrationTestHelper needs a newer kotlinx-serialization than the app ships.)
 */
class MigrationTest {
    private val name = "migration-test.db"
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun from1KeepsBrewsAndAddsTheBuiltInRecipes() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            for (sql in VERSION_1) db.execSQL(sql)
            db.execSQL("INSERT INTO brews VALUES ('a', 'ESPRESSO', 1000, 28000, 18.0, 36.0, 4000, 1.3, 2.4, 0, NULL)")
            db.execSQL("INSERT INTO brew_samples VALUES ('a', 0, 0.0, 0.0), ('a', 100, 0.4, 0.5)")
            db.version = 1
        }

        val db = Room.databaseBuilder(context, BrewDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .build()
        try {
            val brew = BrewRepository(db.brews()).get("a")!!
            assertEquals(36.0, brew.yieldGrams, 0.0)
            assertNull(brew.recipeId)
            assertNull(brew.rating)
            assertEquals(2, db.brews().samples("a").size)

            val recipes = RecipeRepository(db.recipes()).recipes.first()
            assertEquals(BuiltInRecipes.all(0).map { it.copy(createdAt = 0, updatedAt = 0) }.sortedWith(compareBy({ it.mode.name }, { it.name })), recipes.map { it.copy(createdAt = 0, updatedAt = 0) })
        } finally {
            db.close()
        }
    }

    @Test
    fun from4AddsOnlyFrenchPressAndKeepsDeletedBuiltInsAndSavedDataUntouched() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            for (sql in VERSION_4) db.execSQL(sql)
            db.execSQL(
                "INSERT INTO brews (id, mode, started_at, duration_ms, dose_grams, yield_grams, time_to_first_drop_ms, " +
                    "flow_avg, flow_peak, interrupted, synced_at) VALUES ('existing', 'FILTER', 1000, 60000, 15.0, 250.0, NULL, 1.3, 4.8, 0, NULL)",
            )
            db.execSQL("INSERT INTO brew_samples VALUES ('existing', 0, 0.0, 0.0)")
            db.execSQL(
                "INSERT INTO recipes VALUES ('custom', 'Custom filter', 'FILTER', 'V60', 15.0, 16.0, NULL, NULL, NULL, NULL, NULL, 100, 200)",
            )
            db.execSQL("INSERT INTO recipe_steps VALUES ('custom', 0, 'BLOOM', 40.0, 0)")
            db.execSQL(
                "INSERT INTO recipes VALUES ('${BuiltInRecipes.V60_ID}', 'V60 One and Done', 'FILTER', 'V60', 15.0, 16.7, NULL, NULL, NULL, NULL, NULL, 100, 200)",
            )
            db.execSQL("INSERT INTO recipe_steps VALUES ('${BuiltInRecipes.V60_ID}', 0, 'BLOOM', 45.0, 0)")
            db.execSQL("DELETE FROM recipe_steps WHERE recipe_id = '${BuiltInRecipes.V60_ID}'")
            db.execSQL("DELETE FROM recipes WHERE id = '${BuiltInRecipes.V60_ID}'")
            db.version = 4
        }

        val db = Room.databaseBuilder(context, BrewDatabase::class.java, name)
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .build()
        try {
            val brew = BrewRepository(db.brews()).get("existing")!!
            assertEquals(250.0, brew.yieldGrams, 0.0)
            assertEquals(1, db.brews().samples("existing").size)

            val recipes = RecipeRepository(db.recipes()).recipes.first()
            assertEquals(setOf("custom", BuiltInRecipes.FRENCH_PRESS_ID), recipes.map { it.id }.toSet())
            assertEquals("Custom filter", recipes.single { it.id == "custom" }.name)
            assertEquals(listOf(40.0), recipes.single { it.id == "custom" }.steps.map { it.targetWeightGrams })
            assertEquals(BrewMode.FRENCH_PRESS, recipes.single { it.id == BuiltInRecipes.FRENCH_PRESS_ID }.mode)
        } finally {
            db.close()
        }
    }

    @Test
    fun from5AddsOptionalFlowTargetsAndSetsUneditedBigBrewBloom() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            for (sql in VERSION_4) db.execSQL(sql)
            db.execSQL(
                "INSERT INTO recipes VALUES ('${BuiltInRecipes.V60_BIG_ID}', 'V60 Big Brew', 'FILTER', 'V60', 30.0, 15.0, NULL, NULL, NULL, NULL, NULL, 100, 100)",
            )
            db.execSQL("INSERT INTO recipe_steps VALUES ('${BuiltInRecipes.V60_BIG_ID}', 0, 'BLOOM', 90.0, 0)")
            db.execSQL("INSERT INTO recipe_steps VALUES ('${BuiltInRecipes.V60_BIG_ID}', 1, 'BLOOM', 155.0, 20000)")
            db.execSQL("INSERT INTO recipes VALUES ('custom', 'Custom', 'FILTER', 'V60', 15.0, 16.0, NULL, NULL, NULL, NULL, NULL, 100, 200)")
            db.execSQL("INSERT INTO recipe_steps VALUES ('custom', 0, 'BLOOM', 45.0, 0)")
            db.version = 5
        }

        val db = Room.databaseBuilder(context, BrewDatabase::class.java, name)
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .build()
        try {
            val recipes = RecipeRepository(db.recipes()).recipes.first()
            val bigBrew = recipes.single { it.id == BuiltInRecipes.V60_BIG_ID }
            assertEquals(listOf(5.0, null), bigBrew.steps.map { it.targetFlowGramsPerSecond })
            assertNull(recipes.single { it.id == "custom" }.steps.single().targetFlowGramsPerSecond)
        } finally {
            db.close()
        }
    }

    @Test
    fun from5LeavesEditedBigBrewFlowTargetUnset() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            for (sql in VERSION_4) db.execSQL(sql)
            db.execSQL(
                "INSERT INTO recipes VALUES ('${BuiltInRecipes.V60_BIG_ID}', 'V60 Big Brew', 'FILTER', 'V60', 30.0, 15.0, NULL, NULL, NULL, NULL, NULL, 100, 200)",
            )
            db.execSQL("INSERT INTO recipe_steps VALUES ('${BuiltInRecipes.V60_BIG_ID}', 0, 'BLOOM', 90.0, 0)")
            db.version = 5
        }

        val db = Room.databaseBuilder(context, BrewDatabase::class.java, name)
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .build()
        try {
            val bigBrew = RecipeRepository(db.recipes()).recipes.first().single { it.id == BuiltInRecipes.V60_BIG_ID }
            assertNull(bigBrew.steps.single().targetFlowGramsPerSecond)
        } finally {
            db.close()
        }
    }

    @Test
    fun from6AddsDrainTimesToUneditedFilterBuiltInsAndKeepsEditedRecipes() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            for (sql in VERSION_4) db.execSQL(sql)
            db.execSQL("ALTER TABLE recipe_steps ADD COLUMN target_flow_grams_per_second REAL")
            db.execSQL("UPDATE room_master_table SET identity_hash = '36146e16c2fb9413f32c4add7004ed99' WHERE id = 42")
            insertRecipe(db, BuiltInRecipes.V60_ID, "V60 One and Done", createdAt = 100, updatedAt = 200, minMs = 91_000, maxMs = 99_000)
            insertRecipe(db, BuiltInRecipes.V60_BIG_ID, "V60 Big Brew")
            insertRecipe(db, BuiltInRecipes.MELITTA_ID, "Melitta 102")
            insertRecipe(db, BuiltInRecipes.V60_FOUR_SIX_ID, "V60 4:6 10 pours")
            insertRecipe(db, BuiltInRecipes.MELITTA_LITER_ID, "Melitta 103 1 L")
            insertRecipe(db, BuiltInRecipes.FRENCH_PRESS_ID, "French press", mode = "FRENCH_PRESS")
            db.version = 6
        }

        val db = Room.databaseBuilder(context, BrewDatabase::class.java, name)
            .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
            .build()
        try {
            val recipes = RecipeRepository(db.recipes()).recipes.first().associateBy { it.id }
            assertEquals(91_000L to 99_000L, recipes.getValue(BuiltInRecipes.V60_ID).let { it.targetMinMs!! to it.targetMaxMs!! })
            assertEquals(120_000L to 150_000L, recipes.getValue(BuiltInRecipes.V60_BIG_ID).let { it.targetMinMs!! to it.targetMaxMs!! })
            assertEquals(160_000L to 170_000L, recipes.getValue(BuiltInRecipes.MELITTA_ID).let { it.targetMinMs!! to it.targetMaxMs!! })
            assertEquals(180_000L to 210_000L, recipes.getValue(BuiltInRecipes.V60_FOUR_SIX_ID).let { it.targetMinMs!! to it.targetMaxMs!! })
            assertEquals(255_000L to 285_000L, recipes.getValue(BuiltInRecipes.MELITTA_LITER_ID).let { it.targetMinMs!! to it.targetMaxMs!! })
            assertNull(recipes.getValue(BuiltInRecipes.FRENCH_PRESS_ID).targetMinMs)
            assertNull(recipes.getValue(BuiltInRecipes.FRENCH_PRESS_ID).targetMaxMs)
        } finally {
            db.close()
        }
    }

    @Test
    fun aFreshInstallGetsTheBuiltInRecipes() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, BrewDatabase::class.java).addCallback(SeedBuiltInRecipes).build()
        try {
            val recipes = RecipeRepository(db.recipes()).recipes.first()
            assertEquals(7, recipes.size)
            val v60 = recipes.single { it.id == BuiltInRecipes.V60_ID }
            assertEquals(listOf(45.0, 90.0, 250.0), v60.steps.map { it.targetWeightGrams })
            assertEquals(listOf(0L, 30_000L, 60_000L), v60.steps.map { it.targetAtMs })
            assertEquals(120_000L to 150_000L, v60.targetMinMs!! to v60.targetMaxMs!!)
            val bigBrew = recipes.single { it.id == BuiltInRecipes.V60_BIG_ID }
            assertEquals(listOf(5.0, null, null, null), bigBrew.steps.map { it.targetFlowGramsPerSecond })
            assertEquals(120_000L to 150_000L, bigBrew.targetMinMs!! to bigBrew.targetMaxMs!!)
            assertEquals(
                emptyList<Double>(),
                recipes.filterNot { it.id == BuiltInRecipes.V60_BIG_ID }.flatMap { it.steps }
                    .mapNotNull { it.targetFlowGramsPerSecond },
            )
            val melitta = recipes.single { it.id == BuiltInRecipes.MELITTA_ID }
            assertEquals(listOf(40.0, 300.0), melitta.steps.map { it.targetWeightGrams })
            val frenchPress = recipes.single { it.id == BuiltInRecipes.FRENCH_PRESS_ID }
            assertEquals(BrewMode.FRENCH_PRESS, frenchPress.mode)
            assertNull(frenchPress.targetMinMs)
            assertNull(frenchPress.targetMaxMs)
            assertEquals(20.0, frenchPress.doseGrams, 0.0)
            assertEquals(15.0, frenchPress.targetRatio, 0.0)
            assertEquals(listOf(StepKind.BLOOM, StepKind.WAIT, StepKind.POUR, StepKind.PRESS), frenchPress.steps.map { it.kind })
            assertEquals(listOf(100.0, null, 300.0, null), frenchPress.steps.map { it.targetWeightGrams })
            assertEquals(listOf(0L, 0L, 60_000L, 240_000L), frenchPress.steps.map { it.targetAtMs })
        } finally {
            db.close()
        }
    }

    private companion object {
        fun insertRecipe(
            db: SQLiteDatabase,
            id: String,
            name: String,
            mode: String = "FILTER",
            createdAt: Long = 100,
            updatedAt: Long = createdAt,
            minMs: Long? = null,
            maxMs: Long? = null,
        ) {
            db.execSQL(
                "INSERT INTO recipes (id, name, mode, method, dose_grams, target_ratio, target_min_ms, target_max_ms, created_at, updated_at) " +
                    "VALUES (?, ?, ?, 'V60', 15.0, 16.0, ?, ?, ?, ?)",
                arrayOf<Any?>(id, name, mode, minMs, maxMs, createdAt, updatedAt),
            )
        }

        /** app/schemas/…/1.json: the tables and Room's bookkeeping. */
        val VERSION_1 = listOf(
            "CREATE TABLE IF NOT EXISTS `brews` (`id` TEXT NOT NULL, `mode` TEXT NOT NULL, `started_at` INTEGER NOT NULL, " +
                "`duration_ms` INTEGER NOT NULL, `dose_grams` REAL NOT NULL, `yield_grams` REAL NOT NULL, " +
                "`time_to_first_drop_ms` INTEGER, `flow_avg` REAL NOT NULL, `flow_peak` REAL NOT NULL, " +
                "`interrupted` INTEGER NOT NULL, `synced_at` INTEGER, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_brews_started_at` ON `brews` (`started_at`)",
            "CREATE TABLE IF NOT EXISTS `brew_samples` (`brew_id` TEXT NOT NULL, `at_ms` INTEGER NOT NULL, " +
                "`weight_grams` REAL NOT NULL, `flow` REAL NOT NULL, PRIMARY KEY(`brew_id`, `at_ms`), " +
                "FOREIGN KEY(`brew_id`) REFERENCES `brews`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '1151d1cc76e6803e8b6bd2ebabb3d270')",
        )

        /** app/schemas/…/4.json: the current schema before French press (no French press data or columns). */
        val VERSION_4 = listOf(
            "CREATE TABLE IF NOT EXISTS `brews` (`id` TEXT NOT NULL, `mode` TEXT NOT NULL, `started_at` INTEGER NOT NULL, " +
                "`duration_ms` INTEGER NOT NULL, `dose_grams` REAL NOT NULL, `yield_grams` REAL NOT NULL, " +
                "`time_to_first_drop_ms` INTEGER, `flow_avg` REAL NOT NULL, `flow_peak` REAL NOT NULL, `interrupted` INTEGER NOT NULL, " +
                "`synced_at` INTEGER, `recipe_id` TEXT, `recipe_name` TEXT, `coffee` TEXT, `grind` TEXT, " +
                "`water_temp_celsius` INTEGER, `rating` INTEGER, `notes` TEXT, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_brews_started_at` ON `brews` (`started_at`)",
            "CREATE TABLE IF NOT EXISTS `brew_samples` (`brew_id` TEXT NOT NULL, `at_ms` INTEGER NOT NULL, `weight_grams` REAL NOT NULL, " +
                "`flow` REAL NOT NULL, PRIMARY KEY(`brew_id`, `at_ms`), FOREIGN KEY(`brew_id`) REFERENCES `brews`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `recipes` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `mode` TEXT NOT NULL, `method` TEXT NOT NULL, " +
                "`dose_grams` REAL NOT NULL, `target_ratio` REAL NOT NULL, `grind` TEXT, `water_temp_celsius` INTEGER, `notes` TEXT, " +
                "`target_min_ms` INTEGER, `target_max_ms` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `recipe_steps` (`recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                "`target_weight_grams` REAL, `target_at_ms` INTEGER NOT NULL, PRIMARY KEY(`recipe_id`, `position`), " +
                "FOREIGN KEY(`recipe_id`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `coffee_bags` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `roaster` TEXT, `origin` TEXT, " +
                "`roasted_at` INTEGER, `weight_grams` REAL NOT NULL, `notes` TEXT, `finished` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '3143a4074eb93ae955378051a2509310')",
        )
    }
}
