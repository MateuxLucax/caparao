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
    private val readings = ArrayList<TimedReading>()
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

    fun onReading(reading: TimedReading): SessionReading {
        val recorded = record(reading)
        return SessionReading(detector.onReading(reading), recorded)
    }
    /** Advances the filter clock and flow from each reading; reports a pre-pour setup lift. */
    fun onFilterReading(reading: TimedReading): FilterReadingState {
        require(mode == BrewMode.FILTER) { "Filter readings require a filter session" }
        val recorded = record(reading)
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
        if (detector.phase == BrewPhase.READY) {
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
            recorded = recorded,
        )
    }

    fun pauseIntervals(): List<Pair<Long, Long>> = pauseIntervals.toList()
    fun readings(): List<TimedReading> = readings.toList()

    private fun record(reading: TimedReading): Boolean {
        if (readings.size >= MAX_READINGS) return false
        readings += reading
        return true
    }

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
        return ratio == null || ratioBand(ratio, targetRatio) != RatioBand.BELOW
    }

    fun clearRun() {
        readings.clear()
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

    fun finish(
        frenchPressRemovedAtMs: Long? = null,
        frenchPressRemovalAtMs: Long? = null,
        frenchPressSettledWeights: Map<Long, Double> = emptyMap(),
    ): BrewSessionResult? {
        val detected = detector.result ?: return null
        val endedAtMs = frenchPressRemovedAtMs?.takeIf { it in detected.startedAtMs until detected.endedAtMs }
            ?: detected.endedAtMs
        val bounded = detected.copy(endedAtMs = endedAtMs)
        val result = if (mode == BrewMode.FRENCH_PRESS) {
            // Settled water, not the running maximum, is the saved yield (#214).
            FrenchPressWater.of(readings, bounded.startedAtMs, bounded.endedAtMs)
                ?.let { bounded.copy(yieldGrams = it) } ?: bounded
        } else bounded
        val summaryReadings = when {
            // Cut at confirmed removal; later readings are not brewed water (#223).
            mode == BrewMode.FRENCH_PRESS -> readings.asSequence()
                .filter { it.atMs in result.startedAtMs..result.endedAtMs && (frenchPressRemovalAtMs == null || it.atMs < frenchPressRemovalAtMs) }
                .mapNotNull { reading ->
                    frenchPressSettledWeights[reading.atMs]?.let { reading.copy(reading = reading.reading.copy(weightGrams = it)) }
                }
                .toList()
            mode == BrewMode.FILTER && (pauseIntervals.isNotEmpty() || openPauseAtMs != null) -> {
                // A lift is judged on the water before it; the detector's pause reading may already be lower (#277).
                val rebased = readings.asSequence()
                    .filter { reading ->
                        val paused = pauseIntervals.any { (start, end) -> reading.atMs in start until end } ||
                            openPauseAtMs?.let { reading.atMs >= it } == true
                        !paused
                    }
                    .map { reading ->
                        val removed = pauseIntervals.filter { (_, end) -> end <= reading.atMs }.sumOf { (start, end) -> end - start }
                        reading.copy(atMs = reading.atMs - removed)
                    }
                    .filter { it.atMs in result.startedAtMs..result.endedAtMs }
                    .toList()
                val last = rebased.lastOrNull()
                if (last != null && last.atMs < result.endedAtMs) {
                    rebased + last.copy(
                        atMs = result.endedAtMs,
                        reading = last.reading.copy(weightGrams = result.yieldGrams, flowGramsPerSecond = 0.0),
                    )
                } else rebased
            }
            else -> readings
        }
        val flowResetTimesMs = if (mode == BrewMode.FILTER) {
            (pauseIntervals.map { it.first } + listOfNotNull(openPauseAtMs)).map { pauseStart ->
                pauseStart - pauseIntervals.filter { (_, end) -> end <= pauseStart }.sumOf { (start, end) -> end - start }
            }
        } else emptyList()
        return BrewSessionResult(result, BrewSummary.of(mode, summaryReadings, result, flowResetTimesMs))
    }
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
    val recorded: Boolean,
)

data class SessionReading(val command: ScaleCommand?, val recorded: Boolean)

private const val FILTER_PREPARE_LIFT_MS = 2_000L
private const val MAX_READINGS = 12_000

data class BrewSessionResult(val result: BrewResult, val summary: BrewSummary)
