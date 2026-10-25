package io.github.mateuxlucax.caparao.ui.brews

import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

/** An axis from 0 to [max] with gridlines every [step]. */
data class Axis(val max: Double, val step: Double) {
    val ticks: List<Double> get() = generateSequence(0.0) { it + step }.takeWhile { it <= max + step / 1000 }.toList()

    /** Where [value] sits on the axis, 0..1 (clamped). */
    fun fraction(value: Double) = (value / max).coerceIn(0.0, 1.0)
}

/** Picks round axes for the brew chart (docs/adr/0013-canvas-charts.md). Pure, so it is unit tested. */
object ChartScale {
    /**
     * The smallest axis from 0 that holds [maxValue] with 3 to 6 gridlines on 1, 2 or 5 × 10ⁿ steps; [minimum]
     * keeps an empty or tiny curve from filling the whole chart.
     */
    fun axis(maxValue: Double, minimum: Double): Axis {
        val top = maxOf(maxValue, minimum, 1e-6)
        val raw = top / 5
        val magnitude = 10.0.pow(kotlin.math.floor(log10(raw)))
        val step = listOf(1.0, 2.0, 5.0, 10.0).map { it * magnitude }.first { it >= raw }
        return Axis(ceil(top / step - 1e-9) * step, step)
    }

    /** Time axis in seconds, gridlines every 5, 10, 30 or 60 s. */
    fun timeAxis(durationMs: Long): Axis {
        val seconds = maxOf(durationMs / 1000.0, 5.0)
        val step = listOf(5.0, 10.0, 30.0, 60.0, 120.0).first { seconds / it <= 6 }
        return Axis(ceil(seconds / step - 1e-9) * step, step)
    }
}
