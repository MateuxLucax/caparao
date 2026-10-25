package io.github.mateuxlucax.caparao.brew

/**
 * Flow in g/s computed from weights, because the scale's own flow saturates at 99.9 on sudden loads
 * (docs/hardware.md). It is the least-squares slope of weight over time across the last [windowMs]: with ~10 readings
 * per second that averages out the 0.1 g quantisation far better than an end-to-end difference, which jumps by
 * 0.1 g/s whenever one endpoint ticks. Never negative (a falling weight is a lift, not a flow), and 0 until it has
 * [minSpanMs] of data. Feed readings in order.
 */
class FlowSmoother(private val windowMs: Long = 1_000, private val minSpanMs: Long = 500) {
    private val points = ArrayDeque<Pair<Long, Double>>()

    /** Adds a weight at [atMs] and returns the flow now. */
    fun add(atMs: Long, weightGrams: Double): Double {
        points.addLast(atMs to weightGrams)
        while (atMs - points.first().first > windowMs) points.removeFirst()
        return flow()
    }

    fun add(reading: TimedReading): Double = add(reading.atMs, reading.reading.weightGrams)

    /** The flow over the current window, in g/s. */
    fun flow(): Double {
        if (points.isEmpty() || points.last().first - points.first().first < minSpanMs) return 0.0
        // Seconds relative to the first point keep the sums small.
        val t0 = points.first().first
        val n = points.size
        val meanT = points.sumOf { (it.first - t0) / 1000.0 } / n
        val meanW = points.sumOf { it.second } / n
        var num = 0.0
        var den = 0.0
        for ((at, w) in points) {
            val dt = (at - t0) / 1000.0 - meanT
            num += dt * (w - meanW)
            den += dt * dt
        }
        return if (den == 0.0) 0.0 else (num / den).coerceAtLeast(0.0)
    }

    fun reset() = points.clear()
}
