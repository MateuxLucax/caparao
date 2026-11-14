package dev.mateux.caparao.ui.brews

import dev.mateux.caparao.data.local.BrewRepository
import dev.mateux.caparao.data.local.SettingsStore
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.CoffeeBag
import dev.mateux.caparao.model.SyncError
import dev.mateux.caparao.ui.FakeBrewDao
import dev.mateux.caparao.ui.FakePreferences
import dev.mateux.caparao.ui.MainDispatcherRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrewsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val settings = SettingsStore(FakePreferences())
    private var resent = 0
    private val viewModel by lazy { BrewsViewModel(BrewRepository(FakeBrewDao()), flowOf(emptyList()), settings.syncStatus) { resent++ } }

    @Test
    fun showsTheServerTheLastSyncAndTheLastError() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertFalse(viewModel.uiState.value.sync.serverConfigured)

        settings.saveServer("https://coffee.example.com", "dev-token")
        settings.lastSyncMs = 1_000
        settings.setLastSyncError(SyncError(SyncError.Kind.UNAUTHORIZED), "b1")
        val sync = viewModel.uiState.value.sync
        assertTrue(sync.serverConfigured)
        assertEquals(1_000L, sync.lastSyncMs)
        assertEquals(SyncError.Kind.UNAUTHORIZED, sync.lastError?.kind)

        viewModel.retry()
        assertEquals(1, resent)
    }

    private fun brew(id: String, startedAt: Long, bagId: String? = null, ratedAt: Long? = null, coffee: String? = null) =
        Brew(id, BrewMode.FILTER, startedAt, 28_000, 18.0, 36.0, 4_000, 1.3, 2.4, interrupted = false, bagId = bagId, ratedAt = ratedAt, coffee = coffee)

    private val open = CoffeeBag("open", "Sitio", weightGrams = 250.0, createdAt = 0)
    private val finished = CoffeeBag("done", "Catuai", weightGrams = 250.0, createdAt = 0, finished = true)

    @Test
    fun splitsToRateFromRatedNewestFirst() {
        val state = BrewsViewModel.lines(
            listOf(brew("a", 1), brew("b", 2, ratedAt = 9), brew("c", 3), brew("d", 4, ratedAt = 9)),
            emptyList(),
            coffee = null,
        )
        assertEquals(listOf("c", "a"), state.toRate.map { it.brew.id })
        assertEquals(listOf("d", "b"), state.rated.map { it.brew.id })
        assertFalse(state.empty)
        assertTrue(BrewsViewModel.lines(emptyList(), emptyList(), null).empty)
    }

    @Test
    fun namesTheCoffeeFromTheBagThenFromTheBrew() {
        val state = BrewsViewModel.lines(
            listOf(brew("a", 3, bagId = "done", coffee = "Old name"), brew("b", 2, coffee = "Typed"), brew("c", 1, coffee = " ")),
            listOf(open, finished),
            coffee = null,
        )
        assertEquals(listOf("Catuai", "Typed", null), state.toRate.map { it.coffee })
        // Only open bags are offered as filters.
        assertEquals(listOf(CoffeeFilter("open", "Sitio")), state.coffees)
    }

    @Test
    fun filtersByAnOpenBagAndDropsAFilterOnAFinishedOne() {
        val brews = listOf(brew("a", 1, bagId = "open"), brew("b", 2, bagId = "done"), brew("c", 3))
        val filtered = BrewsViewModel.lines(brews, listOf(open, finished), coffee = "open")
        assertEquals("open", filtered.coffee)
        assertEquals(listOf("a"), filtered.toRate.map { it.brew.id })
        assertFalse(filtered.empty)

        val dropped = BrewsViewModel.lines(brews, listOf(open, finished), coffee = "done")
        assertEquals(null, dropped.coffee)
        assertEquals(3, dropped.toRate.size)
    }
}
