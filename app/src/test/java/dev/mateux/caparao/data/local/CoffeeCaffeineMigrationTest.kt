package dev.mateux.caparao.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CoffeeCaffeineMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setUp() { context.deleteDatabase(BrewDatabase.NAME) }
    @After fun tearDown() { context.deleteDatabase(BrewDatabase.NAME) }

    @Test
    fun migration14To15AddsNullableCaffeineOverrideAndKeepsBags() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(BrewDatabase.NAME), null).use { db ->
            createSchemaFromJson(db, 14)
            db.execSQL("INSERT INTO coffee_bags (id, name, weight_grams, finished, created_at, tasting_notes) VALUES ('bag', 'Arabica', 250, 0, 1, '[]')")
            db.version = 14
        }
        val migrated = Room.databaseBuilder(context, BrewDatabase::class.java, BrewDatabase.NAME)
            .addMigrations(BrewDatabase.MIGRATION_14_15, BrewDatabase.MIGRATION_15_16).allowMainThreadQueries().build()
        try {
            assertEquals("Arabica", migrated.coffeeBags().get("bag")!!.bag.name)
            assertEquals(null, migrated.coffeeBags().get("bag")!!.bag.caffeineMgPerGram)
            assertEquals(16, migrated.openHelper.readableDatabase.version)
        } finally { migrated.close() }
    }
}
