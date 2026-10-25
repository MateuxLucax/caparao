package io.github.mateuxlucax.caparao.ui.format

import java.util.Locale

/** Number formatting for the scale readouts, in the app's locale (a comma in pt-BR). */
object Format {
    /** Weight with the scale's 0.1 g resolution, e.g. "18.0". */
    fun grams(value: Double, locale: Locale): String = String.format(locale, "%.1f", value)

    /** Flow rate with one decimal, e.g. "1.3". */
    fun flow(gramsPerSecond: Double, locale: Locale): String = String.format(locale, "%.1f", gramsPerSecond)

    /** Elapsed brew time as mm:ss; minutes keep growing past 59. */
    fun timer(seconds: Int): String {
        val clamped = seconds.coerceAtLeast(0)
        return String.format(Locale.ROOT, "%02d:%02d", clamped / 60, clamped % 60)
    }
}
