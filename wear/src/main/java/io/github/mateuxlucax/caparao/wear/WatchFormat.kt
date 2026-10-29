package io.github.mateuxlucax.caparao.wear

import java.util.Locale

/** Numbers on the watch, formatted like the phone's: one decimal in the locale's style, time as m:ss. */
object WatchFormat {
    fun grams(value: Float, locale: Locale = Locale.getDefault()): String = String.format(locale, "%.1f g", value.normalized())

    fun flow(value: Float, locale: Locale = Locale.getDefault()): String = String.format(locale, "%.1f g/s", value.normalized())

    fun time(ms: Long): String {
        val seconds = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    /** -0.04 reads 0.0, not -0.0. */
    private fun Float.normalized() = if (this > -0.05f && this < 0.05f) 0f else this
}
