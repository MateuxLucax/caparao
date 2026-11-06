package io.github.mateuxlucax.caparao.ui.brew

import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.RecipeRepository
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.scale.ReplayScaleSource
import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind
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
        vm.setDose(20.0) // 255 g at 20 g is 1:12.8, below the band, so the lift pauses rather than ends
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
        assertEquals("a negative weight held during Pour pauses the brew", BrewPhase.PAUSED, vm.uiState.value.phase)
        assertEquals("and does not return to preparation", BrewStep.LIVE, vm.uiState.value.step)
        vm.end()
    }

    @Test
    fun aFilterLiftAfterWaterPausesAndResumesWithoutReturningToPrepare() = TestScope(dispatcher).runTest {
        Dispatchers.setMain(dispatcher)
        // Real Dot session 2026-11-06: the free filter lifted by hand at ~44.7 s into the replay, a hand pressing
        // on the dripper up to ~353 g from ~49.4 s, and the dripper back at ~50.6 s on 74 g of water.
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

        advanceTimeBy(4_300)
        runCurrent()
        assertEquals("the hand on the dripper does not resume or end the brew", BrewPhase.PAUSED, vm.uiState.value.phase)
        assertEquals(BrewStep.LIVE, vm.uiState.value.step)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("the dripper back on the scale resumes the brew", BrewPhase.BREWING, vm.uiState.value.phase)
        assertEquals(BrewStep.LIVE, vm.uiState.value.step)

        advanceTimeBy(500)
        runCurrent()
        vm.stop()
        val brew = vm.uiState.value.saved!!
        assertEquals("the saved water is the 74 g kept at the lift, not the hand's 353 g", 74.3, brew.yieldGrams, 0.5)
        vm.end()
    }

    @Test
    fun aGuidedFilterLiftAfterItsLastPourEndsTheBrewWithoutDone() = TestScope(dispatcher).runTest {
        Dispatchers.setMain(dispatcher)
        // Real Dot session 2026-11-06, guided filter: three pours from first water at ~453.4 s up to ~148.4 g, the
        // water settled from ~503 s, and the dripper lifted at ~572.9 s. Done was never tapped: the fixture ends before it.
        // The recipe is the one the pours were made to, bloom 35 g then 74 g and 149 g; the guide's own targets are not
        // in the capture.
        val guided = Recipe(
            id = "guided", name = "Guided", mode = BrewMode.FILTER, method = "V60", doseGrams = 15.0, targetRatio = 10.0,
            createdAt = 0, updatedAt = 0,
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 35.0, 0),
                RecipeStep(StepKind.POUR, 74.0, 20_000),
                RecipeStep(StepKind.POUR, 149.0, 40_000),
            ),
        )
        val lines = File("src/test/resources/sessions/real-filter-guided-lift-end.jsonl").readLines()
        val scale = ReplayScaleSource(backgroundScope, lines, repeat = false)
        val dao = FakeBrewDao()
        val vm = BrewViewModel(BrewMode.FILTER, scale, BrewRepository(dao), SettingsStore(FakePreferences()), RecipeRepository(FakeRecipeDao()), clock = { currentTime })
        scale.start()
        vm.begin()
        vm.selectRecipe(guided)
        vm.setDose(15.0)
        vm.ready()

        // The fixture's first reading is at replay time 0, about 7.8 s before first water.
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals("the water is still in the dripper before the lift", BrewPhase.BREWING, vm.uiState.value.phase)
        assertEquals("the guide has moved past the last pour", 3, vm.uiState.value.guide!!.index)

        advanceTimeBy(8_000)
        runCurrent()
        val state = vm.uiState.value
        assertEquals("the lift after the last pour opens the summary with no Done tapped", BrewStep.SUMMARY, state.step)
        assertEquals(BrewPhase.DONE, state.phase)
        val brew = state.saved!!
        assertEquals("the saved water is the 148.4 g at the lift, not the 2.3 g left on the scale", 148.4, brew.yieldGrams, 0.3)
        assertEquals("the brew ends at the lift, about 119.6 s after first water", 119.6, brew.durationMs / 1000.0, 0.5)
        // The saved curve is cut at the lift: its last sample is the yield, not the 152 g reading just before the drop.
        val curve = dao.samples.getValue(brew.id)
        assertEquals("the curve ends at the saved duration", brew.durationMs, curve.last().atMs)
        assertEquals("the curve ends at the saved yield", brew.yieldGrams, curve.last().weightGrams, 0.3)
        vm.end()
    }
}
