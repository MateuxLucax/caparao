package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.ScaleReading
import kotlin.math.round
import kotlin.random.Random

/**
 * Builds a 10 Hz stream of [TimedReading]s from segments, like a Dot would send. Weights are rounded to 0.1 g; noise
 * is uniform in ±[noise] and seeded so tests are repeatable. The scale's flow is a plausible value unless overridden.
 */
class SyntheticSession(startMs: Long = 0, seed: Int = 1) {
    val readings = mutableListOf<TimedReading>()

    /** Clock time of the next reading. */
    var nowMs = startMs
        private set

    private val random = Random(seed)

    /** Holds [weight] for [seconds]. */
    fun hold(weight: Double, seconds: Double, noise: Double = 0.0, flow: Double? = null, overload: Boolean = false) =
        apply { emit(seconds, noise, flow, overload) { weight } }

    /** Goes linearly from [from] to [to] over [seconds]; the last reading is [to]. */
    fun ramp(
        from: Double,
        to: Double,
        seconds: Double,
        noise: Double = 0.0,
        flow: Double? = null,
        overload: Boolean = false,
    ) = apply { emit(seconds, noise, flow ?: ((to - from) / seconds).coerceIn(0.0, 99.9), overload) { from + (to - from) * it } }

    private fun emit(seconds: Double, noise: Double, flow: Double?, overload: Boolean, weightAt: (Double) -> Double) {
        val n = round(seconds * 10).toInt()
        for (k in 1..n) {
            val jitter = if (noise > 0) random.nextDouble(-noise, noise) else 0.0
            val grams = round((weightAt(k.toDouble() / n) + jitter) * 10) / 10
            readings += TimedReading(nowMs, ScaleReading(grams, flow ?: 0.0, 0, overload))
            nowMs += STEP_MS
        }
    }

    companion object {
        const val STEP_MS = 100L
    }
}

/** Feeds every reading to [detector] and returns the commands with the time each came. */
fun BrewDetector.feed(readings: List<TimedReading>): List<Pair<Long, ScaleCommand>> =
    readings.mapNotNull { r -> onReading(r)?.let { r.atMs to it } }

fun BrewDetector.feed(session: SyntheticSession) = feed(session.readings.toList()).also { session.readings.clear() }
