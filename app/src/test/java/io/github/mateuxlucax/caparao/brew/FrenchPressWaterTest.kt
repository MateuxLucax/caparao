package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.ScaleReading
import io.github.mateuxlucax.caparao.model.StepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The live weight at each reading, as the ViewModel shows it: the settled water so far. */
private fun liveWater(readings: List<TimedReading>): List<Double> =
    readings.indices.map { i -> FrenchPressWater.of(readings.subList(0, i + 1), 0L, readings[i].atMs) ?: 0.0 }

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
    fun aBigSpoonInTheBloomThatComesOffIsNotWaterEither() {
        // A 45 g spoon (twice the 22 g one) on the bloom for two seconds, then the pour to 154 g.
        val bloom = ramp(0, 0.0, 62.0, 8.0) + hold(8_000, 62.0, 2.0) + hold(10_000, 107.0, 2.0) +
            hold(12_000, 62.0, 2.0) + ramp(14_000, 62.0, 154.0, 20.0) + hold(34_000, 154.0, 4.0)
        assertEquals(154.0, water(bloom)!!, 0.3)
    }

    @Test
    fun aLidThatLandsAfterTheSteepStartsIsNotWater() {
        val steep = ramp(0, 0.0, 154.0, 8.0) + hold(8_000, 154.0, 20.0)
        val lid = hold(28_000, 154.0 + 189.0 + 40.0, 0.5) + hold(28_500, 154.0 + 189.0, 10.0)
        assertEquals(154.0, water(steep + lid)!!, 0.3)
    }

    @Test
    fun aLiftThatComesBackIsNotTheEndOfTheBrew() {
        val brew = ramp(0, 0.0, 154.0, 8.0) + hold(8_000, 154.0, 2.0) + hold(10_000, -157.0, 3.0) +
            hold(13_000, 154.0, 0.2) + hold(13_200, 154.0, 10.0)
        assertEquals(154.0, water(brew)!!, 0.3)
    }

    @Test
    fun nothingOnTheScaleHasNoWater() {
        assertEquals(0.0, water(hold(0, 0.0, 3.0))!!, 0.3)
    }

    @Test
    fun aSpoonInTheBloomDoesNotReachTheStepTarget() {
        val bloom = ramp(0, 0.0, 62.0, 4.0) + hold(4_000, 62.0, 2.0) + hold(6_000, 84.0, 0.3) + hold(6_400, 84.0, 1.5) +
            hold(8_000, 62.0, 2.0) + ramp(10_000, 62.0, 100.0, 4.0)
        val live = liveWater(bloom)
        val tracker = StepTracker(listOf(RecipeStep(StepKind.POUR, 80.0, 0L)))
        val reachedAt = bloom.indices.firstNotNullOfOrNull { i ->
            tracker.update(bloom[i].atMs, live[i], 0.0).any { it is StepEvent.Reached }.takeIf { it }?.let { bloom[i].atMs }
        }
        assertTrue("the spoon never reached the target; the pour did (at $reachedAt ms)", reachedAt != null && reachedAt >= 10_000L)
    }

    @Test
    fun aLidThatLandsAfterTheSteepStartsLeavesTheLiveWeightWhereItWas() {
        val brew = ramp(0, 0.0, 154.0, 8.0) + hold(8_000, 154.0, 20.0) + hold(28_000, 154.0 + 189.0 + 40.0, 0.5) +
            hold(28_500, 154.0 + 189.0, 10.0)
        val live = liveWater(brew).drop(brew.indexOfFirst { it.atMs > 28_000 })
        assertTrue(live.all { abs(it - 154.0) <= 0.3 })
    }
}
