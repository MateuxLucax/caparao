package dev.mateux.caparao.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BagRecipeAdjustmentMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        context.deleteDatabase(BrewDatabase.NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(BrewDatabase.NAME)
    }

    @Test
    fun migration10To11AddsAdjustmentTablesAndKeepsExistingData() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(BrewDatabase.NAME), null).use { db ->
            createSchemaFromJson(db, 10)
            db.execSQL(
                "INSERT INTO coffee_bags (id, name, weight_grams, finished, created_at, tasting_notes) " +
                    "VALUES ('bag', 'Caparaó', 250.0, 0, 1, '[]')",
            )
            db.execSQL(
                "INSERT INTO recipes (id, name, mode, method, dose_grams, target_ratio, created_at, updated_at) " +
                    "VALUES ('recipe', 'V60', 'FILTER', 'V60', 15.0, 16.0, 1, 1)",
            )
            db.version = 10
        }

        val migrated = Room.databaseBuilder(context, BrewDatabase::class.java, BrewDatabase.NAME)
            .addMigrations(BrewDatabase.MIGRATION_10_11)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(11, migrated.openHelper.readableDatabase.version)
            val tables = mutableSetOf<String>()
            migrated.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                while (cursor.moveToNext()) tables += cursor.getString(0)
            }
            assertTrue(tables.containsAll(setOf("bag_recipe_adjustments", "bag_recipe_adjustment_history")))
            assertEquals("Caparaó", migrated.coffeeBags().get("bag")!!.bag.name)
            assertEquals("V60", migrated.recipes().get("recipe")!!.name)
        } finally {
            migrated.close()
        }
    }
}
