package dev.mateux.caparao.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.BrewTaste
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrewRatingRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, BrewDatabase::class.java).allowMainThreadQueries().build()
    private val repository = BrewRepository(db.brews())

    @After
    fun tearDown() = db.close()

    @Test
    fun savesTasteAndNotesFoundMarksRatedAndBumpsRevision() = runBlocking {
        val brew = brew("brew").copy(syncedAt = 1_000)
        repository.save(brew, emptyList())

        repository.saveRating(
            brew.copy(taste = BrewTaste.BALANCED, tastingNotesFound = listOf("cocoa", "red fruit"), notes = "Clean cup"),
            ratedAt = 2_000,
        )

        val saved = repository.get("brew")!!
        assertEquals(BrewTaste.BALANCED, saved.taste)
        assertEquals(listOf("cocoa", "red fruit"), saved.tastingNotesFound)
        assertEquals("Clean cup", saved.notes)
        assertEquals(2_000L, saved.ratedAt)
        assertEquals(1, saved.revision)
        assertNull(saved.syncedAt)
        assertEquals(emptyList<Brew>(), repository.notRatedYet().first())
    }

    @Test
    fun newBrewWithoutRatingIsReturnedAsNotRated() = runBlocking {
        repository.save(brew("unrated"), emptyList())

        assertEquals(listOf("unrated"), repository.notRatedYet().first().map { it.id })
    }

    private fun brew(id: String) = Brew(
        id = id,
        mode = BrewMode.FILTER,
        startedAt = 1_000,
        durationMs = 60_000,
        doseGrams = 15.0,
        yieldGrams = 250.0,
        timeToFirstDropMs = null,
        flowAvgGramsPerSecond = 1.3,
        flowPeakGramsPerSecond = 4.8,
        interrupted = false,
    )
}
