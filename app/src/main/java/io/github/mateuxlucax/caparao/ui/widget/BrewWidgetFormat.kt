package io.github.mateuxlucax.caparao.ui.widget

import android.content.Context
import io.github.mateuxlucax.caparao.R
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

/** Locale-aware values used only by the home-screen widget. */
internal object BrewWidgetFormat {
    fun count(context: Context, value: Int): String = integer(value.toDouble(), locale(context))

    fun dose(context: Context, grams: Double): String = amount(context, grams, R.string.unit_grams, 0)

    fun caffeine(context: Context, milligrams: Double): String = amount(context, milligrams, R.string.unit_mg, 0)

    fun beverageVolume(context: Context, milliliters: Double): String {
        val roundedMl = milliliters.roundToLong().coerceAtLeast(0)
        return if (roundedMl >= 1_000) {
            amount(context, roundedMl / 1_000.0, R.string.unit_l, 1)
        } else {
            amount(context, roundedMl.toDouble(), R.string.unit_ml, 0)
        }
    }

    private fun amount(context: Context, value: Double, unit: Int, maximumFractionDigits: Int): String =
        context.getString(
            R.string.widget_amount_with_unit,
            decimal(value, locale(context), maximumFractionDigits),
            context.getString(unit),
        )

    private fun integer(value: Double, locale: Locale): String = NumberFormat.getIntegerInstance(locale).apply {
        roundingMode = RoundingMode.HALF_UP
    }.format(value)

    private fun decimal(value: Double, locale: Locale, maximumFractionDigits: Int): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 0
            this.maximumFractionDigits = maximumFractionDigits
            roundingMode = RoundingMode.HALF_UP
        }.format(value)

    private fun locale(context: Context): Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
}
