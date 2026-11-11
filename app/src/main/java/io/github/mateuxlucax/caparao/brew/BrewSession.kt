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
    private val freeSteps: List<RecipeStep> = emptyList(),
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
    private val alerted = HashSet<StepEvent>()
    private var openPauseAtMs: Long? = null
    private var brewingSinceMs: Long? = null
    private var liftBeganAtMs: Long? = null
    private var guideTracker: StepTracker? = null
    private var finishTracker: FrenchPressFinishTracker? = null
    private var pressDeadlineAlertSent = false
    private var pressLastAlertAtMs: Long? = null
    private var frenchPressWaterGrams = 0.0
    private var previousWeightGrams: Double? = null
    private var frenchPressWaterTracker: FrenchPressWaterTracker? = null
    private val frenchPressSettled = HashMap<Long, Double>()

    companion object {
        const val FRENCH_PRESS_ALERT_INTERVAL_MS = 15_000L
    }

    val phase: BrewPhase get() = detector.phase
    val pauseState: BrewPauseState? get() = detector.pauseState
    val result: BrewResult? get() = detector.result
    val tareTimedOut: Boolean get() = detector.tareTimedOut
    val startedAtMs: Long? get() = filterDetector?.startedAtMs
    val lastResumedAtMs: Long? get() = filterDetector?.lastResumedAtMs

    val guideSteps: List<RecipeStep> get() = if (mode == BrewMode.FRENCH_PRESS && steps.isEmpty()) freeSteps else steps

    fun onReading(reading: TimedReading): BrewReadingState {
        val previousWeight = previousWeightGrams ?: reading.reading.weightGrams
        previousWeightGrams = reading.reading.weightGrams
        val recorded = record(reading)
        val before = detector.phase
        val command = detector.onReading(reading)
        val pause = detector.pauseState
        if (mode == BrewMode.FILTER) when {
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
        if (mode != BrewMode.ESPRESSO && detector.phase == BrewPhase.READY) {
            if (reading.reading.weightGrams < 0.0) {
                val since = liftBeganAtMs ?: reading.atMs.also { liftBeganAtMs = it }
                if (reading.atMs - since >= FILTER_PREPARE_LIFT_MS) {
                    clearRun()
                    returnedToPreparation = true
                }
            } else liftBeganAtMs = null
        }
        if (returnedToPreparation) return readingState(command, reading, 0.0, 0, null, emptyList(), true, false, weightGrams = reading.reading.weightGrams)

        if (detector.phase == BrewPhase.BREWING && brewingSinceMs == null) {
            brewingSinceMs = filterDetector?.startedAtMs ?: reading.atMs
            smoother.reset()
            val stepsToTrack = when (mode) {
                BrewMode.FILTER -> steps
                BrewMode.FRENCH_PRESS -> guideSteps
                BrewMode.ESPRESSO -> emptyList()
            }
            if (stepsToTrack.isNotEmpty()) guideTracker = StepTracker(stepsToTrack, filterConfig.stepTargetHoldMs.takeIf { mode == BrewMode.FILTER } ?: 0L)
        }

        val since = brewingSinceMs
        val shownWeight = if (mode == BrewMode.FILTER && detector.phase == BrewPhase.PAUSED) pause?.waterWeightGrams ?: reading.reading.weightGrams
            else reading.reading.weightGrams
        var displayedWeight = shownWeight
        if (mode == BrewMode.FRENCH_PRESS && detector.phase == BrewPhase.BREWING) {
            val waterTracker = frenchPressWaterTracker
            if (waterTracker == null) {
                frenchPressWaterTracker = FrenchPressWaterTracker(since ?: reading.atMs).also { tracker ->
                    readings.forEach { tracker.update(it) }
                    frenchPressWaterGrams = tracker.waterGrams ?: frenchPressWaterGrams
                }
            } else if (recorded) frenchPressWaterGrams = waterTracker.update(reading) ?: frenchPressWaterGrams
            frenchPressSettled[reading.atMs] = frenchPressWaterGrams
            displayedWeight = frenchPressWaterGrams
        }
        val flowReading = if (mode == BrewMode.FRENCH_PRESS && detector.phase == BrewPhase.BREWING) {
            reading.copy(reading = reading.reading.copy(weightGrams = displayedWeight))
        } else reading
        val flow = if (since != null && detector.phase == BrewPhase.BREWING) smoother.add(flowReading) else 0.0
        val activeAt = when {
            since == null -> null
            detector.phase == BrewPhase.PAUSED -> pause?.startedAtMs ?: reading.atMs
            else -> reading.atMs
        }
        val guideWeight = if (detector.phase == BrewPhase.PAUSED || mode == BrewMode.FRENCH_PRESS) displayedWeight else reading.reading.weightGrams
        val (guide, guideEvents) = advanceGuide(reading.atMs, since, activeAt, guideWeight, flow, reading.reading.weightGrams)
        val events = guideEvents + readingEvents(before, displayedWeight, activeAt?.let { it - since!! } ?: 0L)
        val filterEnds = mode == BrewMode.FILTER && before == BrewPhase.BREWING && detector.phase == BrewPhase.PAUSED &&
            filterLiftEndsBrew(previousWeight)
        val result = readingState(
            command, reading, flow, activeAt?.let { it - since!! } ?: 0L,
            if (detector.phase == BrewPhase.PAUSED) {
                ((pause?.startedAtMs?.let { filterConfig.pauseTimeoutMs - (reading.atMs - it) } ?: 0L).coerceAtLeast(0L))
            } else null,
            events, returnedToPreparation, filterEnds, displayedWeight, guide,
        )
        if (mode == BrewMode.FRENCH_PRESS) {
            val deadlineEvents = pressDeadlineEvents(reading.atMs, reading.reading.weightGrams, guide?.allDone(guideSteps.size) == true)
            val finish = finishTracker
            val removed = finish?.removedAtMs != null
            val pending = finish?.removalStartedAtMs != null && !removed
            return result.copy(
                frenchPressRemoved = removed,
                frenchPressRemovalPending = pending,
                guideEvents = result.guideEvents + deadlineEvents,
            )
        }
        return result
    }

    private fun advanceGuide(
        atMs: Long,
        sinceMs: Long?,
        activeAtMs: Long?,
        weightGrams: Double,
        flowGramsPerSecond: Double,
        removalWeightGrams: Double?,
    ): Pair<StepState?, List<StepEvent>> {
        val tracker = guideTracker ?: return null to emptyList()
        val activeAt = activeAtMs ?: return tracker.state to emptyList()
        val events = tracker.update(activeAt - (sinceMs ?: return tracker.state to emptyList()), weightGrams, flowGramsPerSecond)
        if (mode == BrewMode.FRENCH_PRESS && StepEvent.AllDone in events && finishTracker == null) {
            finishTracker = FrenchPressFinishTracker().also {
                if (removalWeightGrams != null) it.backdateFrom(readings, sinceMs, atMs)
            }
        }
        return tracker.state to events.filterNot { mode == BrewMode.FRENCH_PRESS && it == StepEvent.AllDone }
    }

    fun phaseEvents(from: BrewPhase, to: BrewPhase): List<StepEvent> = when (mode) {
        BrewMode.ESPRESSO -> when {
            from == BrewPhase.WAITING_FOR_CUP && to == BrewPhase.TARING -> once(StepEvent.CupDetected)
            from == BrewPhase.TARING && to == BrewPhase.READY -> once(StepEvent.TareConfirmed)
            from == BrewPhase.READY && to == BrewPhase.BREWING -> once(StepEvent.FirstDrop)
            else -> emptyList()
        }
        BrewMode.FILTER -> when {
            from == BrewPhase.READY && to == BrewPhase.BREWING && steps.isEmpty() -> once(StepEvent.FirstWater)
            from == BrewPhase.BREWING && to == BrewPhase.PAUSED -> listOf(StepEvent.Paused)
            from == BrewPhase.PAUSED && to == BrewPhase.BREWING -> listOf(StepEvent.Resumed)
            else -> emptyList()
        }
        BrewMode.FRENCH_PRESS -> emptyList()
    }

    private fun readingEvents(from: BrewPhase, weightGrams: Double, elapsedMs: Long): List<StepEvent> = buildList {
        addAll(phaseEvents(from, detector.phase))
        val ratio = weightGrams.takeIf { it > 0.0 && doseGrams > 0.0 }?.div(doseGrams)
        if (detector.phase == BrewPhase.BREWING && mode == BrewMode.ESPRESSO && ratio != null && ratioBand(ratio, targetRatio) == RatioBand.AT) {
            once(StepEvent.InTargetBand).let(::addAll)
        }
        if (detector.phase == BrewPhase.BREWING && mode == BrewMode.FILTER) {
            if (steps.isEmpty() && ratio != null) when (ratioBand(ratio, targetRatio)) {
                RatioBand.BELOW -> Unit
                RatioBand.AT -> once(StepEvent.InTargetBand).let(::addAll)
                RatioBand.ABOVE -> {
                    once(StepEvent.InTargetBand).let(::addAll)
                    once(StepEvent.PastTargetBand).let(::addAll)
                }
            }
            val min = targetMinMs
            val max = targetMaxMs
            if (steps.isNotEmpty() && guideTracker?.state?.allDone(steps.size) == true && min != null && max != null && min <= max) {
                if (elapsedMs > max) once(StepEvent.DrainTimePassed).let(::addAll)
                else if (elapsedMs >= min) once(StepEvent.DrainTimeEntered).let(::addAll)
            }
        }
    }

    private fun once(event: StepEvent): List<StepEvent> = if (alerted.add(event)) listOf(event) else emptyList()

    private fun pressDeadlineEvents(atMs: Long, weightGrams: Double?, deadlinePassed: Boolean): List<StepEvent> {
        val finish = finishTracker ?: return emptyList()
        val removed = weightGrams?.let { finish.update(atMs, it) } ?: (finish.removedAtMs != null)
        if (!deadlinePassed || removed) return emptyList()
        if (finish.removalStartedAtMs != null) {
            pressLastAlertAtMs = null
            return emptyList()
        }
        val last = pressLastAlertAtMs
        if (last != null && atMs - last < FRENCH_PRESS_ALERT_INTERVAL_MS) return emptyList()
        val event = if (pressDeadlineAlertSent) StepEvent.FrenchPressReminder else StepEvent.FrenchPressDeadline
        pressDeadlineAlertSent = true
        pressLastAlertAtMs = atMs
        return listOf(event)
    }

    fun onClock(atMs: Long): BrewClockState {
        val since = brewingSinceMs ?: return BrewClockState(0L, guideTracker?.state, emptyList())
        val (guide, events) = advanceGuide(atMs, since, atMs, frenchPressWaterGrams, 0.0, removalWeightGrams = null)
        val deadlineEvents = if (mode == BrewMode.FRENCH_PRESS) {
            pressDeadlineEvents(atMs, null, guide?.allDone(guideSteps.size) == true)
        } else emptyList()
        val finish = finishTracker
        val removed = finish?.removedAtMs != null
        return BrewClockState(
            elapsedMs = atMs - since,
            guide = guide,
            guideEvents = events + deadlineEvents,
            frenchPressRemoved = removed,
            frenchPressRemovalPending = finish?.removalStartedAtMs != null && !removed,
        )
    }

    private fun readingState(
        command: ScaleCommand?,
        reading: TimedReading,
        flowGramsPerSecond: Double,
        elapsedMs: Long,
        pauseRemainingMs: Long?,
        guideEvents: List<StepEvent>,
        returnedToPreparation: Boolean,
        filterLiftEndsBrew: Boolean,
        weightGrams: Double = reading.reading.weightGrams,
        guide: StepState? = guideTracker?.state,
    ) = BrewReadingState(
        command = command,
        phase = detector.phase,
        tareTimedOut = detector.tareTimedOut,
        weightGrams = weightGrams,
        flowGramsPerSecond = flowGramsPerSecond,
        elapsedMs = elapsedMs,
        pauseRemainingMs = pauseRemainingMs,
        guide = guide,
        guideEvents = guideEvents,
        returnedToPreparation = returnedToPreparation,
        filterLiftEndsBrew = filterLiftEndsBrew,
        frenchPressRemoved = finishTracker?.removedAtMs != null,
        frenchPressRemovalPending = finishTracker?.removalStartedAtMs != null && finishTracker?.removedAtMs == null,
    )

    fun pauseIntervals(): List<Pair<Long, Long>> = pauseIntervals.toList()

    private fun record(reading: TimedReading): Boolean {
        if (readings.size >= MAX_READINGS) return false
        readings += reading
        return true
    }

    private fun filterLiftEndsBrew(weightGrams: Double): Boolean {
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
        alerted.clear()
        smoother.reset()
        pauseIntervals.clear()
        openPauseAtMs = null
        brewingSinceMs = null
        liftBeganAtMs = null
        guideTracker = null
        finishTracker = null
        pressDeadlineAlertSent = false
        pressLastAlertAtMs = null
        frenchPressWaterGrams = 0.0
        previousWeightGrams = null
        frenchPressWaterTracker = null
        frenchPressSettled.clear()
    }

    fun armNow(atMs: Long): ScaleCommand? {
        if (mode != BrewMode.ESPRESSO) return null
        return requireNotNull(espressoDetector).armNow(atMs)
    }

    fun beginWaitingFromBaseline(reading: TimedReading?) {
        if (mode == BrewMode.ESPRESSO) espressoDetector?.beginWaitingFromBaseline(reading)
    }
    fun retryTare(atMs: Long): ScaleCommand = detector.retryTare(atMs)
    fun stop(atMs: Long): List<StepEvent> {
        val before = detector.phase
        detector.stop(atMs)
        return phaseEvents(before, detector.phase) + if (mode == BrewMode.FILTER && steps.isEmpty() && before in setOf(BrewPhase.BREWING, BrewPhase.PAUSED)) {
            once(StepEvent.AllDone)
        } else emptyList()
    }
    fun interrupt(atMs: Long) = detector.interrupt(atMs)

    fun finish(): BrewSessionResult? {
        val detected = detector.result ?: return null
        val removedAtMs = finishTracker?.removedAtMs
        val removalAtMs = removedAtMs ?: finishTracker?.removalStartedAtMs
        val endedAtMs = removedAtMs?.takeIf { it in detected.startedAtMs until detected.endedAtMs }
            ?: detected.endedAtMs
        val bounded = detected.copy(endedAtMs = endedAtMs)
        val result = if (mode == BrewMode.FRENCH_PRESS) {
            FrenchPressWater.of(readings, bounded.startedAtMs, bounded.endedAtMs)
                ?.let { bounded.copy(yieldGrams = it) } ?: bounded
        } else bounded
        val summaryReadings = when {
            mode == BrewMode.FRENCH_PRESS -> readings.asSequence()
                .filter { it.atMs in result.startedAtMs..result.endedAtMs && (removalAtMs == null || it.atMs < removalAtMs) }
                .mapNotNull { reading ->
                    frenchPressSettled[reading.atMs]?.let { reading.copy(reading = reading.reading.copy(weightGrams = it)) }
                }
                .toList()
            mode == BrewMode.FILTER && (pauseIntervals.isNotEmpty() || openPauseAtMs != null) -> {
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

data class BrewReadingState(
    val command: ScaleCommand?,
    val phase: BrewPhase,
    val tareTimedOut: Boolean,
    val weightGrams: Double,
    val flowGramsPerSecond: Double,
    val elapsedMs: Long,
    val pauseRemainingMs: Long?,
    val guide: StepState?,
    val guideEvents: List<StepEvent>,
    val returnedToPreparation: Boolean,
    val filterLiftEndsBrew: Boolean,
    val frenchPressRemoved: Boolean,
    val frenchPressRemovalPending: Boolean,
)

data class BrewClockState(
    val elapsedMs: Long,
    val guide: StepState?,
    val guideEvents: List<StepEvent>,
    val frenchPressRemoved: Boolean = false,
    val frenchPressRemovalPending: Boolean = false,
)

private const val FILTER_PREPARE_LIFT_MS = 2_000L
private const val MAX_READINGS = 12_000

data class BrewSessionResult(val result: BrewResult, val summary: BrewSummary)
