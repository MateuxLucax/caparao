package io.github.mateuxlucax.caparao.ui.brew

import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.scale.ReplayScaleSource
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.ui.FakeBrewDao
import io.github.mateuxlucax.caparao.ui.FakePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The whole chain on the JVM: the synthetic espresso session replayed by [ReplayScaleSource] (frames, decoder, tare)
 * into [BrewViewModel] and its detector, out to a saved brew. Virtual time, so the 52 s session runs instantly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplayedEspressoTest {
    private val dispatcher = StandardTestDispatcher()

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun theSyntheticShotIsSavedWithItsNumbers() = TestScope(dispatcher).runTest {
        Dispatchers.setMain(dispatcher)
        val lines = File("src/test/resources/sessions/synthetic-espresso.jsonl").readLines()
        val scale = ReplayScaleSource(backgroundScope, lines)
        val dao = FakeBrewDao()
        val vm = BrewViewModel(BrewMode.ESPRESSO, scale, BrewRepository(dao), SettingsStore(FakePreferences()), clock = { currentTime })
        scale.start()
        vm.begin()
        vm.setDose(18.0)
        advanceTimeBy(48_000)

        val brew = vm.uiState.value.saved!!
        // The script pours 36 g over 26 s, but its first and last second add only a few tenths: the shot as the
        // detector sees it (first 0.3 g to the last real rise) is about 24.5 s.
        assertEquals(36.0, brew.yieldGrams, 0.5)
        assertEquals(24.5, brew.durationMs / 1000.0, 1.0)
        assertEquals(2.0, brew.ratio, 0.05)
        assertEquals(1, dao.brews.value.size)
        vm.end()
    }
}
