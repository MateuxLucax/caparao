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
    fun negativeWeightReturnsToPrepareBeforeWaterAndPausesDuringPour() = TestScope(dispatcher).runTest {
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
        assertEquals("a negative weight held during Pour pauses the brew", BrewPhase.PAUSED, vm.uiState.value.phase)
        assertEquals("and does not return to preparation", BrewStep.LIVE, vm.uiState.value.step)
        vm.end()
    }

    @Test
    fun aFilterLiftAfterWaterPausesAndResumesWithoutReturningToPrepare() = TestScope(dispatcher).runTest {
        Dispatchers.setMain(dispatcher)
        // Real Dot session 2026-11-06: the free filter lifted by hand at ~44.7 s into the replay, a hand pressing
        // on the dripper up to ~353 g from ~49.4 s, and the dripper back on 75 g at ~50.3 s.
        val lines = File("src/test/resources/sessions/real-filter-free-lift-hand.jsonl").readLines()
        val scale = ReplayScaleSource(backgroundScope, lines, repeat = false)
        val vm = BrewViewModel(BrewMode.FILTER, scale, BrewRepository(FakeBrewDao()), SettingsStore(FakePreferences()), RecipeRepository(FakeRecipeDao()), clock = { currentTime })
        scale.start()
        vm.begin()
        vm.setDose(15.0)
        vm.ready()

        advanceTimeBy(45_700)
        runCurrent()
        assertEquals("the lift pauses the brew at once", BrewPhase.PAUSED, vm.uiState.value.phase)
        assertEquals("the lift after water does not return to preparation", BrewStep.LIVE, vm.uiState.value.step)
        assertEquals("the paused scale shows the 74 g of water", 74.2, vm.uiState.value.weightGrams, 0.5)

        // The whole hand window, up to ~50.2 s: a spike over the water level does not open the resume window.
        advanceTimeBy(4_300)
        runCurrent()
        assertEquals("the hand on the dripper does not resume the brew at ~50.0 s", BrewPhase.PAUSED, vm.uiState.value.phase)
        assertEquals(BrewStep.LIVE, vm.uiState.value.step)
        advanceTimeBy(200)
        runCurrent()
        assertEquals("nor by ~50.2 s, the end of the hand window", BrewPhase.PAUSED, vm.uiState.value.phase)

        // The dripper is back on the water at ~50.3 s and must hold for a second before the brew resumes.
        advanceTimeBy(300)
        runCurrent()
        assertEquals("the dripper back on the scale is still held before it resumes", BrewPhase.PAUSED, vm.uiState.value.phase)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("the dripper back on the scale resumes the brew", BrewPhase.BREWING, vm.uiState.value.phase)
        assertEquals(BrewStep.LIVE, vm.uiState.value.step)

        vm.stop()
        val brew = vm.uiState.value.saved!!
        assertEquals("the saved water is the 74 g kept at the lift, not the hand's 353 g", 74.3, brew.yieldGrams, 0.5)
        vm.end()
    }
}
