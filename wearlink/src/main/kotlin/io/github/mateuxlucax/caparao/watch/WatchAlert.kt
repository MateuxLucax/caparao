package io.github.mateuxlucax.caparao.watch

/**
 * A guided brew moment the phone asks the watch to vibrate for (docs/adr/0019-guided-brew.md), sent as one byte on
 * [WatchPaths.ALERT]. The patterns match the phone's, so both feel the same.
 */
enum class WatchAlert(val pattern: LongArray) {
    STEP_STARTED(longArrayOf(0, 80)),
    NEAR_TARGET(longArrayOf(0, 30)),
    TARGET_REACHED(longArrayOf(0, 60, 80, 60)),
    COUNTDOWN(longArrayOf(0, 20)),
    ALL_DONE(longArrayOf(0, 400)),
    ;

    fun encode(): ByteArray = byteArrayOf(ordinal.toByte())

    companion object {
        /** Null for anything this version did not write. */
        fun decode(bytes: ByteArray): WatchAlert? = bytes.singleOrNull()?.let { entries.getOrNull(it.toInt()) }
    }
}
