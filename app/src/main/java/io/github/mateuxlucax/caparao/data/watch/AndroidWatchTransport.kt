package io.github.mateuxlucax.caparao.data.watch

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import io.github.mateuxlucax.caparao.watch.WatchPaths
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * [WatchTransport] on the Wearable Data Layer. The capability and message listeners are registered only while their
 * flow is collected, so the watch link's `stop()` removes both of them (#324).
 */
class AndroidWatchTransport(context: Context) : WatchTransport {
    private val appContext = context.applicationContext
    private val messages = Wearable.getMessageClient(appContext)
    private val capabilities = Wearable.getCapabilityClient(appContext)
    private val remote = RemoteActivityHelper(appContext)

    override val watches: Flow<List<String>> = callbackFlow {
        // Registered before the first lookup, so a watch that connects right now is followed too.
        val listener = CapabilityClient.OnCapabilityChangedListener { info ->
            trySend(info.nodes.filter { it.isNearby }.map { it.id })
        }
        capabilities.addListener(listener, WatchPaths.WATCH_CAPABILITY)
        trySend(reachableWatches())
        awaitClose { capabilities.removeListener(listener) }
    }

    override val commands: Flow<String> = callbackFlow {
        val listener = MessageClient.OnMessageReceivedListener { event -> trySend(event.path) }
        messages.addListener(listener)
        awaitClose { messages.removeListener(listener) }
    }

    override fun send(node: String, path: String, payload: ByteArray) {
        messages.sendMessage(node, path, payload)
    }

    override fun open(node: String) {
        val intent = Intent(Intent.ACTION_VIEW, WatchPaths.OPEN_URI.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
        runCatching { remote.startRemoteActivity(intent, node) }
    }

    private suspend fun reachableWatches(): List<String> = runCatching {
        capabilities.getCapability(WatchPaths.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE).await().nodes.map { it.id }
    }.getOrDefault(emptyList())
}
