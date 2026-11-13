package dev.mateux.caparao.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Version 9 is the compatibility baseline (#396): a phone already on it opens with its brews, curves, recipes and bags.
 */
@RunWith(RobolectricTestRunner::class)
class BaselineSchemaTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    // Kept in step with BrewDatabase's @Database version; the schema file name must match it.
    private val version = 9
    private val schemaDir = File("schemas/dev.mateux.caparao.data.local.BrewDatabase")

    @Before
    fun setUp() {
        context.deleteDatabase(BrewDatabase.NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(BrewDatabase.NAME)
    }

    @Test
    fun baselineAndCurrentSchemasAreExported() {
        assertEquals(9, version)
        assertEquals(setOf("9.json", "10.json"), schemaDir.list()!!.toSet())
        assertEquals(version, JSONObject(File(schemaDir, "$version.json").readText()).getJSONObject("database").getInt("version"))
        assertEquals(10, JSONObject(File(schemaDir, "10.json").readText()).getJSONObject("database").getInt("version"))
    }

    @Test
    fun aPhoneAlreadyOnTheBaselineKeepsEverythingAcrossMigration() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(BrewDatabase.NAME), null).use { db ->
            createBaseline(db)
            db.execSQL(
                "INSERT INTO brews (id, mode, started_at, duration_ms, dose_grams, yield_grams, flow_avg, flow_peak, " +
                    "interrupted, bag_id) VALUES ('brew', 'FILTER', 1000, 60000, 15.0, 250.0, 1.3, 4.8, 0, 'bag')",
            )
            db.execSQL("INSERT INTO brew_samples VALUES ('brew', 0, 0.0, 0.0)")
            db.execSQL(
                "INSERT INTO recipes (id, name, mode, method, dose_grams, target_ratio, created_at, updated_at) " +
                    "VALUES ('recipe', 'V60', 'FILTER', 'V60', 15.0, 16.0, 1, 1)",
            )
            db.execSQL("INSERT INTO recipe_steps VALUES ('recipe', 0, 'POUR', 90.0, 0, NULL)")
            db.execSQL("INSERT INTO coffee_bags VALUES ('bag', 'Caparaó', NULL, NULL, NULL, 250.0, NULL, 0, 1)")
            db.version = version
        }

        val db = BrewDatabase.open(context)
        try {
            assertEquals(250.0, db.brews().get("brew")!!.yieldGrams, 0.0)
            assertEquals("bag", db.brews().get("brew")!!.bagId)
            assertEquals(1, db.brews().samples("brew").size)
            assertNotNull(db.recipes().get("recipe"))
            assertEquals(1, db.recipes().steps("recipe").size)
            assertNotNull(db.coffeeBags().get("bag"))

            // Independent of the exported JSON: Room's open database has every model table and column.
            val sqlite = db.openHelper.readableDatabase
            val tables = mutableSetOf<String>()
            sqlite.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                while (cursor.moveToNext()) tables += cursor.getString(0)
            }
            assertTrue(tables.containsAll(setOf("brews", "brew_samples", "recipes", "recipe_steps", "coffee_bags")))
            val columns = mutableSetOf<String>()
            sqlite.query("PRAGMA table_info(brews)").use { cursor ->
                while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
            assertTrue(columns.containsAll(setOf("bag_id", "revision", "recipe_id", "notes", "water_temp_celsius")))
        } finally {
            db.close()
        }
    }

    /** Room's own v9 tables, indices and bookkeeping rows, exactly as app/schemas/…/9.json describes them. */
    private fun createBaseline(db: SQLiteDatabase) {
        val schema = JSONObject(File(schemaDir, "$version.json").readText()).getJSONObject("database")
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: JSONArray()
            for (j in 0 until indices.length()) {
                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
    }
}
