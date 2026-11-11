package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.RecipeStep

/** Pure owner of one detector run; orchestration and persistence stay with the caller. */
class BrewSession(
    private val mode: BrewMode,
    val doseGrams: Double,
    val targetRatio: Double,
    val steps: List<RecipeStep>,
    val targetMinMs: Long?,
    val targetMaxMs: Long?,
    private val filterConfig: FilterConfig = FilterConfig(),
) {
    private val espressoDetector = if (mode == BrewMode.ESPRESSO) EspressoDetector() else null
    private val filterDetector = if (mode == BrewMode.ESPRESSO) null else FilterDetector(
        if (mode == BrewMode.FRENCH_PRESS) filterConfig.copy(liftDetectionEnabled = false) else filterConfig,
    )
    private val detector: BrewDetector = when (mode) {
        BrewMode.ESPRESSO -> requireNotNull(espressoDetector)
        BrewMode.FILTER, BrewMode.FRENCH_PRESS -> requireNotNull(filterDetector)
    }

    val phase: BrewPhase get() = detector.phase
    val pauseState: BrewPauseState? get() = detector.pauseState
    val result: BrewResult? get() = detector.result
    val tareTimedOut: Boolean get() = detector.tareTimedOut
    val startedAtMs: Long? get() = filterDetector?.startedAtMs
    val lastResumedAtMs: Long? get() = filterDetector?.lastResumedAtMs

    fun onReading(reading: TimedReading): ScaleCommand? = detector.onReading(reading)
    fun armNow(atMs: Long): ScaleCommand? {
        if (mode != BrewMode.ESPRESSO) return null
        return requireNotNull(espressoDetector).armNow(atMs)
    }

    fun beginWaitingFromBaseline(reading: TimedReading?) {
        if (mode == BrewMode.ESPRESSO) espressoDetector?.beginWaitingFromBaseline(reading)
    }
    fun retryTare(atMs: Long): ScaleCommand = detector.retryTare(atMs)
    fun stop(atMs: Long) = detector.stop(atMs)
    fun interrupt(atMs: Long) = detector.interrupt(atMs)
}
