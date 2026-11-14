package dev.mateux.caparao.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrewRatingMigrationTest {
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
    fun migration11To12AddsRatingDataAndMarksAllExistingBrewsRated() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(BrewDatabase.NAME), null).use { db ->
            createSchemaFromJson(db, 11)
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
            .addMigrations(BrewDatabase.MIGRATION_11_12, BrewDatabase.MIGRATION_12_13)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(13, migrated.openHelper.readableDatabase.version)
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
}
