package dev.mateux.caparao.watch

/**
 * A guided brew moment the phone asks the watch to vibrate for (#103), sent as one byte on
 * [WatchPaths.ALERT]. The patterns match the phone's, so both feel the same.
 */
enum class WatchAlert(val pattern: LongArray) {
    STEP_STARTED(longArrayOf(0, 80)),
    NEAR_TARGET(longArrayOf(0, 30)),
    TARGET_REACHED(longArrayOf(0, 60, 80, 60)),
    COUNTDOWN(longArrayOf(0, 20)),
    ALL_DONE(longArrayOf(0, 400)),
    // Appended: the ordinal is the wire byte, so older watches decode these as null and stay quiet (#193, #209).
    CUP_DETECTED(longArrayOf(0, 40)),
    TARE_CONFIRMED(longArrayOf(0, 40, 80, 40)),
    FIRST_DROP(longArrayOf(0, 120)),
    IN_TARGET_BAND(longArrayOf(0, 50, 60, 50, 60, 50)),
    FIRST_WATER(longArrayOf(0, 120)),
    PAST_TARGET_BAND(longArrayOf(0, 80, 60, 80)),
    PAUSED(longArrayOf(0, 100)),
    RESUMED(longArrayOf(0, 50, 50, 50)),
    DRAIN_TIME_ENTERED(longArrayOf(0, 60, 80, 100)),
    DRAIN_TIME_PASSED(longArrayOf(0, 200)),
    FRENCH_PRESS_REMINDER(longArrayOf(0, 400)),
    // The French press deadline, sent once in place of ALL_DONE so the watch names the press step (#184). Older watches
    // decode it as null: they stay quiet for this one moment and still get the reminders.
    FRENCH_PRESS_DEADLINE(longArrayOf(0, 400)),
    ;

    fun encode(): ByteArray = byteArrayOf(ordinal.toByte())

    companion object {
        /** Null for anything this version did not write. */
        fun decode(bytes: ByteArray): WatchAlert? = bytes.singleOrNull()?.let { entries.getOrNull(it.toInt()) }
    }
}
