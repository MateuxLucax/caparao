package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.ScaleReading
import org.junit.Assert.assertEquals
import org.junit.Test

class FrenchPressWaterTest {
    /** Readings at 10 Hz from [from] to [to] grams over [seconds], starting at [startMs]. */
    private fun ramp(startMs: Long, from: Double, to: Double, seconds: Double): List<TimedReading> {
        val steps = (seconds * 10).toInt()
        return (1..steps).map { i -> reading(startMs + i * 100L, from + (to - from) * i / steps) }
    }

    private fun hold(startMs: Long, grams: Double, seconds: Double): List<TimedReading> =
        (1..(seconds * 10).toInt()).map { i -> reading(startMs + i * 100L, grams) }

    private fun reading(atMs: Long, grams: Double) = TimedReading(atMs, ScaleReading(grams, 0.0, 0, overload = false))

    private fun water(readings: List<TimedReading>) = FrenchPressWater.of(readings, 0L, readings.last().atMs)

    @Test
    fun aPourThatEndsInARemovalIsTheWaterPoured() {
        val brew = ramp(0, 0.0, 120.0, 12.0) + ramp(12_000, 120.0, 0.0, 0.4)
        assertEquals(120.0, water(brew)!!, 0.01)
    }

    @Test
    fun aSpoonInTheBloomThatComesOffIsNotWater() {
        val bloom = ramp(0, 0.0, 62.0, 4.0) + hold(4_000, 62.0, 2.0) +
            hold(6_000, 84.0, 0.5) + hold(6_500, 62.0, 2.0) + ramp(8_500, 62.0, 154.0, 4.0) + hold(12_500, 154.0, 4.0)
        assertEquals(154.0, water(bloom)!!, 0.3)
    }

    @Test
    fun aLidThatLandsAfterTheSteepStartsIsNotWater() {
        val steep = ramp(0, 0.0, 154.0, 4.0) + hold(4_000, 154.0, 20.0)
        val lid = hold(24_000, 154.0 + 189.0 + 40.0, 0.5) + hold(24_500, 154.0 + 189.0, 10.0)
        assertEquals(154.0, water(steep + lid)!!, 0.3)
    }

    @Test
    fun aLiftThatComesBackIsNotTheEndOfTheBrew() {
        val brew = ramp(0, 0.0, 154.0, 4.0) + hold(4_000, 154.0, 2.0) + hold(6_000, -157.0, 3.0) +
            hold(9_000, 154.0, 0.2) + hold(9_200, 154.0, 10.0)
        assertEquals(154.0, water(brew)!!, 0.3)
    }

    @Test
    fun nothingOnTheScaleHasNoWater() {
        assertEquals(null, water(hold(0, 0.0, 3.0)))
    }
}
