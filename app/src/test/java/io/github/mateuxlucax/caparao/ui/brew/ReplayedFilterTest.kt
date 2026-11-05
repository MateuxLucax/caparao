package io.github.mateuxlucax.caparao.ui.brew

import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.RecipeRepository
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.scale.ReplayScaleSource
import io.github.mateuxlucax.caparao.brew.BrewPhase
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The whole chain on the JVM for a filter brew: the synthetic session with the server lifted at 90 s, replayed into
 * [BrewViewModel], with Done tapped during the pause.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplayedFilterTest {
    private val dispatcher = StandardTestDispatcher()

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun theServerLiftedBeforeDoneKeepsTheWaterAndTheFrozenTime() = TestScope(dispatcher).runTest {
        Dispatchers.setMain(dispatcher)
        val lines = File("src/test/resources/sessions/synthetic-filter-lift.jsonl").readLines()
        val scale = ReplayScaleSource(backgroundScope, lines)
        val dao = FakeBrewDao()
        val vm = BrewViewModel(BrewMode.FILTER, scale, BrewRepository(dao), SettingsStore(FakePreferences()), RecipeRepository(FakeRecipeDao()), clock = { currentTime })
        scale.start()
        vm.begin()
        vm.setDose(15.0)
        vm.ready()
        // Everything leaves the scale at 90 s; the brew is paused at the lift.
        advanceTimeBy(92_000)
        assertEquals(BrewPhase.PAUSED, vm.uiState.value.phase)
        assertTrue(kotlin.math.abs(70_000L - vm.uiState.value.elapsedMs) <= 500L)
        vm.stop()

        val brew = vm.uiState.value.saved!!
        assertEquals(255.0, brew.yieldGrams, 0.5)
        assertEquals(70.0, brew.durationMs / 1000.0, 1.0)
        vm.end()
    }

    @Test
    fun negativeWeightReturnsFilterToPrepareBeforeAndDuringPour() = TestScope(dispatcher).runTest {
        Dispatchers.setMain(dispatcher)
        val lines = File("src/test/resources/sessions/synthetic-filter-prepare-return.jsonl").readLines()
        val scale = ReplayScaleSource(backgroundScope, lines, startPaused = true, repeat = false)
        val vm = BrewViewModel(BrewMode.FILTER, scale, BrewRepository(FakeBrewDao()), SettingsStore(FakePreferences()), RecipeRepository(FakeRecipeDao()), clock = { currentTime })
        scale.start()
        vm.begin()
        runCurrent()
        vm.setDose(15.0)
        vm.setPreparationCheck(1, true)
        vm.ready()
        scale.resume()

        advanceTimeBy(3_100)
        runCurrent()
        assertEquals("lifting the dripper during preparation returns to the checklist", BrewStep.PREPARE_PORTAFILTER, vm.uiState.value.step)
        assertTrue("the checked grind item is kept", 1 in vm.uiState.value.preparationChecks)

        advanceTimeBy(200)
        runCurrent()
        vm.ready()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("the scale starts pouring after Ready tares again", BrewPhase.BREWING, vm.uiState.value.phase)

        advanceTimeBy(2_100)
        runCurrent()
        assertEquals("a negative weight held during Pour returns to preparation", BrewStep.PREPARE_PORTAFILTER, vm.uiState.value.step)
        assertTrue("the checklist is still checked after Pour", 1 in vm.uiState.value.preparationChecks)
        vm.end()
    }
}
