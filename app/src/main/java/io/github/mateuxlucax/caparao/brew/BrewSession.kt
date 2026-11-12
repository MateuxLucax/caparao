package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.RecipeStep

/** Pure owner of one detector run. */
interface BrewSession {
    val doseGrams: Double
    val targetRatio: Double
    val steps: List<RecipeStep>
    val targetMinMs: Long?
    val targetMaxMs: Long?
    val phase: BrewPhase
    val pauseState: BrewPauseState?
    val result: BrewResult?
    val tareTimedOut: Boolean
    val startedAtMs: Long?
    val lastResumedAtMs: Long?
    val guideSteps: List<RecipeStep>

    fun onReading(reading: TimedReading): BrewReadingState
    fun phaseEvents(from: BrewPhase, to: BrewPhase): List<StepEvent>
    fun pauseIntervals(): List<Pair<Long, Long>>
    fun clearRun()
    fun armNow(atMs: Long): ScaleCommand?
    fun beginWaitingFromBaseline(reading: TimedReading?)
    fun retryTare(atMs: Long): ScaleCommand
    fun stop(atMs: Long): List<StepEvent>
    fun onScaleLost(atMs: Long)
    fun onClock(atMs: Long): BrewClockState
    fun finish(): BrewSessionResult?

    companion object {
        const val FRENCH_PRESS_ALERT_INTERVAL_MS = FrenchPressSession.ALERT_INTERVAL_MS

        operator fun invoke(plan: BrewPlan, filterConfig: FilterConfig = FilterConfig()): BrewSession =
            plan.createSession(filterConfig)

        operator fun invoke(
            mode: BrewMode,
            doseGrams: Double,
            targetRatio: Double,
            steps: List<RecipeStep>,
            targetMinMs: Long?,
            targetMaxMs: Long?,
            freeWaterGrams: Double? = null,
            freeSteepMs: Long? = null,
            filterConfig: FilterConfig = FilterConfig(),
        ): BrewSession = createBrewSession(mode, doseGrams, targetRatio, steps, targetMinMs, targetMaxMs, freeWaterGrams, freeSteepMs, filterConfig)
    }
}

