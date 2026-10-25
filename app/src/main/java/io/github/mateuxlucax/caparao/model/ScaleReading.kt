package io.github.mateuxlucax.caparao.model

/**
 * One weight notification from the scale (about 10 per second). [weightGrams] can be negative (load removed after
 * the Dot zeroed itself); [flowGramsPerSecond] is the scale's raw value, which saturates on sudden loads, so clamp and
 * smooth it before use (docs/hardware.md).
 */
data class ScaleReading(
    val weightGrams: Double,
    val flowGramsPerSecond: Double,
    val timerSeconds: Int,
    val overload: Boolean,
)

/** Answer to the battery query: [bars] as the scale reports them, [percent] 0-100. */
data class ScaleBattery(val percent: Int, val bars: Int)
