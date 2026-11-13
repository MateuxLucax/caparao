package dev.mateux.caparao.wear

import dev.mateux.caparao.watch.WatchSnapshot
import dev.mateux.caparao.watch.WatchState
import java.util.Locale

/** Numbers on the watch, formatted like the phone's: one decimal in the locale's style, time as m:ss. */
object WatchFormat {
    fun weight(value: Float, locale: Locale = Locale.getDefault()): String = String.format(locale, "%.1f", value.normalized())

    fun grams(value: Float, locale: Locale = Locale.getDefault()): String = "${weight(value, locale)} g"

    fun flow(value: Float, locale: Locale = Locale.getDefault()): String = String.format(locale, "%.1f g/s", value.normalized())

    /** A step's target: 150 g, no decimals. */
    fun wholeGrams(value: Float): String = "%d g".format(Math.round(value))

    /** Whole seconds, rounded up so a countdown never shows 0 before it is due. */
    fun seconds(ms: Long): Int = ((ms + 999) / 1000).toInt()

    fun time(ms: Long): String {
        val seconds = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    /**
     * The countdown to the next step, or null when there is none: only while brewing, with time left on the current
     * step, and only when a step follows it (the last step's remaining time is not a "next" countdown).
     */
    fun nextStepDueMs(snapshot: WatchSnapshot): Long? {
        if (snapshot.state != WatchState.BREWING) return null
        val guide = snapshot.guide ?: return null
        val dueInMs = guide.dueInMs?.takeIf { it > 0 } ?: return null
        if (guide.index >= guide.count - 1) return null
        return dueInMs
    }

    /** -0.04 reads 0.0, not -0.0. */
    private fun Float.normalized() = if (this > -0.05f && this < 0.05f) 0f else this
}
