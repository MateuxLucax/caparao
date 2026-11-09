package io.github.mateuxlucax.caparao.data.watch

import kotlinx.coroutines.flow.Flow

/**
 * The Wear OS Data Layer operations the watch mirroring needs: which watches are reachable, sending to them and opening
 * the watch app. [AndroidWatchTransport] wraps the Play services clients; tests fake it, so following a watch that
 * connects after the app opened runs on the JVM (#324).
 */
interface WatchTransport {
    /**
     * The reachable watch node ids, starting with the current ones and then again whenever a watch comes or goes while
     * collected.
     */
    val watches: Flow<List<String>>

    /** The message paths the watch sends to the phone (tare and the timer buttons) while collected. */
    val commands: Flow<String>

    /** Sends [payload] to [node] at [path]. Failures stay silent: the next snapshot replaces a lost frame. */
    fun send(node: String, path: String, payload: ByteArray)

    /** Opens the watch app on [node], so the wrist shows the phone without a tap. */
    fun open(node: String)
}
