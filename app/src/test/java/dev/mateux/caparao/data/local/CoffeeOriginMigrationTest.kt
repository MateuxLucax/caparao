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
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
class CoffeeOriginMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setUp() { context.deleteDatabase(BrewDatabase.NAME) }
    @After fun tearDown() { context.deleteDatabase(BrewDatabase.NAME) }

    @Test
    fun migration15To16MovesLegacyOriginIntoRegionAndDropsOldColumn() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(BrewDatabase.NAME), null).use { db ->
            createSchemaFromJson(db, 15)
            db.execSQL("INSERT INTO coffee_bags (id, name, origin, weight_grams, finished, created_at) VALUES ('bag', 'Coffee', 'Alta Mogiana - Topázio', 250, 0, 1)")
            db.version = 15
        }

        val migrated = Room.databaseBuilder(context, BrewDatabase::class.java, BrewDatabase.NAME)
            .addMigrations(BrewDatabase.MIGRATION_15_16).allowMainThreadQueries().build()
        try {
            val bag = migrated.coffeeBags().get("bag")!!.bag
            assertEquals("Alta Mogiana - Topázio", bag.region)
            val columns = mutableSetOf<String>()
            migrated.openHelper.readableDatabase.query("PRAGMA table_info(coffee_bags)").use { cursor ->
                while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
            assertEquals(false, "origin" in columns)
        } finally { migrated.close() }
    }
}
