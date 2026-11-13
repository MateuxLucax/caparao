package dev.mateux.caparao.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
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
            createSchemaFromJson(db, 9)
            db.execSQL(
                "INSERT INTO coffee_bags (id, name, roaster, origin, roasted_at, weight_grams, notes, finished, created_at) " +
                    "VALUES ('bag', 'Caparaó', NULL, 'Catuaí, natural', NULL, 250.0, NULL, 0, 1)",
            )
            db.version = 9
        }

        val migrated = Room.databaseBuilder(context, BrewDatabase::class.java, BrewDatabase.NAME)
            .addMigrations(BrewDatabase.MIGRATION_9_10, BrewDatabase.MIGRATION_10_11)
            .allowMainThreadQueries()
            .build()
        try {
            val bag = migrated.coffeeBags().get("bag")!!.bag
            assertEquals("Caparaó", bag.name)
            assertEquals("Catuaí, natural", bag.origin)
            assertEquals(emptyList<String>(), bag.tastingNotes)
            assertEquals(11, migrated.openHelper.readableDatabase.version)
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
