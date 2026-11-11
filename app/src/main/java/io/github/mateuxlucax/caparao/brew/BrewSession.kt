package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.RecipeStep

/** Pure owner of one detector run; orchestration and persistence stay with the caller. */
class BrewSession(
    mode: BrewMode,
    val doseGrams: Double,
    val targetRatio: Double,
    val steps: List<RecipeStep>,
    val targetMinMs: Long?,
    val targetMaxMs: Long?,
) {
    private val detector: BrewDetector = when (mode) {
        BrewMode.ESPRESSO -> EspressoDetector()
        BrewMode.FILTER -> FilterDetector()
        BrewMode.FRENCH_PRESS -> FilterDetector(FilterConfig(liftDetectionEnabled = false))
    }

    val phase: BrewPhase get() = detector.phase
    val pauseState: BrewPauseState? get() = detector.pauseState
    val result: BrewResult? get() = detector.result
    val tareTimedOut: Boolean get() = detector.tareTimedOut
    val startedAtMs: Long? get() = (detector as? FilterDetector)?.startedAtMs
    val lastResumedAtMs: Long? get() = (detector as? FilterDetector)?.lastResumedAtMs

    fun onReading(reading: TimedReading): ScaleCommand? = detector.onReading(reading)
    fun armNow(atMs: Long): ScaleCommand? = (detector as? EspressoDetector)?.armNow(atMs)
    fun beginWaitingFromBaseline(reading: TimedReading?) = (detector as? EspressoDetector)?.beginWaitingFromBaseline(reading)
    fun retryTare(atMs: Long): ScaleCommand = detector.retryTare(atMs)
    fun stop(atMs: Long) = detector.stop(atMs)
    fun interrupt(atMs: Long) = detector.interrupt(atMs)
}
