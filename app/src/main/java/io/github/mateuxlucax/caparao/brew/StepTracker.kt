package io.github.mateuxlucax.caparao.brew

import io.github.mateuxlucax.caparao.model.RecipeStep
import kotlin.math.abs
import kotlin.math.ceil

/** How the start of a pour compares with the recipe's time for it. */
enum class Pace { AHEAD, ON_TIME, BEHIND }

/** A moment worth an alert (docs/adr/0019-guided-brew.md), reported once by [StepTracker.update]. */
sealed interface StepEvent {
    /** Step [index] is now the current one. */
    data class Started(val index: Int) : StepEvent

    /** The pour of step [index] is within [StepTracker.NEAR_GRAMS] of its target. */
    data class NearTarget(val index: Int) : StepEvent

    /** Step [index] reached its target weight. */
    data class Reached(val index: Int) : StepEvent

    /** [secondsLeft] (5 to 1) until step [index] is due: a pour not begun yet, or the end of a wait. */
    data class Countdown(val index: Int, val secondsLeft: Int) : StepEvent

    /** The last step is done; the brew goes on (drawdown) until the filter detector ends it. */
    data object AllDone : StepEvent
}

/** Where the guide stands after a reading. */
data class StepState(
    /** The current step; equals the number of steps once all are done. */
    val index: Int,
    /** How far the current step is, 0 to 1: weight since the last target for a pour, time for a wait. */
    val progress: Float,
    /** Milliseconds until the current step is due, while it hasn't begun: a pour's start or a wait's end. */
    val dueInMs: Long?,
    /** Set once the current pour began: ahead or behind by [paceMs] (negative is ahead). */
    val pace: Pace?,
    val paceMs: Long?,
    /** Grams poured past the last reached target, when over [StepTracker.TOLERANCE_GRAMS], before the next pour. */
    val overshootGrams: Double?,
) {
    fun allDone(stepCount: Int) = index >= stepCount
}

/**
 * Follows a filter recipe's [steps] through the readings of a brew (docs/adr/0019-guided-brew.md). Pure and in order
 * like the detectors: feed it the time since the first water, the weight and the smoothed flow.
 *
 * A pour is done within [TOLERANCE_GRAMS] of its cumulative target, a wait or swirl when the next step is due; the
 * guide then moves on, and pouring past a target never blocks it. The pace is measured in time, once, when a pour
 * begins (flow over [POUR_FLOW] after resting, or [CONTINUE_GRAMS] past the last target), against the step's start.
 */
class StepTracker(private val steps: List<RecipeStep>) {
    private var index = 0
    private var lastTarget = 0.0
    private var stepSinceMs = 0L
    private var pourBeganAtMs: Long? = null
    private var rested = true
    private var nearSent = false
    private val countdownSent = HashSet<Int>()

    var state = StepState(0, 0f, null, null, null, null)
        private set

    /** Feeds the next reading; returns what happened, in order. */
    fun update(elapsedMs: Long, weightGrams: Double, flowGramsPerSecond: Double): List<StepEvent> {
        val events = mutableListOf<StepEvent>()
        while (index < steps.size) {
            val step = steps[index]
            val target = step.targetWeightGrams
            if (step.kind.pours && target != null) {
                markPourBegan(step, elapsedMs, weightGrams, flowGramsPerSecond)
                if (!nearSent && weightGrams >= target - NEAR_GRAMS && weightGrams < target - TOLERANCE_GRAMS) {
                    nearSent = true
                    events += StepEvent.NearTarget(index)
                }
                if (weightGrams < target - TOLERANCE_GRAMS) break
                events += StepEvent.Reached(index)
                lastTarget = target
            } else {
                val end = steps.getOrNull(index + 1)?.targetAtMs ?: break
                if (elapsedMs < end) break
            }
            advance(elapsedMs, events)
        }
        if (flowGramsPerSecond < POUR_FLOW) rested = true
        state = stateAt(elapsedMs, weightGrams)
        state.dueInMs?.let { due ->
            val seconds = ceil(due / 1000.0).toInt()
            if (seconds in 1..COUNTDOWN_SECONDS && countdownSent.add(seconds)) events += StepEvent.Countdown(index, seconds)
        }
        return events
    }

    private fun markPourBegan(step: RecipeStep, elapsedMs: Long, weightGrams: Double, flow: Double) {
        if (pourBeganAtMs != null) return
        val pouring = flow > POUR_FLOW && (rested || weightGrams > lastTarget + CONTINUE_GRAMS)
        // The first water is the start of the brew: the first step began then.
        if (pouring || (index == 0 && step.targetAtMs == 0L)) pourBeganAtMs = elapsedMs
    }

    private fun advance(elapsedMs: Long, events: MutableList<StepEvent>) {
        index++
        stepSinceMs = elapsedMs
        pourBeganAtMs = null
        // Still pouring into the next step: the pour began only once the flow rests or clearly goes on.
        rested = false
        nearSent = false
        countdownSent.clear()
        events += if (index < steps.size) StepEvent.Started(index) else StepEvent.AllDone
    }

    private fun stateAt(elapsedMs: Long, weightGrams: Double): StepState {
        val step = steps.getOrNull(index) ?: return StepState(index, 1f, null, null, null, null)
        val began = pourBeganAtMs
        val overshoot = (weightGrams - lastTarget).takeIf { lastTarget > 0 && it > TOLERANCE_GRAMS && (!step.kind.pours || began == null) }
        val target = step.targetWeightGrams
        if (step.kind.pours && target != null) {
            val progress = ((weightGrams - lastTarget) / (target - lastTarget)).coerceIn(0.0, 1.0).toFloat()
            val paceMs = began?.let { it - step.targetAtMs }
            return StepState(
                index = index,
                progress = progress,
                dueInMs = if (began == null) (step.targetAtMs - elapsedMs).takeIf { it > 0 } else null,
                pace = paceMs?.let(::pace),
                paceMs = paceMs,
                overshootGrams = overshoot,
            )
        }
        val end = steps.getOrNull(index + 1)?.targetAtMs
        val start = maxOf(step.targetAtMs, stepSinceMs)
        val progress = if (end == null || end <= start) 0f else ((elapsedMs - start).toFloat() / (end - start)).coerceIn(0f, 1f)
        return StepState(index, progress, end?.let { (it - elapsedMs).takeIf { d -> d > 0 } }, null, null, overshoot)
    }

    private fun pace(deltaMs: Long) = when {
        abs(deltaMs) <= ON_TIME_MS -> Pace.ON_TIME
        deltaMs < 0 -> Pace.AHEAD
        else -> Pace.BEHIND
    }

    companion object {
        /** Recorded pours land 1–4 g past round targets (docs/adr/0019-guided-brew.md). */
        const val TOLERANCE_GRAMS = 3.0
        const val NEAR_GRAMS = 5.0
        /** Pours run at 3–7 g/s; between them the weight rests. */
        const val POUR_FLOW = 1.0
        const val ON_TIME_MS = 5_000L
        /** Pouring on without a rest: past an overshoot, this is the next pour. */
        const val CONTINUE_GRAMS = 10.0
        const val COUNTDOWN_SECONDS = 5
    }
}
