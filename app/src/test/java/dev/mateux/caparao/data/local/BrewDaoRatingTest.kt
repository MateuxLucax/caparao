package dev.mateux.caparao.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrewDaoRatingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, BrewDatabase::class.java).allowMainThreadQueries().build()
    private val dao = db.brews()

    @After
    fun tearDown() = db.close()

    @Test
    fun updateNotesMarksRatedForStarsOrNonblankNotesAndPreservesFirstRatingTime() = runBlocking {
        dao.insert(brew("notes", startedAt = 1_000))
        dao.insert(brew("stars", startedAt = 2_000))

        dao.updateNotes("notes", null, null, null, null, rating = null, notes = "Sweet", now = 3_000)
        dao.updateNotes("stars", null, null, null, null, rating = 4, notes = null, now = 4_000)
        dao.updateNotes("notes", null, null, null, null, rating = null, notes = "  ", now = 5_000)

        assertEquals(3_000L, dao.get("notes")!!.ratedAt)
        assertEquals(4_000L, dao.get("stars")!!.ratedAt)
        assertNull(dao.get("notes")!!.rating)
        assertEquals(emptyList<BrewEntity>(), dao.notRatedYet().first())
    }

    @Test
    fun notRatedFlowUpdatesWhenANewBrewIsAddedAndRated() = runBlocking {
        val notRated = dao.notRatedYet()
        assertEquals(emptyList<BrewEntity>(), notRated.first())

        dao.insert(brew("new", startedAt = 1_000))
        assertEquals(listOf("new"), notRated.first { it.isNotEmpty() }.map { it.id })

        dao.updateNotes("new", null, null, null, null, rating = null, notes = "Balanced", now = 2_000)
        assertEquals(emptyList<BrewEntity>(), notRated.first { it.isEmpty() })
    }

    private fun brew(id: String, startedAt: Long) = BrewEntity(
        id = id,
        mode = "FILTER",
        startedAt = startedAt,
        durationMs = 60_000,
        doseGrams = 15.0,
        yieldGrams = 250.0,
        timeToFirstDropMs = null,
        flowAvg = 1.3,
        flowPeak = 4.8,
        interrupted = false,
        syncedAt = null,
    )
}
