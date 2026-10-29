package io.github.mateuxlucax.caparao.ui.watch

import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.model.ScaleReading
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.ui.brew.BrewStep
import io.github.mateuxlucax.caparao.ui.brew.BrewUiState
import io.github.mateuxlucax.caparao.watch.WatchSnapshot
import io.github.mateuxlucax.caparao.watch.WatchState

/** What the phone knows, mirrored to the watch (docs/adr/0023-watch-companion.md). Pure, so the JVM tests cover it. */
object WatchMirror {
    /**
     * The snapshot for the scale's [state] and latest [reading], and the open [brew] if any. With a brew going the
     * weight, flow and time are the brew screen's; otherwise the scale's own, timer included.
     */
    fun snapshot(state: ScaleState, reading: ScaleReading?, brew: BrewUiState?, timerRunning: Boolean): WatchSnapshot {
        if (state !is ScaleState.Connected) return WatchSnapshot(WatchState.DISCONNECTED, 0f, 0f, 0, timerRunning = false)
        val live = brew?.takeIf { it.step != BrewStep.DOSE }
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
            live.step == BrewStep.SUMMARY || live.phase == BrewPhase.DONE -> WatchState.DONE
            live.phase == BrewPhase.BREWING -> WatchState.BREWING
            else -> WatchState.READY
        }
        return WatchSnapshot(watchState, live.weightGrams.toFloat(), live.flowGramsPerSecond.toFloat(), live.elapsedMs, timerRunning)
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
