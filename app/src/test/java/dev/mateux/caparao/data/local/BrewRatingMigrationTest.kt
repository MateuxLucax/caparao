package dev.mateux.caparao.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BrewRatingMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val schema = File("schemas/dev.mateux.caparao.data.local.BrewDatabase/11.json")

    @Before
    fun setUp() {
        context.deleteDatabase(BrewDatabase.NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(BrewDatabase.NAME)
    }

    @Test
    fun migration11To12AddsRatingDataAndMarksAllExistingBrewsRated() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(BrewDatabase.NAME), null).use { db ->
            createVersion11(db)
            db.execSQL(
                "INSERT INTO brews (id, mode, started_at, duration_ms, dose_grams, yield_grams, flow_avg, flow_peak, interrupted, rating) " +
                    "VALUES ('starred', 'FILTER', 1000, 60000, 15.0, 250.0, 1.3, 4.8, 0, 4)",
            )
            db.execSQL(
                "INSERT INTO brews (id, mode, started_at, duration_ms, dose_grams, yield_grams, flow_avg, flow_peak, interrupted, notes) " +
                    "VALUES ('noted', 'FILTER', 2000, 60000, 15.0, 250.0, 1.3, 4.8, 0, 'Sweet')",
            )
            db.execSQL(
                "INSERT INTO brews (id, mode, started_at, duration_ms, dose_grams, yield_grams, flow_avg, flow_peak, interrupted) " +
                    "VALUES ('unrated', 'FILTER', 3000, 60000, 15.0, 250.0, 1.3, 4.8, 0)",
            )
            db.version = 11
        }

        val migrated = Room.databaseBuilder(context, BrewDatabase::class.java, BrewDatabase.NAME)
            .addMigrations(BrewDatabase.MIGRATION_11_12)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(12, migrated.openHelper.readableDatabase.version)
            assertEquals(61_000L, migrated.brews().get("starred")!!.ratedAt)
            assertEquals(62_000L, migrated.brews().get("noted")!!.ratedAt)
            val unrated = migrated.brews().get("unrated")!!
            assertEquals(63_000L, unrated.ratedAt)
            assertEquals(emptyList<String>(), unrated.tastingNotesFound)
            assertEquals(emptyList<BrewEntity>(), migrated.brews().notRatedYet().first())
        } finally {
            migrated.close()
        }
    }

    private fun createVersion11(db: SQLiteDatabase) {
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
    }
}
