package io.github.mateuxlucax.caparao.brew

/** Where [ratio] falls relative to the target's ±5% band. */
enum class RatioBand { BELOW, AT, ABOVE }

/** Classifies a ratio against the shared target band. A missing ratio is treated as on target. */
fun ratioBand(ratio: Double?, target: Double): RatioBand {
    if (ratio == null) return RatioBand.AT
    val deviation = (ratio - target) / target
    return when {
        deviation < -0.05 - 1e-9 -> RatioBand.BELOW
        deviation > 0.05 + 1e-9 -> RatioBand.ABOVE
        else -> RatioBand.AT
    }
}
