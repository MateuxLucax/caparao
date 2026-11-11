package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.RecipeStep

/** Pure owner of one detector run and its filter clock bookkeeping. */
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
    private val smoother = FlowSmoother()
    private val pauseIntervals = mutableListOf<Pair<Long, Long>>()
    private var openPauseAtMs: Long? = null
    private var brewingSinceMs: Long? = null
    private var liftBeganAtMs: Long? = null
    private var guideTracker: StepTracker? = null

    val phase: BrewPhase get() = detector.phase
    val pauseState: BrewPauseState? get() = detector.pauseState
    val result: BrewResult? get() = detector.result
    val tareTimedOut: Boolean get() = detector.tareTimedOut
    val startedAtMs: Long? get() = filterDetector?.startedAtMs
    val lastResumedAtMs: Long? get() = filterDetector?.lastResumedAtMs

    fun onReading(reading: TimedReading): ScaleCommand? = detector.onReading(reading)
    /** Advances the filter clock and flow from each reading; reports a pre-pour setup lift. */
    fun onFilterReading(reading: TimedReading, isLive: Boolean): FilterReadingState {
        require(mode == BrewMode.FILTER) { "Filter readings require a filter session" }
        val before = detector.phase
        val command = detector.onReading(reading)
        val pause = detector.pauseState
        when {
            before == BrewPhase.BREWING && detector.phase == BrewPhase.PAUSED -> {
                openPauseAtMs = pause?.startedAtMs
                smoother.reset()
            }
            before == BrewPhase.PAUSED && detector.phase == BrewPhase.BREWING -> {
                val pauseStart = openPauseAtMs
                if (pauseStart != null) {
                    val resumedAt = filterDetector?.lastResumedAtMs ?: reading.atMs
                    pauseIntervals += pauseStart to resumedAt
                    brewingSinceMs = (brewingSinceMs ?: pauseStart) + (resumedAt - pauseStart)
                }
                openPauseAtMs = null
                smoother.reset()
            }
        }

        var returnedToPreparation = false
        if (isLive && detector.phase == BrewPhase.READY) {
            if (reading.reading.weightGrams < 0.0) {
                val since = liftBeganAtMs ?: reading.atMs.also { liftBeganAtMs = it }
                if (reading.atMs - since >= FILTER_PREPARE_LIFT_MS) {
                    clearRun()
                    returnedToPreparation = true
                }
            } else {
                liftBeganAtMs = null
            }
        }
        if (detector.phase == BrewPhase.BREWING && brewingSinceMs == null) {
            brewingSinceMs = filterDetector?.startedAtMs ?: reading.atMs
            smoother.reset()
            if (steps.isNotEmpty()) guideTracker = StepTracker(steps, filterConfig.stepTargetHoldMs)
        }
        val since = brewingSinceMs
        val shownWeight = if (detector.phase == BrewPhase.PAUSED) pause?.waterWeightGrams ?: reading.reading.weightGrams
            else reading.reading.weightGrams
        val flowReading = if (detector.phase == BrewPhase.BREWING) {
            reading.copy(reading = reading.reading.copy(weightGrams = shownWeight))
        } else reading
        val flow = if (since != null && detector.phase == BrewPhase.BREWING) smoother.add(flowReading) else 0.0
        val activeAt = when {
            since == null -> null
            detector.phase == BrewPhase.PAUSED -> pause?.startedAtMs ?: reading.atMs
            else -> reading.atMs
        }
        val guideEvents = if (since != null && guideTracker != null &&
            (detector.phase == BrewPhase.BREWING || detector.phase == BrewPhase.PAUSED)
        ) guideTracker!!.update(activeAt!! - since, shownWeight, flow) else emptyList()
        return FilterReadingState(
            command = command,
            phaseBefore = before,
            phase = detector.phase,
            tareTimedOut = detector.tareTimedOut,
            weightGrams = shownWeight,
            flowGramsPerSecond = flow,
            elapsedMs = if (activeAt == null) 0 else activeAt - since!!,
            pauseRemainingMs = if (detector.phase == BrewPhase.PAUSED) {
                ((pause?.startedAtMs?.let { filterConfig.pauseTimeoutMs - (reading.atMs - it) } ?: 0L).coerceAtLeast(0L))
            } else null,
            guide = guideTracker?.state,
            guideEvents = guideEvents,
            returnedToPreparation = returnedToPreparation,
        )
    }

    fun pauseIntervals(): List<Pair<Long, Long>> = pauseIntervals.toList()
    fun openPauseAtMs(): Long? = openPauseAtMs

    fun filterLiftEndsBrew(weightGrams: Double): Boolean {
        require(mode == BrewMode.FILTER) { "Filter lift checks require a filter session" }
        val guide = guideTracker
        if (guide != null) {
            val lastPour = steps.indexOfLast { it.kind.pours && it.targetWeightGrams != null }
            if (lastPour < 0) return false
            if (guide.state.index > lastPour) return true
            val target = steps[lastPour].targetWeightGrams ?: return false
            return guide.state.index == lastPour && weightGrams >= target - StepTracker.TOLERANCE_GRAMS
        }
        val ratio = if (doseGrams > 0.0 && weightGrams > 0.0) weightGrams / doseGrams else null
        return ratio == null || (ratio - targetRatio) / targetRatio >= -0.05 - 1e-9
    }

    fun clearRun() {
        smoother.reset()
        pauseIntervals.clear()
        openPauseAtMs = null
        brewingSinceMs = null
        liftBeganAtMs = null
        guideTracker = null
    }

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

data class FilterReadingState(
    val command: ScaleCommand?,
    val phaseBefore: BrewPhase,
    val phase: BrewPhase,
    val tareTimedOut: Boolean,
    val weightGrams: Double,
    val flowGramsPerSecond: Double,
    val elapsedMs: Long,
    val pauseRemainingMs: Long?,
    val guide: StepState?,
    val guideEvents: List<StepEvent>,
    val returnedToPreparation: Boolean,
)

private const val FILTER_PREPARE_LIFT_MS = 2_000L
