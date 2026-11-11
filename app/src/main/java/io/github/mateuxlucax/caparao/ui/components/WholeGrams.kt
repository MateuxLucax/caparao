package io.github.mateuxlucax.caparao.ui.components

import java.util.Locale

/** A gram amount rounded to a whole number for copy, e.g. "250" (shared by coffees and the brew steps). */
internal fun wholeGrams(value: Double, locale: Locale): String = String.format(locale, "%.0f", value)
