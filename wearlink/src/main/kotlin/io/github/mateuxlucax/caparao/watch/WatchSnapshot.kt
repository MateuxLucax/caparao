package io.github.mateuxlucax.caparao.watch

import java.nio.ByteBuffer

/** Where the phone stands, as the watch shows it. */
enum class WatchState {
    /** The phone app is open but not connected to the scale. */
    DISCONNECTED,

    /** Connected, no brew open: the phone's Scale mode (weight, flow, timer, tare). */
    SCALE,

    /** A brew is open and waiting for the cup, the tare or the first drop. */
    READY,

    /** Extracting or brewing. */
    BREWING,

    /** The brew ended. */
    DONE,
}

/** A filter recipe step's kind, as the watch names it (the phone's `StepKind`). */
enum class WatchStepKind { BLOOM, POUR, WAIT, SWIRL }

/**
 * Where the guided brew stands (docs/adr/0019-guided-brew.md): step [index] of [count] (equal to [count] once all are
 * done), its cumulative [targetGrams] for a pour, [dueInMs] until it is due while it hasn't begun, and once a pour began
 * its [paceMs] against the recipe (negative is ahead).
 */
data class WatchGuide(
    val index: Int,
    val count: Int,
    val kind: WatchStepKind?,
    val targetGrams: Float?,
    val dueInMs: Long?,
    val paceMs: Long?,
)

/**
 * What the phone sends the watch about 5 times a second. [elapsedMs] is the brew time while a brew runs and the
 * scale's own timer otherwise; [timerRunning] is Scale mode's timer button, the same as on the phone.
 */
data class WatchSnapshot(
    val state: WatchState,
    val weightGrams: Float,
    val flowGramsPerSecond: Float,
    val elapsedMs: Long,
    val timerRunning: Boolean,
    /** Set while a filter brew follows a recipe's steps. */
    val guide: WatchGuide? = null,
) {
    /**
     * A fixed 43-byte layout, versioned so an older watch ignores a newer phone instead of misreading it. Absent
     * guide values are written as sentinels: -1 for a kind or a count, NaN for grams, [Long.MIN_VALUE] for times.
     */
    fun encode(): ByteArray = ByteBuffer.allocate(SIZE)
        .put(VERSION)
        .put(state.ordinal.toByte())
        .putFloat(weightGrams)
        .putFloat(flowGramsPerSecond)
        .putLong(elapsedMs)
        .put(if (timerRunning) 1 else 0)
        .put((guide?.index ?: -1).toByte())
        .put((guide?.count ?: -1).toByte())
        .put((guide?.kind?.ordinal ?: -1).toByte())
        .putFloat(guide?.targetGrams ?: Float.NaN)
        .putLong(guide?.dueInMs ?: Long.MIN_VALUE)
        .putLong(guide?.paceMs ?: Long.MIN_VALUE)
        .array()

    companion object {
        private const val VERSION: Byte = 2
        private const val SIZE = 43

        /** Null for anything this version did not write. */
        fun decode(bytes: ByteArray): WatchSnapshot? {
            if (bytes.size != SIZE) return null
            val buffer = ByteBuffer.wrap(bytes)
            if (buffer.get() != VERSION) return null
            val state = WatchState.entries.getOrNull(buffer.get().toInt()) ?: return null
            val weight = buffer.getFloat()
            val flow = buffer.getFloat()
            val elapsed = buffer.getLong()
            val timerRunning = buffer.get() == 1.toByte()
            val index = buffer.get().toInt()
            val count = buffer.get().toInt()
            val kind = buffer.get().toInt()
            val target = buffer.getFloat()
            val due = buffer.getLong()
            val pace = buffer.getLong()
            val guide = if (count < 0) null else WatchGuide(
                index,
                count,
                WatchStepKind.entries.getOrNull(kind),
                target.takeUnless { it.isNaN() },
                due.takeUnless { it == Long.MIN_VALUE },
                pace.takeUnless { it == Long.MIN_VALUE },
            )
            return WatchSnapshot(state, weight, flow, elapsed, timerRunning, guide)
        }
    }
}
