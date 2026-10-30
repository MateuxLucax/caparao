package io.github.mateuxlucax.caparao.wear

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import io.github.mateuxlucax.caparao.watch.WatchGuide
import io.github.mateuxlucax.caparao.watch.WatchPaths
import io.github.mateuxlucax.caparao.watch.WatchSnapshot
import io.github.mateuxlucax.caparao.watch.WatchState
import io.github.mateuxlucax.caparao.watch.WatchStepKind
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
        val brewing = BrewOngoingService.isBrewing(received.state)
        if (brewing && snapshot.value?.state?.let(BrewOngoingService::isBrewing) != true) BrewOngoingService.start(this)
        snapshot.value = received
        keepScreenOn(brewing)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Debug builds: a fixed brew for screenshots on an emulator with no phone paired.
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val demo = if (debuggable) intent.getStringExtra(EXTRA_DEMO)?.let { name -> WatchState.entries.find { it.name == name } } else null
        if (demo != null) snapshot.value = DEMOS.getValue(demo)
        // The Ongoing Activity's notification (Wear OS 4+ asks for it).
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        setContent {
            WatchScreen(
                snapshot.value,
                onTare = { send(WatchPaths.TARE) },
                onTimer = { send(WatchPaths.TIMER_TOGGLE) },
                onReset = { send(WatchPaths.TIMER_RESET) },
            )
        }
        // The phone closed or went out of range: back to "open the app" instead of a frozen weight.
        lifecycleScope.launch {
            while (true) {
                delay(STALE_AFTER_MS)
                if (demo == null && snapshot.value != null && SystemClock.elapsedRealtime() - receivedAtMs > STALE_AFTER_MS) {
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
        /** Debug builds: a [WatchState] name; shows a sample of it instead of waiting for the phone. */
        const val EXTRA_DEMO = "demo"
        private const val STALE_AFTER_MS = 3_000L

        private val DEMOS = mapOf(
            WatchState.DISCONNECTED to WatchSnapshot(WatchState.DISCONNECTED, 0f, 0f, 0, false),
            WatchState.SCALE to WatchSnapshot(WatchState.SCALE, 17.8f, 0f, 42_000, true),
            WatchState.READY to WatchSnapshot(WatchState.READY, 0f, 0f, 0, false),
            WatchState.BREWING to WatchSnapshot(
                WatchState.BREWING, 112.6f, 4.1f, 58_000, false, WatchGuide(2, 5, WatchStepKind.POUR, 150f, null, 3_000),
            ),
            WatchState.DONE to WatchSnapshot(WatchState.DONE, 36.1f, 0f, 28_400, false),
        )
    }
}
