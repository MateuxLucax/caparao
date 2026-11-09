package io.github.mateuxlucax.caparao.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.mateuxlucax.caparao.model.CoffeeBag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * 7 → 8 (#288): the v7 file is built from app/schemas/…/7.json, so the test starts from the exact schema users have.
 * Only the first matching open bag takes a brew, and only brews from after the bag was added.
 */
@RunWith(RobolectricTestRunner::class)
class CoffeeBagMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "coffee-bag-migration-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    @Test
    fun linksEachBrewOnceByCoffeeNameToTheOpenBagAddedBeforeIt() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            createVersion7(db)
            // Bags: an older finished "Caparaó" and a newer open one; a "Mantiqueira" bag with no brews.
            db.execSQL("INSERT INTO coffee_bags VALUES ('finished', 'Caparaó', NULL, NULL, NULL, 250.0, NULL, 1, 100)")
            db.execSQL("INSERT INTO coffee_bags VALUES ('open', 'Caparaó', NULL, NULL, NULL, 250.0, NULL, 0, 200)")
            db.execSQL("INSERT INTO coffee_bags VALUES ('mantiqueira', 'Mantiqueira', NULL, NULL, NULL, 500.0, NULL, 0, 300)")
            // Named brew after both bags: the open bag, not the finished one.
            brew(db, "after-open", startedAt = 1_000, coffee = "'Caparaó'", dose = 15.0, rating = 4)
            // Named brew before either bag existed: no match, so it stays unlinked.
            brew(db, "before-open", startedAt = 50, coffee = "'Caparaó'", dose = 12.0, rating = null)
            // Brew with a coffee name that no bag has: unlinked.
            brew(db, "other", startedAt = 2_000, coffee = "'Geisha'", dose = 18.0, rating = 5)
            // Brew without a coffee note: unlinked.
            brew(db, "no-coffee", startedAt = 3_000, coffee = "NULL", dose = 20.0, rating = null)
            db.version = 7
        }

        val db = Room.databaseBuilder(context, BrewDatabase::class.java, name).addMigrations(MIGRATION_7_8, MIGRATION_8_9).build()
        try {
            val brews = db.brews()
            assertEquals("open", brews.get("after-open")!!.bagId)
            assertNull(brews.get("before-open")!!.bagId)
            assertNull(brews.get("other")!!.bagId)
            assertNull(brews.get("no-coffee")!!.bagId)
            assertEquals("Caparaó", brews.get("after-open")!!.coffee)

            val bags = CoffeeBagRepository(db.coffeeBags()).bags.first().associateBy { it.id }
            assertEquals(15.0, bags.getValue("open").usedGrams, 0.0)
            assertEquals(1, bags.getValue("open").brewCount)
            assertEquals(0.0, bags.getValue("finished").usedGrams, 0.0)
            assertEquals(0, bags.getValue("mantiqueira").brewCount)
            assertNull(bags.getValue("mantiqueira").lastBrewedAt)
        } finally {
            db.close()
        }
    }

    /** Room's own v7 tables, indices and bookkeeping rows, exactly as app/schemas/…/7.json describes them. */
    private fun createVersion7(db: SQLiteDatabase) {
        val schema = JSONObject(File("schemas/io.github.mateuxlucax.caparao.data.local.BrewDatabase/7.json").readText())
            .getJSONObject("database")
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            // Room leaves "indices" out of the JSON for tables without any.
            val indices = entity.optJSONArray("indices") ?: JSONArray()
            for (j in 0 until indices.length()) {
                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
    }

    private fun brew(db: SQLiteDatabase, id: String, startedAt: Long, coffee: String, dose: Double, rating: Int?) {
        db.execSQL(
            "INSERT INTO brews (id, mode, started_at, duration_ms, dose_grams, yield_grams, time_to_first_drop_ms, flow_avg, " +
                "flow_peak, interrupted, synced_at, coffee, rating) VALUES ('$id', 'FILTER', $startedAt, 60000, $dose, 250.0, NULL, " +
                "1.3, 4.8, 0, NULL, $coffee, ${rating ?: "NULL"})",
        )
    }
}
