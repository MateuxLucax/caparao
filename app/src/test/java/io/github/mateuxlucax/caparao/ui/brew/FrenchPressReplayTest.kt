package io.github.mateuxlucax.caparao.ui.brew

import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.RecipeRepository
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.scale.FrameDecoder
import io.github.mateuxlucax.caparao.data.scale.ScaleMessage
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.ui.FakeBrewDao
import io.github.mateuxlucax.caparao.ui.FakePreferences
import io.github.mateuxlucax.caparao.ui.FakeRecipeDao
import io.github.mateuxlucax.caparao.ui.FakeScaleSource
import io.github.mateuxlucax.caparao.ui.MainDispatcherRule
import org.json.JSONObject
import org.junit.Assert.assertEquals
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

    /** Plays the capture; [lidAtMs] sets a synthetic 189 g lid with a 40 g landing spike on the scale from then on. */
    private fun savedYieldOf(lidAtMs: Long? = null): Double {
        val vm = BrewViewModel(
            BrewMode.FRENCH_PRESS, scale, BrewRepository(FakeBrewDao()), SettingsStore(FakePreferences()), RecipeRepository(FakeRecipeDao()),
            clock = { now }, wallClock = { 1_000_000 + now }, newId = { "brew-1" },
            onSaved = {}, onDiscarded = {}, onWatchAlert = {},
        )
        val decoder = FrameDecoder()
        var armed = false
        File("src/test/resources/sessions/press-spoon-lift-removal.jsonl").readLines().drop(1).map(::JSONObject)
            .takeWhile { it.getLong("t_ms") <= STOP_AT_MS }
            .filter { it.getString("dir") == "rx" }
            .forEach { line ->
                now = line.getLong("t_ms")
                if (!armed && now >= 228_000) {
                    armed = true
                    vm.begin()
                    vm.setDose(20.0)
                    vm.ready()
                }
                val bytes = line.getString("hex").split(' ').map { it.toInt(16).toByte() }.toByteArray()
                decoder.feed(bytes).filterIsInstance<ScaleMessage.Weight>().forEach { weight ->
                    val reading = weight.reading
                    val onScale = reading.weightGrams > 5.0
                    scale.send(
                        when {
                            lidAtMs != null && now >= lidAtMs && onScale ->
                                reading.copy(weightGrams = reading.weightGrams + 189.0 + if (now < lidAtMs + 500) 40.0 else 0.0)
                            else -> reading
                        },
                    )
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
}
