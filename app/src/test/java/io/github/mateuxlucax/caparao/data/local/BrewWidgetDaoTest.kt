package io.github.mateuxlucax.caparao.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The weekly widget query filters by brew end time and includes rows that have no curve samples. */
@RunWith(RobolectricTestRunner::class)
class BrewWidgetDaoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, BrewDatabase::class.java).allowMainThreadQueries().build()
    private val repository = BrewRepository(db.brews())

    @After
    fun tearDown() = db.close()

    @Test
    fun queryUsesTheHalfOpenEndTimeWindowWithoutLoadingSamples() = runBlocking {
        db.brews().insertWithSamples(brew("before", startedAt = 500, durationMs = 499), emptyList())
        db.brews().insertWithSamples(brew("start", startedAt = 800, durationMs = 200), emptyList())
        db.brews().insertWithSamples(brew("inside", startedAt = 1_200, durationMs = 100), emptyList())
        db.brews().insertWithSamples(brew("end", startedAt = 1_800, durationMs = 200), emptyList())

        val rows = repository.endedBetween(startInclusive = 1_000, endExclusive = 2_000)

        assertEquals(listOf("start", "inside"), rows.map { it.id })
        assertEquals(emptyList<BrewSampleEntity>(), db.brews().samples("start"))
    }

    private fun brew(id: String, startedAt: Long, durationMs: Long) = BrewEntity(
        id = id,
        mode = "FILTER",
        startedAt = startedAt,
        durationMs = durationMs,
        doseGrams = 15.0,
        yieldGrams = 250.0,
        timeToFirstDropMs = null,
        flowAvg = 1.0,
        flowPeak = 2.0,
        interrupted = false,
        syncedAt = null,
    )
}
