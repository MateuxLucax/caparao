package dev.mateux.caparao.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecipeGrindMigrationTest {
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
    fun migration12To13KeepsWholeNumbersAndClearsOtherGrindText() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(BrewDatabase.NAME), null).use { db ->
            createSchemaFromJson(db, 12)
            listOf("18", "18.0", " 18 ", "18,0", "18.5", "medium-coarse", null).forEachIndexed { index, grind ->
                val literal = grind?.let { "'$it'" } ?: "NULL"
                db.execSQL(
                    "INSERT INTO recipes (id, name, mode, method, dose_grams, target_ratio, grind, created_at, updated_at) " +
                        "VALUES ('recipe-$index', 'Recipe $index', 'FILTER', 'V60', 15.0, 16.0, $literal, 1, 1)",
                )
            }
            db.execSQL("INSERT INTO recipe_steps (recipe_id, position, kind, target_at_ms) VALUES ('recipe-0', 0, 'BLOOM', 0)")
            db.version = 12
        }

        val migrated = Room.databaseBuilder(context, BrewDatabase::class.java, BrewDatabase.NAME)
            .addMigrations(BrewDatabase.MIGRATION_12_13)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(18, migrated.recipes().get("recipe-0")!!.grind)
            assertEquals(18, migrated.recipes().get("recipe-1")!!.grind)
            assertEquals(18, migrated.recipes().get("recipe-2")!!.grind)
            assertEquals(18, migrated.recipes().get("recipe-3")!!.grind)
            assertEquals(null, migrated.recipes().get("recipe-4")!!.grind)
            assertEquals(null, migrated.recipes().get("recipe-5")!!.grind)
            assertEquals(null, migrated.recipes().get("recipe-6")!!.grind)
            assertEquals(1, migrated.recipes().steps("recipe-0").size)
            assertEquals(13, migrated.openHelper.readableDatabase.version)
        } finally {
            migrated.close()
        }
    }
}
