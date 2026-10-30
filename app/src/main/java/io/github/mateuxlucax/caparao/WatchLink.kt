package io.github.mateuxlucax.caparao

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.os.SystemClock
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import io.github.mateuxlucax.caparao.brew.StepEvent
import io.github.mateuxlucax.caparao.data.scale.ScaleSource
import io.github.mateuxlucax.caparao.ui.brew.BrewUiState
import io.github.mateuxlucax.caparao.ui.home.ScaleTimer
import io.github.mateuxlucax.caparao.ui.watch.SnapshotThrottle
import io.github.mateuxlucax.caparao.ui.watch.WatchMirror
import io.github.mateuxlucax.caparao.watch.WatchPaths
import io.github.mateuxlucax.caparao.watch.WatchState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Mirrors the scale and the open brew to a paired Wear OS watch and takes its tare and timer buttons
 * (docs/adr/0023-watch-companion.md). Runs while the app is visible, like the scale connection; with no watch it
 * finds no node and sends nothing.
 */
class WatchLink(context: Context, private val scope: CoroutineScope, private val timer: ScaleTimer, private val scale: () -> ScaleSource) {
    private val appContext = context.applicationContext
    private val messages = Wearable.getMessageClient(appContext)
    private val capabilities = Wearable.getCapabilityClient(appContext)

    /** The open brew screen's state, null without one (BrewViewModel.onLive). */
    val brew = MutableStateFlow<BrewUiState?>(null)

    private var watchNodes: List<String> = emptyList()
    private var job: Job? = null

    private val listener = MessageClient.OnMessageReceivedListener { event ->
        when (event.path) {
            WatchPaths.TARE -> scale().tare()
            WatchPaths.TIMER_TOGGLE -> timer.toggle()
            WatchPaths.TIMER_RESET -> timer.reset()
        }
    }

    fun start() {
        if (job != null) return
        messages.addListener(listener)
        val throttle = SnapshotThrottle()
        job = scope.launch {
            watchNodes = reachableWatches()
            val source = scale()
            var lastState: WatchState? = null
            // Readings arrive about 10 times a second even when equal; state and brew changes cover the gaps.
            merge(source.readings, source.state, brew, timer.running).collect {
                val snapshot = WatchMirror.snapshot(source.state.value, source.reading.value, brew.value, timer.running.value)
                if (snapshot.state == WatchState.READY && lastState != WatchState.READY) openOnWatch()
                lastState = snapshot.state
                if (throttle.offer(SystemClock.elapsedRealtime(), snapshot)) send(snapshot.encode())
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        messages.removeListener(listener)
    }

    private suspend fun reachableWatches(): List<String> = runCatching {
        capabilities.getCapability(WatchPaths.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE).await().nodes.map { it.id }
    }.getOrDefault(emptyList())

    private fun send(payload: ByteArray) {
        // Fire and forget: a lost frame is replaced by the next one 200 ms later.
        watchNodes.forEach { node -> messages.sendMessage(node, WatchPaths.SNAPSHOT, payload) }
    }

    /** Vibrates the watch for a guide moment (docs/adr/0019-guided-brew.md); nothing without a watch. */
    fun alert(event: StepEvent) {
        val payload = WatchMirror.alert(event).encode()
        watchNodes.forEach { node -> messages.sendMessage(node, WatchPaths.ALERT, payload) }
    }

    /** A brew screen opened: bring the watch app up so the wrist shows it without a tap. */
    private fun openOnWatch() {
        if (watchNodes.isEmpty()) return
        val intent = Intent(Intent.ACTION_VIEW, WatchPaths.OPEN_URI.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
        val helper = RemoteActivityHelper(appContext)
        watchNodes.forEach { node -> runCatching { helper.startRemoteActivity(intent, node) } }
    }
}
