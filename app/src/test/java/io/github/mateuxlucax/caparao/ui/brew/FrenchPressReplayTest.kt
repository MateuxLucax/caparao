package io.github.mateuxlucax.caparao.ui.brew

import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.brew.FrenchPressWater
import io.github.mateuxlucax.caparao.brew.TimedReading
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.RecipeRepository
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.scale.FrameDecoder
import io.github.mateuxlucax.caparao.data.scale.ScaleMessage
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.model.StepKind
import io.github.mateuxlucax.caparao.ui.FakeBrewDao
import io.github.mateuxlucax.caparao.ui.FakePreferences
import io.github.mateuxlucax.caparao.ui.FakeRecipeDao
import io.github.mateuxlucax.caparao.ui.FakeScaleSource
import io.github.mateuxlucax.caparao.ui.MainDispatcherRule
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * The 2026-11-06 free press (#212) replayed frame by frame at the time each notification arrived: a spoon in the bloom,
 * a lift and return mid-steep, then the press removed. The capture is trimmed to 225–585 s (arming to Done).
 */
class FrenchPressReplayTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val scale = FakeScaleSource().apply { state.value = ScaleState.Connected(null) }
    private var now = 0L

    /** Done was tapped here in the capture. */
    private val STOP_AT_MS = 583_321L

    /** The press came off the scale here in the capture. */
    private val REMOVED_AT_MS = 568_000L

    /** The weight the screen showed after each reading while the synthetic lid was on the scale, before removal. */
    private val lidWeights = mutableListOf<Double>()

    /** The weight the screen showed while the spoon (447–456 s) was on the scale. */
    private val spoonWeights = mutableListOf<Double>()

    /** The step current while the spoon was on the scale, and every step the guide passed through. */
    private val spoonKinds = mutableListOf<StepKind?>()
    private val seenKinds = linkedSetOf<StepKind>()

    /**
     * Plays the capture; [lidAtMs] sets a synthetic 189 g lid with a 40 g landing spike on the scale from then on, and
     * [recipe] guides the brew from the first water.
     */
    private fun savedYieldOf(lidAtMs: Long? = null, recipe: Recipe? = null): Double {
        lidWeights.clear()
        spoonWeights.clear()
        spoonKinds.clear()
        seenKinds.clear()
        val vm = BrewViewModel(
            BrewMode.FRENCH_PRESS, scale, BrewRepository(FakeBrewDao()), SettingsStore(FakePreferences()), RecipeRepository(FakeRecipeDao()),
            clock = { now }, wallClock = { 1_000_000 + now }, newId = { "brew-1" },
            onSaved = {}, onDiscarded = {}, onWatchAlert = {},
        )
        val decoder = FrameDecoder()
        var armed = false
        /** Every reading the ViewModel got, as it got it: the reference [FrenchPressWater.of] runs over them. */
        val fed = mutableListOf<TimedReading>()
        /** When the first water arrived; null while the brew has not started. */
        var firstWaterAtMs: Long? = null
        File("src/test/resources/sessions/press-spoon-lift-removal.jsonl").readLines().drop(1).map(::JSONObject)
            .takeWhile { it.getLong("t_ms") <= STOP_AT_MS }
            .filter { it.getString("dir") == "rx" }
            .forEach { line ->
                now = line.getLong("t_ms")
                if (!armed && now >= 228_000) {
                    armed = true
                    vm.begin()
                    recipe?.let(vm::selectRecipe)
                    vm.setDose(20.0)
                    vm.ready()
                }
                val bytes = line.getString("hex").split(' ').map { it.toInt(16).toByte() }.toByteArray()
                decoder.feed(bytes).filterIsInstance<ScaleMessage.Weight>().forEach { weight ->
                    val reading = weight.reading
                    val onScale = reading.weightGrams > 5.0
                    val sent = when {
                        lidAtMs != null && now >= lidAtMs && onScale ->
                            reading.copy(weightGrams = reading.weightGrams + 189.0 + if (now < lidAtMs + 500) 40.0 else 0.0)
                        else -> reading
                    }
                    // The detector's first water: the first reading at or above 1 g, which starts the brew on the next.
                    if (vm.uiState.value.phase == BrewPhase.READY) {
                        firstWaterAtMs = if (sent.weightGrams >= 1.0) firstWaterAtMs ?: now else null
                    }
                    scale.send(sent)
                    fed += TimedReading(now, sent)
                    // The live weight is the reference water at every reading (#322).
                    if (vm.uiState.value.phase == BrewPhase.BREWING) {
                        val start = requireNotNull(firstWaterAtMs) { "the brew started without a first water at $now ms" }
                        val reference = requireNotNull(FrenchPressWater.of(fed, start, now)) { "no water at $now ms" }
                        assertEquals("the live water at $now ms", reference, vm.uiState.value.weightGrams, 0.0)
                    }
                    if (lidAtMs != null && now > lidAtMs + 600 && now < REMOVED_AT_MS) lidWeights += vm.uiState.value.weightGrams
                    val state = vm.uiState.value
                    val current = state.steps.getOrNull(state.guide?.index ?: -1)?.kind
                    if (current != null) seenKinds += current
                    if (now in 447_000..456_000) {
                        spoonWeights += state.weightGrams
                        spoonKinds += current
                    }
                }
            }
        now = STOP_AT_MS
        vm.stop()
        return vm.uiState.value.saved!!.yieldGrams
    }

    @Test
    fun theSettledWaterIsSavedNotTheRunningMaximum() {
        // The app saved 194.1 g for this brew before #214: the press set back down after a lift.
        assertEquals(154.0, savedYieldOf(), 0.3)
    }

    @Test
    fun aLidSetOnAfterTheSteepStartsChangesNothing() {
        // 540 s: after the second pour (ends ~516 s) and the lift, in the steep, before the removal at ~569 s.
        assertEquals(154.0, savedYieldOf(lidAtMs = 540_000), 0.3)
    }

    @Test
    fun theLiveWeightStaysWhereItWasWhileTheLidIsOn() {
        savedYieldOf(lidAtMs = 540_000)
        assertTrue("the screen read ${lidWeights.size} readings", lidWeights.size > 100)
        assertTrue(lidWeights.all { kotlin.math.abs(it - 154.0) <= 0.3 })
    }

    @Test
    fun theLiveWeightDoesNotFollowTheSpoonInTheBloom() {
        savedYieldOf()
        val spoon = spoonWeights
        assertTrue("the screen showed ${spoon.maxOrNull()} during the spoon", spoon.isNotEmpty() && spoon.max() <= 62.5)
    }

    /**
     * The same capture following a recipe with a stir: the spoon is set and taken off while the stir is the current
     * step, the water stays the bloom's ~62 g, and the second pour's 154 g is what is saved. Reaching the pour moves
     * the guide to the terminal press step, which waits there until Done (the capture ends before its 240 s deadline).
     */
    @Test
    fun aStirRunsInOrderAndTheSpoonDoesNotCountAsWater() {
        val recipe = Recipe(
            id = "press-stir", name = "French press with a stir", mode = BrewMode.FRENCH_PRESS, method = "French press",
            doseGrams = 20.0, targetRatio = 15.0,
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 62.0, 0),
                RecipeStep(StepKind.STIR, null, 5_000),
                RecipeStep(StepKind.POUR, 154.0, 70_000),
                RecipeStep(StepKind.PRESS, null, 240_000),
            ),
            createdAt = 0, updatedAt = 0,
        )

        assertEquals(154.0, savedYieldOf(recipe = recipe), 0.3)
        assertEquals(listOf(StepKind.BLOOM, StepKind.STIR, StepKind.POUR, StepKind.PRESS), seenKinds.toList())
        assertEquals(listOf(StepKind.STIR), spoonKinds.distinct())
        assertTrue("the spoon never reached the water level", spoonWeights.isNotEmpty() && spoonWeights.max() <= 62.5)
    }
}
