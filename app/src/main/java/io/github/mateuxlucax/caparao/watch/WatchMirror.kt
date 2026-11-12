package io.github.mateuxlucax.caparao.watch

import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.brew.StepEvent
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.ScaleReading
import io.github.mateuxlucax.caparao.model.ScaleState

/** What the phone knows, mirrored to the watch (#107). Pure, so the JVM tests cover it. */
object WatchMirror {
    /**
     * The snapshot for the scale's [state] and latest [reading], and the open [brew] if any. With a brew going the
     * weight, flow and time are the brew screen's; otherwise the scale's own, timer included.
     */
    fun snapshot(state: ScaleState, reading: ScaleReading?, brew: WatchBrewState?, timerRunning: Boolean): WatchSnapshot {
        if (state !is ScaleState.Connected) return WatchSnapshot(WatchState.DISCONNECTED, 0f, 0f, 0, timerRunning = false)
        // Before the beans are weighed the watch shows the scale, as the phone's dose and coffee steps do.
        val live = brew?.takeIf { !it.beforeBrew }
        if (live == null) {
            return WatchSnapshot(
                WatchState.SCALE,
                (reading?.weightGrams ?: 0.0).toFloat(),
                (reading?.flowGramsPerSecond ?: 0.0).coerceAtLeast(0.0).toFloat(),
                (reading?.timerSeconds ?: 0) * 1000L,
                timerRunning,
            )
        }
        val watchState = when {
            live.phase == BrewPhase.DONE -> WatchState.DONE
            live.phase == BrewPhase.BREWING || live.phase == BrewPhase.PAUSED -> WatchState.BREWING
            else -> WatchState.READY
        }
        return WatchSnapshot(
            watchState, live.weightGrams.toFloat(), live.flowGramsPerSecond.toFloat(), live.elapsedMs,
            timerRunning, guide(live),
        )
    }

    /** The watch's vibration for a guide [event], the same pattern the phone plays. */
    fun alert(event: StepEvent): WatchAlert = when (event) {
        is StepEvent.Started -> WatchAlert.STEP_STARTED
        is StepEvent.NearTarget -> WatchAlert.NEAR_TARGET
        is StepEvent.Reached -> WatchAlert.TARGET_REACHED
        is StepEvent.Countdown -> WatchAlert.COUNTDOWN
        StepEvent.AllDone -> WatchAlert.ALL_DONE
        StepEvent.CupDetected -> WatchAlert.CUP_DETECTED
        StepEvent.TareConfirmed -> WatchAlert.TARE_CONFIRMED
        StepEvent.FirstDrop -> WatchAlert.FIRST_DROP
        StepEvent.InTargetBand -> WatchAlert.IN_TARGET_BAND
        StepEvent.FirstWater -> WatchAlert.FIRST_WATER
        StepEvent.PastTargetBand -> WatchAlert.PAST_TARGET_BAND
        StepEvent.Paused -> WatchAlert.PAUSED
        StepEvent.Resumed -> WatchAlert.RESUMED
        StepEvent.DrainTimeEntered -> WatchAlert.DRAIN_TIME_ENTERED
        StepEvent.DrainTimePassed -> WatchAlert.DRAIN_TIME_PASSED
        StepEvent.FrenchPressDeadline -> WatchAlert.FRENCH_PRESS_DEADLINE
        StepEvent.FrenchPressReminder -> WatchAlert.FRENCH_PRESS_REMINDER
    }

    /** The guide's current step, for filter or French press brews following a recipe; null otherwise. */
    fun guide(brew: WatchBrewState): WatchGuide? {
        val state = brew.guide ?: return null
        val steps = brew.steps
        if (steps.isEmpty()) return null
        if (brew.mode == BrewMode.FRENCH_PRESS && state.allDone(steps.size)) {
            return WatchGuide(
                index = steps.size,
                count = steps.size,
                kind = WatchStepKind.WAIT,
                targetGrams = null,
                dueInMs = null,
                paceMs = if (brew.frenchPressRemoved) 0 else null,
            )
        }
        val step = steps.getOrNull(state.index)
        return WatchGuide(
            index = state.index,
            count = steps.size,
            kind = step?.kind?.let { WatchStepKind.valueOf(it.name) },
            targetGrams = step?.targetWeightGrams?.toFloat(),
            dueInMs = state.dueInMs,
            paceMs = state.paceMs,
        )
    }
}

/**
 * At most one snapshot every [intervalMs], except a change of [WatchSnapshot.state] or of the timer, which goes at
 * once: the watch should flip to "brewing" without waiting.
 */
class SnapshotThrottle(private val intervalMs: Long = 200) {
    private var lastSentAtMs: Long? = null
    private var last: WatchSnapshot? = null

    /** Whether to send [snapshot] now; if so it becomes the last one sent. */
    fun offer(nowMs: Long, snapshot: WatchSnapshot): Boolean {
        val previous = last
        val sentAt = lastSentAtMs
        val due = previous == null || sentAt == null || nowMs - sentAt >= intervalMs ||
            previous.state != snapshot.state || previous.timerRunning != snapshot.timerRunning
        if (!due) return false
        last = snapshot
        lastSentAtMs = nowMs
        return true
    }
}
