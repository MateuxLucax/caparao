package io.github.mateuxlucax.caparao

import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.brew.StepEvent
import io.github.mateuxlucax.caparao.brew.StepState
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.ScaleReading
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.model.StepKind
import io.github.mateuxlucax.caparao.ui.brew.BrewStep
import io.github.mateuxlucax.caparao.ui.brew.BrewUiState
import io.github.mateuxlucax.caparao.watch.WatchAlert
import io.github.mateuxlucax.caparao.watch.WatchGuide
import io.github.mateuxlucax.caparao.watch.WatchSnapshot
import io.github.mateuxlucax.caparao.watch.WatchState
import io.github.mateuxlucax.caparao.watch.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchMirrorTest {
    private val connected = ScaleState.Connected(battery = null)
    private val reading = ScaleReading(weightGrams = 18.2, flowGramsPerSecond = -0.4, timerSeconds = 12, overload = false)

    @Test
    fun disconnectedSendsNothingButTheState() {
        val snapshot = WatchMirror.snapshot(ScaleState.Searching, reading, null, timerRunning = true)
        assertEquals(WatchSnapshot(WatchState.DISCONNECTED, 0f, 0f, 0, timerRunning = false), snapshot)
    }

    @Test
    fun withoutABrewTheScaleItselfShows() {
        val snapshot = WatchMirror.snapshot(connected, reading, null, timerRunning = true)
        assertEquals(WatchSnapshot(WatchState.SCALE, 18.2f, 0f, 12_000, timerRunning = true), snapshot)
        // The dose step is still weighing, not brewing.
        assertEquals(WatchState.SCALE, WatchMirror.snapshot(connected, reading, BrewUiState(BrewMode.ESPRESSO), false).state)
        // Nor is the coffee step, which comes before the beans are weighed (#291).
        assertEquals(WatchState.SCALE, WatchMirror.snapshot(connected, reading, BrewUiState(BrewMode.ESPRESSO, step = BrewStep.COFFEE), false).state)
    }

    @Test
    fun aLiveBrewShowsItsOwnNumbers() {
        val brew = BrewUiState(BrewMode.ESPRESSO, step = BrewStep.LIVE, phase = BrewPhase.BREWING, weightGrams = 30.5, flowGramsPerSecond = 1.9, elapsedMs = 21_400)
        assertEquals(WatchSnapshot(WatchState.BREWING, 30.5f, 1.9f, 21_400, timerRunning = false), WatchMirror.snapshot(connected, reading, brew, false))
        assertEquals(WatchState.READY, WatchMirror.snapshot(connected, reading, brew.copy(phase = BrewPhase.READY), false).state)
        assertEquals(WatchState.READY, WatchMirror.snapshot(connected, reading, brew.copy(phase = BrewPhase.WAITING_FOR_CUP), false).state)
        assertEquals(WatchState.DONE, WatchMirror.snapshot(connected, reading, brew.copy(step = BrewStep.SUMMARY), false).state)
        val paused = brew.copy(mode = BrewMode.FILTER, phase = BrewPhase.PAUSED, weightGrams = 25.0, flowGramsPerSecond = 0.0)
        assertEquals(WatchSnapshot(WatchState.BREWING, 25.0f, 0f, 21_400, timerRunning = true), WatchMirror.snapshot(connected, reading, paused, true))
    }

    @Test
    fun aGuidedFilterBrewSendsItsStep() {
        val steps = listOf(
            RecipeStep(StepKind.BLOOM, 40.0, 0),
            RecipeStep(StepKind.WAIT, null, 0),
            RecipeStep(StepKind.POUR, 150.0, 45_000),
        )
        val recipe = Recipe("r", "V60", BrewMode.FILTER, "V60", 15.0, 16.0, steps = steps, createdAt = 0, updatedAt = 0)
        val brew = BrewUiState(
            BrewMode.FILTER, step = BrewStep.LIVE, phase = BrewPhase.BREWING, weightGrams = 90.0, elapsedMs = 50_000,
            recipe = recipe, guide = StepState(2, 0.4f, null, null, 3_000, null),
        )
        assertEquals(WatchGuide(2, 3, WatchStepKind.POUR, 150f, null, 3_000), WatchMirror.snapshot(connected, reading, brew, false).guide)
        // All steps done: the count stays, the step is gone.
        assertEquals(WatchGuide(3, 3, null, null, null, null), WatchMirror.guide(brew.copy(guide = StepState(3, 1f, null, null, null, null))))
        // A free brew has no guide.
        assertEquals(null, WatchMirror.snapshot(connected, reading, brew.copy(recipe = null, guide = null), false).guide)
    }

    @Test
    fun aFrenchPressDeadlineUsesTheExistingWaitOrdinalOnTheV2WatchWire() {
        val recipe = Recipe(
            "press", "French press", BrewMode.FRENCH_PRESS, "French press", 20.0, 15.0,
            steps = listOf(RecipeStep(StepKind.PRESS, null, 240_000)), createdAt = 0, updatedAt = 0,
        )
        val brew = BrewUiState(
            BrewMode.FRENCH_PRESS,
            step = BrewStep.LIVE,
            phase = BrewPhase.BREWING,
            weightGrams = 300.0,
            elapsedMs = 180_000,
            recipe = recipe,
            guide = StepState(0, 0.75f, 60_000, null, null, null),
        )

        val snapshot = WatchMirror.snapshot(connected, reading, brew, timerRunning = false)

        assertEquals(WatchGuide(0, 1, WatchStepKind.WAIT, null, 60_000, null), snapshot.guide)
        assertEquals(WatchStepKind.WAIT.ordinal, WatchSnapshot.decode(snapshot.encode())?.guide?.kind?.ordinal)
        assertEquals(2, WatchStepKind.WAIT.ordinal)

        val afterTimer = brew.copy(guide = StepState(1, 1f, null, null, null, null))
        assertEquals(
            WatchGuide(1, 1, WatchStepKind.WAIT, null, null, null),
            WatchMirror.guide(afterTimer),
        )
        val afterRemoval = afterTimer.copy(frenchPressRemoved = true)
        val serving = WatchMirror.snapshot(connected, reading, afterRemoval, timerRunning = false)
        assertEquals(WatchGuide(1, 1, WatchStepKind.WAIT, null, null, 0), serving.guide)
        assertEquals("the watch keeps the measured water after serving", 300f, serving.weightGrams)
        assertEquals(0L, WatchSnapshot.decode(serving.encode())?.guide?.paceMs)
    }

    @Test
    fun aFrenchPressStirUsesTheExistingWaitOrdinalOnTheV2WatchWire() {
        val recipe = Recipe(
            "stir", "French press", BrewMode.FRENCH_PRESS, "French press", 20.0, 15.0,
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 62.0, 0),
                RecipeStep(StepKind.STIR, null, 5_000),
                RecipeStep(StepKind.POUR, 154.0, 70_000),
                RecipeStep(StepKind.PRESS, null, 240_000),
            ),
            createdAt = 0, updatedAt = 0,
        )
        val brew = BrewUiState(
            BrewMode.FRENCH_PRESS,
            step = BrewStep.LIVE,
            phase = BrewPhase.BREWING,
            weightGrams = 62.0,
            elapsedMs = 15_000,
            recipe = recipe,
            guide = StepState(1, 0.2f, 55_000, null, null, null),
        )

        val snapshot = WatchMirror.snapshot(connected, reading, brew, timerRunning = false)

        assertEquals(WatchGuide(1, 4, WatchStepKind.WAIT, null, 55_000, null), snapshot.guide)
        assertEquals(WatchStepKind.WAIT.ordinal, WatchSnapshot.decode(snapshot.encode())?.guide?.kind?.ordinal)
    }

    @Test
    fun throttleSendsFiveASecondButStateChangesAtOnce() {
        val throttle = SnapshotThrottle(intervalMs = 200)
        val scale = WatchSnapshot(WatchState.SCALE, 1f, 0f, 0, false)
        assertTrue(throttle.offer(0, scale))
        assertFalse(throttle.offer(100, scale.copy(weightGrams = 2f)))
        assertTrue(throttle.offer(200, scale.copy(weightGrams = 3f)))
        assertTrue(throttle.offer(250, scale.copy(state = WatchState.BREWING)))
        assertTrue(throttle.offer(260, scale.copy(state = WatchState.BREWING, timerRunning = true)))
        assertFalse(throttle.offer(300, scale.copy(state = WatchState.BREWING, timerRunning = true)))
    }

    @Test
    fun guideEventsMapToTheWatchsVibrations() {
        assertEquals(WatchAlert.STEP_STARTED, WatchMirror.alert(StepEvent.Started(1)))
        assertEquals(WatchAlert.NEAR_TARGET, WatchMirror.alert(StepEvent.NearTarget(1)))
        assertEquals(WatchAlert.TARGET_REACHED, WatchMirror.alert(StepEvent.Reached(1)))
        assertEquals(WatchAlert.COUNTDOWN, WatchMirror.alert(StepEvent.Countdown(1, 3)))
        assertEquals(WatchAlert.ALL_DONE, WatchMirror.alert(StepEvent.AllDone))
        assertEquals(WatchAlert.CUP_DETECTED, WatchMirror.alert(StepEvent.CupDetected))
        assertEquals(WatchAlert.TARE_CONFIRMED, WatchMirror.alert(StepEvent.TareConfirmed))
        assertEquals(WatchAlert.FIRST_DROP, WatchMirror.alert(StepEvent.FirstDrop))
        assertEquals(WatchAlert.IN_TARGET_BAND, WatchMirror.alert(StepEvent.InRatioBand))
        assertEquals(WatchAlert.FIRST_WATER, WatchMirror.alert(StepEvent.FirstWater))
        assertEquals(WatchAlert.PAST_TARGET_BAND, WatchMirror.alert(StepEvent.PastRatioBand))
        assertEquals(WatchAlert.PAUSED, WatchMirror.alert(StepEvent.Paused))
        assertEquals(WatchAlert.RESUMED, WatchMirror.alert(StepEvent.Resumed))
        assertEquals(WatchAlert.DRAIN_TIME_ENTERED, WatchMirror.alert(StepEvent.DrainTimeEntered))
        assertEquals(WatchAlert.DRAIN_TIME_PASSED, WatchMirror.alert(StepEvent.DrainTimePassed))
        assertEquals(WatchAlert.FRENCH_PRESS_DEADLINE, WatchMirror.alert(StepEvent.FrenchPressDeadline))
        assertEquals(WatchAlert.FRENCH_PRESS_REMINDER, WatchMirror.alert(StepEvent.FrenchPressReminder))
    }
}
