package dev.mateux.caparao.ui.widget

import dev.mateux.caparao.data.local.BrewRepository
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.ui.FakeBrewDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrewWidgetRepositoryTest {
    @Test
    fun saveAndDeleteRefreshTheWidgetAndLatestFallsBack() = runBlocking {
        val dao = FakeBrewDao()
        var refreshes = 0
        val repository = BrewRepository(dao) { refreshes++ }
        val first = brew("first", BrewMode.FILTER, 1).copy(interrupted = true)
        val second = brew("second", BrewMode.ESPRESSO, 2)

        repository.save(first, emptyList())
        repository.save(second, emptyList())
        assertEquals(second, repository.latest())
        assertEquals(2, refreshes)

        repository.delete("second")
        assertEquals(first, repository.latest())
        assertEquals(true, repository.latest()?.interrupted)
        assertEquals(3, refreshes)
    }

    @Test
    fun deletingTheOnlyBrewRefreshesToAnEmptyState() = runBlocking {
        val dao = FakeBrewDao()
        var refreshes = 0
        val repository = BrewRepository(dao) { refreshes++ }
        repository.save(brew("only", BrewMode.ESPRESSO, 1), emptyList())

        repository.delete("only")

        assertNull(repository.latest())
        assertEquals(2, refreshes)
    }

    private fun brew(id: String, mode: BrewMode, startedAt: Long) =
        Brew(id, mode, startedAt, 28_000, 18.0, 36.0, 4_000, 1.3, 2.4, interrupted = false)
}
