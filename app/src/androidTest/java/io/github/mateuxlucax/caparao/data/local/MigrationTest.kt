package io.github.mateuxlucax.caparao.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mateuxlucax.caparao.model.BrewMode
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
    fun from1To2KeepsBrewsAndAddsTheBuiltInRecipes() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            for (sql in VERSION_1) db.execSQL(sql)
            db.execSQL("INSERT INTO brews VALUES ('a', 'ESPRESSO', 1000, 28000, 18.0, 36.0, 4000, 1.3, 2.4, 0, NULL)")
            db.execSQL("INSERT INTO brew_samples VALUES ('a', 0, 0.0, 0.0), ('a', 100, 0.4, 0.5)")
            db.version = 1
        }

        val db = Room.databaseBuilder(context, BrewDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        try {
            val brew = BrewRepository(db.brews()).get("a")!!
            assertEquals(36.0, brew.yieldGrams, 0.0)
            assertNull(brew.recipeId)
            assertNull(brew.rating)
            assertEquals(2, db.brews().samples("a").size)

            val recipes = RecipeRepository(db.recipes()).recipes.first()
            assertEquals(BuiltInRecipes.all(0).map { it.copy(createdAt = 0, updatedAt = 0) }.sortedBy { it.mode.name }, recipes.map { it.copy(createdAt = 0, updatedAt = 0) })
        } finally {
            db.close()
        }
    }

    @Test
    fun aFreshInstallGetsTheBuiltInRecipes() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, BrewDatabase::class.java).addCallback(SeedBuiltInRecipes).build()
        try {
            val recipes = RecipeRepository(db.recipes()).recipes.first()
            assertEquals(listOf(BuiltInRecipes.ESPRESSO_ID, BuiltInRecipes.V60_ID), recipes.map { it.id })
            val v60 = recipes.single { it.mode == BrewMode.FILTER }
            assertEquals(listOf(50.0, 100.0, 150.0, 300.0), v60.steps.map { it.targetWeightGrams })
            assertEquals(listOf(0L, 45_000L, 85_000L, 120_000L), v60.steps.map { it.targetAtMs })
        } finally {
            db.close()
        }
    }

    private companion object {
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
    }
}
