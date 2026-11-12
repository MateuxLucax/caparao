package dev.mateux.caparao.brew

import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.RecipeStep
import dev.mateux.caparao.model.ScaleReading
import dev.mateux.caparao.model.StepKind

/** French press run state: the steep continues without the scale and finishes when the user taps Done. */
class FrenchPressSession(
    override val doseGrams: Double,
    override val targetRatio: Double,
    override val steps: List<RecipeStep>,
    override val targetMinMs: Long?,
    override val targetMaxMs: Long?,
    private val freeWaterGrams: Double? = null,
    private val freeSteepMs: Long? = null,
    private val filterConfig: FilterConfig = FilterConfig(),
) : BrewSession {
    override val guideSteps: List<RecipeStep> = steps.ifEmpty {
        listOf(
            RecipeStep(StepKind.POUR, freeWaterGrams ?: 0.0, 0L),
            RecipeStep(StepKind.PRESS, null, freeSteepMs ?: 0L),
        )
    }

    private val readings = ArrayList<TimedReading>()
    private val settled = HashMap<Long, Double>()
    private var currentReading: TimedReading? = null
    private var currentRecorded = false
    private var waterTracker: FrenchPressWaterTracker? = null
    private var waterGrams = 0.0
    private var finishTracker: FrenchPressFinishTracker? = null
    private var deadlineAlertSent = false
    private var lastAlertAtMs: Long? = null

    private val detectorSession = DetectorBrewSession(
        mode = BrewMode.FILTER,
        doseGrams = doseGrams,
        targetRatio = targetRatio,
        steps = guideSteps,
        targetMinMs = targetMinMs,
        targetMaxMs = targetMaxMs,
        detectorOverride = FilterDetector(filterConfig, detectLifts = false),
        weightOverride = ::weightForPhase,
        stepTargetHoldMs = 0L,
        filterConfig = filterConfig,
        recordReadings = false,
    )

    override val phase get() = detectorSession.phase
    override val pauseState get() = detectorSession.pauseState
    override val result get() = detectorSession.result
    override val tareTimedOut get() = detectorSession.tareTimedOut
    override val startedAtMs get() = detectorSession.startedAtMs
    override val lastResumedAtMs get() = detectorSession.lastResumedAtMs

    override fun onReading(reading: TimedReading): BrewReadingState {
        currentReading = reading
        currentRecorded = readings.size < MAX_READINGS
        if (currentRecorded) readings += reading
        val state = detectorSession.onReading(reading)
        currentReading = null
        currentRecorded = false
        if (state.returnedToPreparation) {
            clearRun()
            return state.copy(frenchPressRemoved = false, frenchPressRemovalPending = false)
        }

        val events = state.guideEvents
        if (finishTracker == null && StepEvent.AllDone in events) {
            finishTracker = FrenchPressFinishTracker().also { tracker ->
                startedAtMs?.let { tracker.backdateFrom(readings, it, reading.atMs) }
            }
        }
        val finish = finishTracker
        val removed = finish?.update(reading.atMs, reading.reading.weightGrams) ?: false
        val pressAlerts = deadlineEvents(reading.atMs, finish?.removalStartedAtMs != null || removed, state.guide?.allDone(guideSteps.size) == true)
        return state.copy(
            guideEvents = events.filterNot(::filterEvent) + pressAlerts,
            frenchPressRemoved = finish?.removedAtMs != null,
            frenchPressRemovalPending = finish?.removalStartedAtMs != null && finish.removedAtMs == null,
        )
    }

    private fun weightForPhase(phase: BrewPhase): Double? {
        if (phase != BrewPhase.BREWING) return null
        // French press yield is settled water rather than a running maximum (#214).
        var tracker = waterTracker
        if (tracker == null) {
            tracker = FrenchPressWaterTracker(startedAtMs ?: currentReading?.atMs ?: 0L)
            readings.forEach { tracker.update(it) }
            waterTracker = tracker
        } else if (currentRecorded) {
            currentReading?.let(tracker::update)
        }
        waterGrams = tracker.waterGrams ?: waterGrams
        currentReading?.let { settled[it.atMs] = waterGrams }
        return waterGrams
    }

    override fun phaseEvents(from: BrewPhase, to: BrewPhase): List<StepEvent> =
        detectorSession.phaseEvents(from, to).filterNot(::filterEvent)

    override fun pauseIntervals(): List<Pair<Long, Long>> = emptyList()

    override fun clearRun() {
        detectorSession.clearRun()
        readings.clear()
        settled.clear()
        waterTracker = null
        waterGrams = 0.0
        finishTracker = null
        deadlineAlertSent = false
        lastAlertAtMs = null
    }

    override fun armNow(atMs: Long): ScaleCommand? = null
    override fun beginWaitingFromBaseline(reading: TimedReading?) = Unit
    override fun retryTare(atMs: Long): ScaleCommand = detectorSession.retryTare(atMs)
    override fun stop(atMs: Long): List<StepEvent> = detectorSession.stop(atMs).filterNot(::filterEvent)

    override fun onScaleLost(atMs: Long) {
        if (phase == BrewPhase.BREWING && guideSteps.isNotEmpty()) return
        detectorSession.onScaleLost(atMs)
    }

    override fun onClock(atMs: Long): BrewClockState {
        val state = detectorSession.onClock(atMs)
        if (finishTracker == null && state.guide?.allDone(guideSteps.size) == true) {
            finishTracker = FrenchPressFinishTracker().also { startedAtMs?.let { start -> it.backdateFrom(readings, start, atMs) } }
        }
        val finish = finishTracker
        val alerts = deadlineEvents(atMs, finish?.removalStartedAtMs != null || finish?.removedAtMs != null, state.guide?.allDone(guideSteps.size) == true)
        return state.copy(
            guideEvents = state.guideEvents.filterNot(::filterEvent) + alerts,
            frenchPressRemoved = finish?.removedAtMs != null,
            frenchPressRemovalPending = finish?.removalStartedAtMs != null && finish.removedAtMs == null,
        )
    }

    private fun deadlineEvents(atMs: Long, removalPending: Boolean, deadlinePassed: Boolean): List<StepEvent> {
        val finish = finishTracker ?: return emptyList()
        if (!deadlinePassed || finish.removedAtMs != null) return emptyList()
        if (removalPending) {
            lastAlertAtMs = null
            return emptyList()
        }
        if (lastAlertAtMs?.let { atMs - it < ALERT_INTERVAL_MS } == true) return emptyList()
        lastAlertAtMs = atMs
        return listOf(if (deadlineAlertSent) StepEvent.FrenchPressReminder else StepEvent.FrenchPressDeadline)
            .also { deadlineAlertSent = true }
    }

    private fun filterEvent(event: StepEvent) = event in setOf(
        StepEvent.AllDone,
        StepEvent.FirstWater,
        StepEvent.InTargetBand,
        StepEvent.PastTargetBand,
        StepEvent.Paused,
        StepEvent.Resumed,
        StepEvent.DrainTimeEntered,
        StepEvent.DrainTimePassed,
    )

    override fun finish(): BrewSessionResult? {
        val detected = detectorSession.result ?: return null
        val removalAt = finishTracker?.removedAtMs ?: finishTracker?.removalStartedAtMs
        val end = finishTracker?.removedAtMs?.takeIf { it in detected.startedAtMs until detected.endedAtMs }
            ?: detected.endedAtMs
        val bounded = detected.copy(endedAtMs = end)
        val result = FrenchPressWater.of(readings, bounded.startedAtMs, bounded.endedAtMs)
            ?.let { bounded.copy(yieldGrams = it) } ?: bounded
        // The saved press curve ends when the carafe first leaves the scale for good (#223).
        val summaryReadings = readings.asSequence()
            .filter { it.atMs in result.startedAtMs..result.endedAtMs && (removalAt == null || it.atMs < removalAt) }
            .mapNotNull { reading -> settled[reading.atMs]?.let { reading.copy(reading = reading.reading.copy(weightGrams = it)) } }
            .toList()
        return BrewSessionResult(result, BrewSummary.of(BrewMode.FRENCH_PRESS, summaryReadings, result))
    }

    companion object {
        const val ALERT_INTERVAL_MS = 15_000L
    }
}
