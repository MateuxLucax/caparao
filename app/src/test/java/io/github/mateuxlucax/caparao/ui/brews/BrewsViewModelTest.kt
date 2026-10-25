package io.github.mateuxlucax.caparao.ui.brews

import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.SyncError
import io.github.mateuxlucax.caparao.ui.FakeBrewDao
import io.github.mateuxlucax.caparao.ui.FakePreferences
import io.github.mateuxlucax.caparao.ui.MainDispatcherRule
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
    private val viewModel by lazy { BrewsViewModel(BrewRepository(FakeBrewDao()), settings.syncStatus) { resent++ } }

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

    private fun brew(id: String, mode: BrewMode, startedAt: Long) =
        Brew(id, mode, startedAt, 28_000, 18.0, 36.0, 4_000, 1.3, 2.4, interrupted = false)

    @Test
    fun groupsByModeInModeOrderNewestFirst() {
        val brews = listOf(
            brew("e1", BrewMode.ESPRESSO, 1),
            brew("f1", BrewMode.FILTER, 2),
            brew("e2", BrewMode.ESPRESSO, 3),
        )
        val sections = BrewsViewModel.group(brews)
        assertEquals(listOf(BrewMode.FILTER, BrewMode.ESPRESSO), sections.map { it.first })
        assertEquals(listOf("e2", "e1"), sections[1].second.map { it.id })
    }

    @Test
    fun modesWithoutBrewsAreLeftOut() {
        assertEquals(listOf(BrewMode.ESPRESSO), BrewsViewModel.group(listOf(brew("e1", BrewMode.ESPRESSO, 1))).map { it.first })
        assertEquals(emptyList<Pair<BrewMode, List<Brew>>>(), BrewsViewModel.group(emptyList()))
    }
}