/** Espresso and filter run state, with all detector-specific rules kept here. */
internal class DetectorBrewSession(
    private val mode: BrewMode,
    override val doseGrams: Double,
    override val targetRatio: Double,
    override val steps: List<RecipeStep>,
    override val targetMinMs: Long?,
    override val targetMaxMs: Long?,
    detectorOverride: BrewDetector? = null,
    private val weightOverride: ((BrewPhase) -> Double?)? = null,
    private val stepTargetHoldMs: Long? = null,
    private val filterConfig: FilterConfig = FilterConfig(),
    private val recordReadings: Boolean = true,
) : BrewSession {
    private val detector: BrewDetector = detectorOverride ?:
        if (mode == BrewMode.ESPRESSO) EspressoDetector() else FilterDetector(filterConfig)
    private val smoother = FlowSmoother()
    private val pauseIntervals = mutableListOf<Pair<Long, Long>>()
    private val readings = ArrayList<TimedReading>()
    private val alerted = HashSet<StepEvent>()
    private var openPauseAtMs: Long? = null
    private var brewingSinceMs: Long? = null
    private var liftBeganAtMs: Long? = null
    private var guideTracker: StepTracker? = null
    private var previousWeightGrams: Double? = null
    override val phase: BrewPhase get() = detector.phase
    override val pauseState: BrewPauseState? get() = detector.pauseState
    override val result: BrewResult? get() = detector.result
    override val tareTimedOut: Boolean get() = detector.tareTimedOut
    override val startedAtMs: Long? get() = (detector as? FilterDetector)?.startedAtMs
    override val lastResumedAtMs: Long? get() = (detector as? FilterDetector)?.lastResumedAtMs

    override val guideSteps: List<RecipeStep> get() = steps

    override fun onReading(reading: TimedReading): BrewReadingState {
        val previousWeight = previousWeightGrams ?: reading.reading.weightGrams
        previousWeightGrams = reading.reading.weightGrams
        if (recordReadings) record(reading)
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
                    val resumedAt = (detector as FilterDetector).lastResumedAtMs ?: reading.atMs
                    pauseIntervals += pauseStart to resumedAt
                    brewingSinceMs = (brewingSinceMs ?: pauseStart) + (resumedAt - pauseStart)
                }
                openPauseAtMs = null
                smoother.reset()
            }
        }
        var returnedToPreparation = false
        if (mode == BrewMode.FILTER && detector.phase == BrewPhase.READY) {
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
            brewingSinceMs = (detector as? FilterDetector)?.startedAtMs ?: reading.atMs
            smoother.reset()
            val stepsToTrack = guideSteps.takeIf { mode == BrewMode.FILTER }.orEmpty()
            if (stepsToTrack.isNotEmpty()) guideTracker = StepTracker(stepsToTrack, stepTargetHoldMs ?: filterConfig.stepTargetHoldMs)
        }

        val since = brewingSinceMs
        val shownWeight = if (mode == BrewMode.FILTER && detector.phase == BrewPhase.PAUSED) pause?.waterWeightGrams ?: reading.reading.weightGrams
            else reading.reading.weightGrams
        val displayedWeight = weightOverride?.invoke(detector.phase) ?: shownWeight
        val flowReading = if (displayedWeight != reading.reading.weightGrams) reading.copy(reading = reading.reading.copy(weightGrams = displayedWeight)) else reading
        val flow = if (since != null && detector.phase == BrewPhase.BREWING) smoother.add(flowReading) else 0.0
        val activeAt = when {
            since == null -> null
            detector.phase == BrewPhase.PAUSED -> pause?.startedAtMs ?: reading.atMs
            else -> reading.atMs
        }
        val guideWeight = if (detector.phase == BrewPhase.PAUSED || weightOverride != null) displayedWeight else reading.reading.weightGrams
        val (guide, guideEvents) = advanceGuide(since, activeAt, guideWeight, flow)
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
        return result
    }

    private fun advanceGuide(
        sinceMs: Long?,
        activeAtMs: Long?,
        weightGrams: Double,
        flowGramsPerSecond: Double,
    ): Pair<StepState?, List<StepEvent>> {
        val tracker = guideTracker ?: return null to emptyList()
        val activeAt = activeAtMs ?: return tracker.state to emptyList()
        val events = tracker.update(activeAt - (sinceMs ?: return tracker.state to emptyList()), weightGrams, flowGramsPerSecond)
        return tracker.state to events
    }

    override fun phaseEvents(from: BrewPhase, to: BrewPhase): List<StepEvent> = if (mode == BrewMode.ESPRESSO) when {
            from == BrewPhase.WAITING_FOR_CUP && to == BrewPhase.TARING -> once(StepEvent.CupDetected)
            from == BrewPhase.TARING && to == BrewPhase.READY -> once(StepEvent.TareConfirmed)
            from == BrewPhase.READY && to == BrewPhase.BREWING -> once(StepEvent.FirstDrop)
            else -> emptyList()
        } else when {
            from == BrewPhase.READY && to == BrewPhase.BREWING && steps.isEmpty() -> once(StepEvent.FirstWater)
            from == BrewPhase.PAUSED && to == BrewPhase.DONE && steps.isEmpty() -> once(StepEvent.AllDone)
            from == BrewPhase.BREWING && to == BrewPhase.PAUSED -> listOf(StepEvent.Paused)
            from == BrewPhase.PAUSED && to == BrewPhase.BREWING -> listOf(StepEvent.Resumed)
            else -> emptyList()
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
            when (drainWindow(elapsedMs)) {
                DrainWindow.BEFORE, null -> Unit
                DrainWindow.INSIDE -> once(StepEvent.DrainTimeEntered).let(::addAll)
                DrainWindow.PAST -> once(StepEvent.DrainTimePassed).let(::addAll)
            }
        }
    }

    private fun drainWindow(elapsedMs: Long): DrainWindow? {
        if (mode != BrewMode.FILTER || detector.phase !in setOf(BrewPhase.BREWING, BrewPhase.PAUSED) ||
            steps.isEmpty() || guideTracker?.state?.allDone(steps.size) != true
        ) return null
        val min = targetMinMs ?: return null
        val max = targetMaxMs?.takeIf { min <= it } ?: return null
        return when {
            elapsedMs < min -> DrainWindow.BEFORE
            elapsedMs <= max -> DrainWindow.INSIDE
            else -> DrainWindow.PAST
        }
    }

    private fun once(event: StepEvent): List<StepEvent> = if (alerted.add(event)) listOf(event) else emptyList()

    override fun onClock(atMs: Long): BrewClockState {
        val since = brewingSinceMs ?: return BrewClockState(0L, guideTracker?.state, emptyList())
        val (guide, events) = advanceGuide(since, atMs, weightOverride?.invoke(BrewPhase.BREWING) ?: 0.0, 0.0)
        return BrewClockState(
            elapsedMs = atMs - since,
            drainWindow = drainWindow(atMs - since),
            guide = guide,
            guideEvents = events,
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
        drainWindow = drainWindow(elapsedMs),
    )

    override fun pauseIntervals(): List<Pair<Long, Long>> = pauseIntervals.toList()

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
        // The pause reading can already be below the settled pre-lift water (#277).
        val ratio = if (doseGrams > 0.0 && weightGrams > 0.0) weightGrams / doseGrams else null
        return ratio == null || ratioBand(ratio, targetRatio) != RatioBand.BELOW
    }

    override fun clearRun() {
        readings.clear()
        alerted.clear()
        smoother.reset()
        pauseIntervals.clear()
        openPauseAtMs = null
        brewingSinceMs = null
        liftBeganAtMs = null
        guideTracker = null
        previousWeightGrams = null
    }

    override fun armNow(atMs: Long): ScaleCommand? = (detector as? EspressoDetector)?.armNow(atMs)
    override fun beginWaitingFromBaseline(reading: TimedReading?) { (detector as? EspressoDetector)?.beginWaitingFromBaseline(reading) }
    override fun retryTare(atMs: Long): ScaleCommand = detector.retryTare(atMs)
    override fun stop(atMs: Long): List<StepEvent> {
        val before = detector.phase
        detector.stop(atMs)
        return phaseEvents(before, detector.phase) + if (mode == BrewMode.FILTER && steps.isEmpty() && before in setOf(BrewPhase.BREWING, BrewPhase.PAUSED)) {
            once(StepEvent.AllDone)
        } else emptyList()
    }

    override fun onScaleLost(atMs: Long) {
        if (phase == BrewPhase.BREWING || phase == BrewPhase.PAUSED) {
            detector.interrupt(atMs)
        }
    }

    override fun finish(): BrewSessionResult? {
        val result = detector.result ?: return null
        val summaryReadings = if (mode == BrewMode.FILTER && (pauseIntervals.isNotEmpty() || openPauseAtMs != null)) {
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
            } else readings
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
    val drainWindow: DrainWindow? = null,
    val frenchPressRemoved: Boolean = false,
    val frenchPressRemovalPending: Boolean = false,
)

data class BrewClockState(
    val elapsedMs: Long,
    val guide: StepState?,
    val guideEvents: List<StepEvent>,
    val drainWindow: DrainWindow? = null,
    val frenchPressRemoved: Boolean = false,
    val frenchPressRemovalPending: Boolean = false,
)

enum class DrainWindow { BEFORE, INSIDE, PAST }

private const val FILTER_PREPARE_LIFT_MS = 2_000L
internal const val MAX_READINGS = 12_000

data class BrewSessionResult(val result: BrewResult, val summary: BrewSummary)
