package dev.mateux.caparao.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CoffeeBagTastingNotesMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val schema = File("schemas/dev.mateux.caparao.data.local.BrewDatabase/9.json")

    @Before
    fun setUp() {
        context.deleteDatabase(BrewDatabase.NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(BrewDatabase.NAME)
    }

    @Test
    fun migration9To10AddsAnEmptyProfileAndKeepsExistingBags() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(BrewDatabase.NAME), null).use { db ->
            val database = JSONObject(schema.readText()).getJSONObject("database")
            val entities = database.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (indexInEntity in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(indexInEntity).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = database.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) db.execSQL(setup.getString(index))
            db.execSQL(
                "INSERT INTO coffee_bags (id, name, roaster, origin, roasted_at, weight_grams, notes, finished, created_at) " +
                    "VALUES ('bag', 'Caparaó', NULL, 'Catuaí, natural', NULL, 250.0, NULL, 0, 1)",
            )
            db.version = 9
        }

        val migrated = Room.databaseBuilder(context, BrewDatabase::class.java, BrewDatabase.NAME)
            .addMigrations(BrewDatabase.MIGRATION_9_10)
            .allowMainThreadQueries()
            .build()
        try {
            val bag = migrated.coffeeBags().get("bag")!!.bag
            assertEquals("Caparaó", bag.name)
            assertEquals("Catuaí, natural", bag.origin)
            assertEquals(emptyList<String>(), bag.tastingNotes)
            assertEquals(10, migrated.openHelper.readableDatabase.version)
        } finally {
            migrated.close()
        }
    }

    @Test
    fun coffeeDatabaseRemainsExcludedFromBackupsAndTransfers() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val rules = File("src/main/res/xml/data_extraction_rules.xml").readText()
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(rules.contains("<cloud-backup>") && rules.contains("<device-transfer>"))
        assertEquals(2, Regex("<exclude domain=\"database\" path=\"\\.\" />").findAll(rules).count())
        assertFalse(rules.contains("domain=\"database\" path=\"caparao.db\""))
    }
}
