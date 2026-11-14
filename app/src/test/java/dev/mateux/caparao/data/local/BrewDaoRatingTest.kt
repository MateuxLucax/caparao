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
    fun editingBrewNotesDoesNotOverwriteTheRating() = runBlocking {
        dao.insert(brew("rated", startedAt = 1_000).copy(
            coffee = "Caparaó",
            grind = "Medium",
            rating = 4,
            notes = "Sweet",
            taste = "balanced",
            tastingNotesFound = listOf("cocoa"),
            ratedAt = 2_000,
            revision = 1,
        ))

        dao.updateNotes("rated", "Caparaó", "bag-1", "Fine", 93)

        val saved = dao.get("rated")!!
        assertEquals("Fine", saved.grind)
        assertEquals(93, saved.waterTempCelsius)
        assertEquals(4, saved.rating)
        assertEquals("Sweet", saved.notes)
        assertEquals("balanced", saved.taste)
        assertEquals(listOf("cocoa"), saved.tastingNotesFound)
        assertEquals(2_000L, saved.ratedAt)
        assertEquals(2, saved.revision)
        assertNull(saved.syncedAt)
    }

    @Test
    fun notRatedFlowUpdatesWhenANewBrewIsAddedAndRated() = runBlocking {
        val notRated = dao.notRatedYet()
        assertEquals(emptyList<BrewEntity>(), notRated.first())

        dao.insert(brew("new", startedAt = 1_000))
        assertEquals(listOf("new"), notRated.first { it.isNotEmpty() }.map { it.id })

        dao.saveRating("new", rating = null, notes = "Balanced", taste = null, tastingNotesFound = "[]", ratedAt = 2_000)
        assertEquals(emptyList<BrewEntity>(), notRated.first { it.isEmpty() })
    }

    @Test
    fun savingNoRatingDataDoesNotSetRatedAt() = runBlocking {
        dao.insert(brew("new", startedAt = 1_000))

        dao.saveRating("new", rating = null, notes = null, taste = null, tastingNotesFound = "[]", ratedAt = null)

        assertNull(dao.get("new")!!.ratedAt)
        assertEquals(listOf("new"), dao.notRatedYet().first().map { it.id })
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
