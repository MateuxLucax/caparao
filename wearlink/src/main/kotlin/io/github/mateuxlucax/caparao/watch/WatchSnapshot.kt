package io.github.mateuxlucax.caparao.watch

import java.nio.ByteBuffer

/** Where the phone stands, as the watch shows it. */
enum class WatchState {
    /** The phone app is open but not connected to the scale. */
    DISCONNECTED,

    /** Connected, no brew open: the weight while grinding or weighing. */
    SCALE,

    /** A brew is open and waiting for the cup, the tare or the first drop. */
    READY,

    /** Extracting or brewing. */
    BREWING,

    /** The brew ended. */
    DONE,
}

/**
 * What the phone sends the watch about 5 times a second. [elapsedMs] is the brew time while a brew runs and the
 * scale's own timer otherwise; [timerRunning] says which way the watch's timer button goes.
 */
data class WatchSnapshot(
    val state: WatchState,
    val weightGrams: Float,
    val flowGramsPerSecond: Float,
    val elapsedMs: Long,
    val timerRunning: Boolean,
) {
    /** A fixed 19-byte layout, versioned so an older watch ignores a newer phone instead of misreading it. */
    fun encode(): ByteArray = ByteBuffer.allocate(SIZE)
        .put(VERSION)
        .put(state.ordinal.toByte())
        .putFloat(weightGrams)
        .putFloat(flowGramsPerSecond)
        .putLong(elapsedMs)
        .put(if (timerRunning) 1 else 0)
        .array()

    companion object {
        private const val VERSION: Byte = 1
        private const val SIZE = 19

        /** Null for anything this version did not write. */
        fun decode(bytes: ByteArray): WatchSnapshot? {
            if (bytes.size != SIZE) return null
            val buffer = ByteBuffer.wrap(bytes)
            if (buffer.get() != VERSION) return null
            val state = WatchState.entries.getOrNull(buffer.get().toInt()) ?: return null
            return WatchSnapshot(state, buffer.getFloat(), buffer.getFloat(), buffer.getLong(), buffer.get() == 1.toByte())
        }
    }
}
