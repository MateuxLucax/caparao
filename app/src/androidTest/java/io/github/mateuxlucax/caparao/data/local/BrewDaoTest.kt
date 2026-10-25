package io.github.mateuxlucax.caparao.data.local

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The real queries on SQLite, in memory. */
class BrewDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, BrewDatabase::class.java).build()
    private val dao = db.brews()

    private fun brew(id: String, mode: String, startedAt: Long, syncedAt: Long? = null) =
        BrewEntity(id, mode, startedAt, 28_000, 18.0, 36.0, 4_000, 1.3, 2.4, false, syncedAt)

    private fun samples(id: String) = (0L..2L).map { BrewSampleEntity(id, it * 100, it * 1.0, 0.5) }

    @After
    fun tearDown() = db.close()

    @Test
    fun savesAndListsNewestFirst() = runBlocking {
        dao.insertWithSamples(brew("a", "ESPRESSO", 1), samples("a"))
        dao.insertWithSamples(brew("b", "FILTER", 2), samples("b"))
        assertEquals(listOf("b", "a"), dao.observeAll().first().map { it.id })
        assertEquals(listOf(0L, 100L, 200L), dao.samples("a").map { it.atMs })
    }

    @Test
    fun deleteCascadesToSamples() = runBlocking {
        dao.insertWithSamples(brew("a", "ESPRESSO", 1), samples("a"))
        dao.delete("a")
        assertNull(dao.get("a"))
        assertTrue(dao.samples("a").isEmpty())
    }

    @Test
    fun aDuplicateIdIsRejectedWithoutPartialRows() = runBlocking {
        dao.insertWithSamples(brew("a", "ESPRESSO", 1), samples("a"))
        val failed = runCatching { dao.insertWithSamples(brew("a", "ESPRESSO", 5), listOf(BrewSampleEntity("a", 999, 1.0, 1.0))) }
        assertTrue(failed.isFailure)
        assertEquals(3, dao.samples("a").size)
    }

    @Test
    fun unsyncedAndMarkSynced() = runBlocking {
        dao.insertWithSamples(brew("a", "ESPRESSO", 1), samples("a"))
        dao.insertWithSamples(brew("b", "ESPRESSO", 2, syncedAt = 10), samples("b"))
        assertEquals(listOf("a"), dao.unsynced().map { it.id })
        dao.markSynced("a", 20)
        assertEquals(20L, dao.get("a")!!.syncedAt)
        assertTrue(dao.unsynced().isEmpty())
    }
}
