package io.github.mateuxlucax.caparao.ui.brew

import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.RecipeRepository
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.scale.ReplayScaleSource
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.ui.FakeBrewDao
import io.github.mateuxlucax.caparao.ui.FakePreferences
import io.github.mateuxlucax.caparao.ui.FakeRecipeDao
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
 * The whole chain on the JVM for a filter brew: the synthetic session with the server lifted at 90 s, replayed into
 * [BrewViewModel], with Done tapped before the lift hold runs out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplayedFilterTest {
    private val dispatcher = StandardTestDispatcher()

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun theServerLiftedBeforeDoneKeepsTheWater() = TestScope(dispatcher).runTest {
        Dispatchers.setMain(dispatcher)
        val lines = File("src/test/resources/sessions/synthetic-filter-lift.jsonl").readLines()
        val scale = ReplayScaleSource(backgroundScope, lines)
        val dao = FakeBrewDao()
        val vm = BrewViewModel(BrewMode.FILTER, scale, BrewRepository(dao), SettingsStore(FakePreferences()), RecipeRepository(FakeRecipeDao()), clock = { currentTime })
        scale.start()
        vm.begin()
        vm.setDose(15.0)
        // Everything leaves the scale at 90 s; Done is tapped 2 s later, inside the lift hold.
        advanceTimeBy(92_000)
        vm.stop()

        val brew = vm.uiState.value.saved!!
        assertEquals(255.0, brew.yieldGrams, 0.5)
        assertEquals(70.0, brew.durationMs / 1000.0, 1.0)
        vm.end()
    }
}
