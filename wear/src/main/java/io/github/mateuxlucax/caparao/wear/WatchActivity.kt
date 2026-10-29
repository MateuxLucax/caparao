package io.github.mateuxlucax.caparao.wear

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import io.github.mateuxlucax.caparao.watch.WatchPaths
import io.github.mateuxlucax.caparao.watch.WatchSnapshot
import io.github.mateuxlucax.caparao.watch.WatchState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * The one watch screen: what the phone sends, and tare and timer buttons sent back
 * (docs/adr/0023-watch-companion.md). Listens only while visible; the phone sends about 5 snapshots a second.
 */
class WatchActivity : ComponentActivity() {
    private val messages by lazy { Wearable.getMessageClient(this) }

    /** The latest snapshot, null until one arrives or after the phone goes quiet. */
    private val snapshot = mutableStateOf<WatchSnapshot?>(null)
    private var receivedAtMs = 0L

    private val listener = MessageClient.OnMessageReceivedListener { event ->
        if (event.path != WatchPaths.SNAPSHOT) return@OnMessageReceivedListener
        val received = WatchSnapshot.decode(event.data) ?: return@OnMessageReceivedListener
        receivedAtMs = SystemClock.elapsedRealtime()
        snapshot.value = received
        keepScreenOn(received.state == WatchState.READY || received.state == WatchState.BREWING)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Debug builds: a fixed brew for screenshots on an emulator with no phone paired.
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val demo = debuggable && intent.getBooleanExtra(EXTRA_DEMO, false)
        if (demo) snapshot.value = WatchSnapshot(WatchState.BREWING, 32.4f, 1.9f, 24_000, timerRunning = false)
        setContent {
            WatchScreen(
                snapshot.value,
                onTare = { send(WatchPaths.TARE) },
                onTimer = { running -> send(if (running) WatchPaths.TIMER_STOP else WatchPaths.TIMER_START) },
            )
        }
        // The phone closed or went out of range: back to "open the app" instead of a frozen weight.
        lifecycleScope.launch {
            while (true) {
                delay(STALE_AFTER_MS)
                if (!demo && snapshot.value != null && SystemClock.elapsedRealtime() - receivedAtMs > STALE_AFTER_MS) {
                    snapshot.value = null
                    keepScreenOn(false)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        messages.addListener(listener)
    }

    override fun onStop() {
        messages.removeListener(listener)
        super.onStop()
    }

    private fun send(path: String) {
        lifecycleScope.launch {
            runCatching {
                val phones = Wearable.getCapabilityClient(this@WatchActivity)
                    .getCapability(WatchPaths.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE).await().nodes
                phones.forEach { messages.sendMessage(it.id, path, null).await() }
            }
        }
    }

    private fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        /** Debug builds: show a sample brew instead of waiting for the phone (scripts/screenshots.sh style). */
        const val EXTRA_DEMO = "demo"
        private const val STALE_AFTER_MS = 3_000L
    }
}
