package io.github.mateuxlucax.caparao

import android.os.SystemClock
import io.github.mateuxlucax.caparao.brew.StepEvent
import io.github.mateuxlucax.caparao.data.scale.ScaleSource
import io.github.mateuxlucax.caparao.data.watch.WatchTransport
import io.github.mateuxlucax.caparao.ui.brew.BrewUiState
import io.github.mateuxlucax.caparao.ui.home.ScaleTimer
import io.github.mateuxlucax.caparao.ui.watch.SnapshotThrottle
import io.github.mateuxlucax.caparao.ui.watch.WatchMirror
import io.github.mateuxlucax.caparao.watch.WatchPaths
import io.github.mateuxlucax.caparao.watch.WatchSnapshot
import io.github.mateuxlucax.caparao.watch.WatchState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Mirrors the scale and the open brew to a paired Wear OS watch and takes its tare and timer buttons
 * (#107). Runs while the app is visible, like the scale connection. It follows the reachable watches while
 * started: one that connects after the app opened starts mirroring without leaving the app, and one that
 * goes away stops being sent to (#324).
 */
class WatchLink(
    private val transport: WatchTransport,
    private val scope: CoroutineScope,
    private val timer: ScaleTimer,
    private val scale: ScaleSource,
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
) {
    /** The open brew screen's state, null without one (BrewViewModel.onLive). */
    val brew = MutableStateFlow<BrewUiState?>(null)

    /** Reachable watches, updated by the capability listener's collector on another thread. */
    @Volatile
    private var watchNodes: List<String> = emptyList()

    /** The snapshot last computed, sent at once to a watch that becomes reachable. */
    @Volatile
    private var lastSnapshot: WatchSnapshot? = null

    private var job: Job? = null
    private var watchesJob: Job? = null
    private var commandsJob: Job? = null

    fun start() {
        if (job != null) return
        val throttle = SnapshotThrottle()
        watchesJob = scope.launch {
            transport.watches.collect(::follow)
        }
        commandsJob = scope.launch {
            transport.commands.collect(::command)
        }
        job = scope.launch {
            val source = scale
            var lastState: WatchState? = null
            // Readings arrive about 10 times a second even when equal; state and brew changes cover the gaps.
            merge(source.readings, source.state, brew, timer.running).collect {
                val snapshot = WatchMirror.snapshot(source.state.value, source.reading.value, brew.value, timer.running.value)
                if (snapshot.state == WatchState.READY && lastState != WatchState.READY) openOnWatch(watchNodes)
                lastState = snapshot.state
                lastSnapshot = snapshot
                if (throttle.offer(now(), snapshot)) send(snapshot.encode())
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        watchesJob?.cancel()
        watchesJob = null
        commandsJob?.cancel()
        commandsJob = null
        watchNodes = emptyList()
        lastSnapshot = null
    }

    /**
     * Follows the watches: a watch that just became reachable gets the current snapshot at once, and the watch app
     * opens when the wrist is due to show the brew. A watch that left is dropped, so nothing is sent to it anymore.
     */
    private fun follow(nodes: List<String>) {
        val added = nodes - watchNodes.toSet()
        watchNodes = nodes
        if (added.isEmpty()) return
        val snapshot = lastSnapshot ?: return
        val payload = snapshot.encode()
        added.forEach { node -> transport.send(node, WatchPaths.SNAPSHOT, payload) }
        if (snapshot.state == WatchState.READY) openOnWatch(added)
    }

    private fun command(path: String) {
        when (path) {
            WatchPaths.TARE -> scale.tare()
            WatchPaths.TIMER_TOGGLE -> timer.toggle()
            WatchPaths.TIMER_RESET -> timer.reset()
        }
    }

    private fun send(payload: ByteArray) {
        // Fire and forget: a lost frame is replaced by the next one 200 ms later.
        watchNodes.forEach { node -> transport.send(node, WatchPaths.SNAPSHOT, payload) }
    }

    /** Vibrates the watch for a guide moment (#103); nothing without a watch. */
    fun alert(event: StepEvent) {
        val payload = WatchMirror.alert(event).encode()
        watchNodes.forEach { node -> transport.send(node, WatchPaths.ALERT, payload) }
    }

    /** A brew screen is ready: bring the watch app up so the wrist shows it without a tap. */
    private fun openOnWatch(nodes: List<String>) {
        nodes.forEach { node -> transport.open(node) }
    }
}
