package io.github.mateuxlucax.caparao.ui.brew

import android.os.SystemClock
import androidx.compose.animation.core.Easing
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.mateuxlucax.caparao.ui.components.TargetBand
import io.github.mateuxlucax.caparao.ui.components.targetBand
import io.github.mateuxlucax.caparao.ui.recipes.RecipeForm
import io.github.mateuxlucax.caparao.brew.BrewDetector
import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.brew.BrewSummary
import io.github.mateuxlucax.caparao.brew.EspressoDetector
import io.github.mateuxlucax.caparao.brew.FilterConfig
import io.github.mateuxlucax.caparao.brew.FilterDetector
import io.github.mateuxlucax.caparao.brew.FrenchPressFinishTracker
import io.github.mateuxlucax.caparao.brew.FlowSmoother
import io.github.mateuxlucax.caparao.brew.ScaleCommand
import io.github.mateuxlucax.caparao.brew.StepEvent
import io.github.mateuxlucax.caparao.brew.StepState
import io.github.mateuxlucax.caparao.brew.StepTracker
import io.github.mateuxlucax.caparao.brew.withDefaultFrenchPressSteep
import io.github.mateuxlucax.caparao.brew.scaleRecipeToWater
import io.github.mateuxlucax.caparao.brew.TimedReading
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticBrewPhase
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticEvent
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticRecorder
import io.github.mateuxlucax.caparao.data.diagnostics.recordBrewPhase
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.RecipeRepository
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.scale.ScaleSource
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind
import io.github.mateuxlucax.caparao.model.ScaleReading
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.ui.brews.NotesForm
import io.github.mateuxlucax.caparao.ui.theme.Motion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * One brew in [mode]: the dose, then readings fed to the mode's [BrewDetector], which decides when it starts and ends
 * and asks for tares; the result is saved as soon as it ends (#90).
 */
class BrewViewModel(
    private val mode: BrewMode,
    private val scale: ScaleSource,
    private val repository: BrewRepository,
    private val settings: SettingsStore,
    private val recipes: RecipeRepository,
    /** Monotonic, for the detector. */
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    /** Epoch, for the saved start time. */
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    /** Called once a brew is in the database (UploadScheduler.brewSaved). */
    private val onSaved: (String) -> Unit = {},
    /** Called when a saved brew is discarded (UploadScheduler.brewDeleted). */
    private val onDiscarded: (String) -> Unit = {},
    /** Called for a guide moment the watch should vibrate for (WatchLink.alert). */
    private val onWatchAlert: (StepEvent) -> Unit = {},
    /** Every state while the screen is open, then null when it closes (WatchLink.brew). */
    private val onLive: (BrewUiState?) -> Unit = {},
    /** Optional local diagnostic sink; it stores only fixed lifecycle and detector events. */
    private val diagnostics: DiagnosticRecorder? = null,
    /** Whether the system animates (ValueAnimator.areAnimatorsEnabled); with it off, the timer jumps to the saved time. */
    private val animations: () -> Boolean = { true },
) : ViewModel() {
    private val _uiState = MutableStateFlow(BrewUiState(mode))
    val uiState: StateFlow<BrewUiState> = _uiState.asStateFlow()

    private val _alerts = MutableSharedFlow<GuideAlert>(extraBufferCapacity = 16)

    /** The guide's moments worth a vibration or a tone, with what Settings allows; the screen plays them. */
    val alerts: SharedFlow<GuideAlert> = _alerts.asSharedFlow()

    /** Whether Settings lets the phone vibrate for the guide; the guide's alerts and the beans buzz both follow it. */
    val phoneVibrates: Boolean get() = settings.guideVibration && settings.guideVibrationOn.phone

    /** Follows a filter or French press recipe's steps; null for espresso, a free brew or a recipe without steps. */
    private var tracker: StepTracker? = null
    /** The espresso moments already alerted for this brew, so each one alerts once (#193). */
    private val alerted = HashSet<StepEvent>()
    /** Starts only after the terminal press deadline, and never ends the brew. */
    private var frenchPressFinishTracker: FrenchPressFinishTracker? = null
    private var frenchPressDeadlineAlertSent = false
    private var frenchPressLastAlertAtMs: Long? = null
    /** Counts the steep on the phone's clock while the scale is gone (#221). */
    private var steepClockJob: Job? = null
    /** Counts the seconds without a scale before the brew starts; the screen keeps listening (#182). */
    private var scaleWatchJob: Job? = null

    private var job: Job? = null
    /** The timer rolling back to the saved time, before the summary opens (#197). */
    private var rollJob: Job? = null
    private var detector: BrewDetector? = null
    private val readings = ArrayList<TimedReading>()
    private val pauseIntervals = ArrayList<Pair<Long, Long>>()
    private var openPauseAtMs: Long? = null
    private val smoother = FlowSmoother()
    private val filterConfig = FilterConfig()
    private var brewingSinceMs: Long? = null
    /** A negative tared weight held for two seconds means filter or press setup was lifted before water. */
    private var filterLiftBeganAtMs: Long? = null
    /** Highest tared water weight for the current French press, held as the server is lifted off the scale. */
    private var frenchPressWaterGrams = 0.0

    /** Whether the recipe was chosen for this brew, by the user or by preselecting the last one. */
    private var recipeChosen = false
    /** This brew's filter scaling is local to the dose step and never written back to the recipe store. */
    private var filterRecipeEdited = false
    /** The selected filter recipe is the stable basis for repeated water changes; rounding never compounds. */
    private var filterRecipeBase: Recipe? = null
    private var lastDiagnosticPhase: BrewPhase? = null

    /** The brew screen opened: start from the dose, listening to the scale. Does nothing while a brew is open. */
    fun begin() {
        if (job?.isActive == true) return
        reset()
        diagnostics?.recordEvent(DiagnosticEvent.BrewScreenOpened)
        watchScale()
        job = viewModelScope.launch {
            launch { scale.readings.collect(::onReading) }
            launch { _uiState.collect(onLive) }
            launch { recipes.recipes.collect { all -> onRecipes(all.filter { it.mode == mode }) } }
            scale.state.collect(::onScaleState)
        }
    }

    /** The screen closed: stop listening; the next [begin] starts over. */
    fun end() {
        diagnostics?.recordEvent(DiagnosticEvent.BrewScreenClosed)
        job?.cancel()
        job = null
        reset()
        onLive(null)
    }

    /** On the dose step: follow [recipe], or none for a free brew. Its dose fills the dose field. */
    fun selectRecipe(recipe: Recipe?) {
        val state = _uiState.value
        if (state.step != BrewStep.DOSE) return
        if (waterIsControl && filterRecipeEdited && state.recipe?.id == recipe?.id) {
            settings.setLastRecipeId(mode, recipe?.id ?: SettingsStore.FREE_BREW)
            return
        }
        recipeChosen = true
        filterRecipeEdited = false
        settings.setLastRecipeId(mode, recipe?.id ?: SettingsStore.FREE_BREW)
        apply(recipe)
    }

    /** Filter and French press recipes make water the control (#204, #215). */
    private val waterIsControl get() = mode == BrewMode.FILTER || mode == BrewMode.FRENCH_PRESS

    /** Change this brew's water target in ml/g; recipe pours scale while their times and ratio stay fixed. */
    fun setFilterRecipeWater(grams: Double) {
        val state = _uiState.value
        val recipe = filterRecipeBase?.takeIf { state.recipe?.id == it.id && waterIsControl } ?: return
        if (state.step != BrewStep.DOSE || grams !in 1.0..2000.0 || grams / recipe.targetRatio !in RecipeForm.DOSE) return
        val scaled = scaleRecipeToWater(recipe, grams)
        filterRecipeEdited = true
        _uiState.update { it.copy(recipe = scaled, dose = scaled.doseGrams) }
    }

    /** Change this brew's dose while keeping its recipe ratio. */
    fun setFilterRecipeDose(grams: Double) {
        if (grams !in RecipeForm.DOSE) return
        setFilterRecipeWater(grams * _uiState.value.targetRatio)
    }

    /** On the dose step: brew at 1:[ratio]. A free brew remembers it for next time. */
    fun setRatio(ratio: Double) {
        if (_uiState.value.step != BrewStep.DOSE) return
        val clamped = ratio.coerceIn(RecipeForm.RATIO.start, RecipeForm.RATIO.endInclusive)
        if (_uiState.value.recipe == null) settings.setTargetRatio(mode, clamped)
        _uiState.update { it.copy(targetRatio = clamped) }
    }

    /** On the dose step of a free French press: how long to steep; the next free one starts from it (#218). */
    fun setSteep(ms: Long) {
        val state = _uiState.value
        if (state.step != BrewStep.DOSE || mode != BrewMode.FRENCH_PRESS || state.recipe != null || ms !in STEEP_RANGE_MS) return
        settings.setLastSteepMs(ms)
        _uiState.update { it.copy(steepMs = ms) }
    }

    /** On the dose step: the dose to weigh, typed or from the water. */
    fun planDose(grams: Double) {
        if (_uiState.value.step != BrewStep.DOSE || grams <= 0) return
        _uiState.update { it.copy(dose = grams) }
    }

    /**
     * From the dose to weighing the beans: tares, so a container already on the scale isn't counted. Without the scale
     * the dose waits and the weighing opens by itself once it connects (#182).
     */
    fun weigh() {
        val state = _uiState.value
        if (state.step != BrewStep.DOSE || state.dose == null) return
        if (!state.connected) {
            _uiState.update { it.copy(waitingForScale = true) }
            return
        }
        _uiState.update { it.copy(step = BrewStep.WEIGH, waitingForScale = false) }
        scale.tare()
    }

    /** The connection waited too long: ask the scale to try once more, and keep waiting for it (#182). */
    fun retryScale() {
        _uiState.update { it.copy(scaleTimedOut = false) }
        scale.stop()
        scale.start()
        watchScale()
    }

    /** Zero the scale, to weigh the beans without the container. */
    fun tare() = scale.tare()

    /** The dose was weighed: keep it and move espresso, filter or French press to preparation. */
    fun setDose(grams: Double) {
        if (_uiState.value.step != BrewStep.DOSE && _uiState.value.step != BrewStep.WEIGH) return
        if (_uiState.value.recipe == null) settings.setLastDose(mode, grams)
        if (mode == BrewMode.ESPRESSO || mode == BrewMode.FILTER || mode == BrewMode.FRENCH_PRESS) {
            _uiState.update { it.copy(step = BrewStep.PREPARE_PORTAFILTER, dose = grams) }
            return
        }
        armDetector(grams)
    }

    /** Mark or unmark one filter or French press preparation item; checks live only for this brew. */
    fun setPreparationCheck(index: Int, checked: Boolean) {
        val lastIndex = when (mode) {
            BrewMode.FILTER -> 4
            BrewMode.FRENCH_PRESS -> 3
            BrewMode.ESPRESSO -> return
        }
        if (_uiState.value.step != BrewStep.PREPARE_PORTAFILTER || index !in 0..lastIndex) return
        _uiState.update {
            it.copy(preparationChecks = if (checked) it.preparationChecks + index else it.preparationChecks - index)
        }
    }

    /** Ready on the preparation step: arm detection from the current scale weight. */
    fun ready() {
        val state = _uiState.value
        if (state.step != BrewStep.PREPARE_PORTAFILTER) return
        val dose = state.dose ?: return
        armDetector(dose, scale.reading.value)
    }

    private fun armDetector(grams: Double, espressoBaseline: ScaleReading? = null) {
        val new = when (mode) {
            BrewMode.ESPRESSO -> EspressoDetector()
            BrewMode.FILTER -> FilterDetector(filterConfig)
            BrewMode.FRENCH_PRESS -> FilterDetector(FilterConfig(liftDetectionEnabled = false))
        }
        detector = new
        alerted.clear()
        scaleWatchJob?.cancel()
        scaleWatchJob = null
        _uiState.update { it.copy(step = BrewStep.LIVE, dose = grams, phase = new.phase) }
        diagnostics?.recordEvent(DiagnosticEvent.BrewArmed)
        recordDiagnosticPhase(new.phase)
        // Espresso without a reading (Ready after a disconnect) takes the first one as its baseline.
        if (new is EspressoDetector) {
            new.beginWaitingFromBaseline(espressoBaseline?.let { TimedReading(clock(), it) })
        } else {
            // The filter detector tares on its first reading; feed it the current one instead of waiting for a change.
            scale.reading.value?.let(::onReading)
        }
    }

    /** Espresso: the cup on the scale is already tared, skip waiting for it. */
    fun armNow() {
        val espresso = detector as? EspressoDetector ?: return
        val before = espresso.phase
        espresso.armNow(clock()).also(::send)
        espressoMoment(before, espresso.phase)
        _uiState.update { it.copy(phase = espresso.phase) }
    }

    /** Taring timed out: asks for the tare once more. Does nothing outside the tare or while the scale is disconnected. */
    fun retryTare() {
        val current = detector ?: return
        if (current.phase != BrewPhase.TARING || !_uiState.value.connected) return
        send(current.retryTare(clock()))
        _uiState.update { it.copy(tareTimedOut = false) }
    }

    /** Stop (espresso) or Done (filter); before the brew started, nothing is saved. */
    fun stop() {
        val current = detector ?: return
        if (current.phase == BrewPhase.DONE) return
        val before = current.phase
        diagnostics?.recordEvent(DiagnosticEvent.BrewStopRequested)
        current.stop(clock())
        if (mode == BrewMode.FILTER && _uiState.value.recipe == null && before in setOf(BrewPhase.BREWING, BrewPhase.PAUSED)) {
            alertOnce(StepEvent.AllDone)
        }
        recordDiagnosticPhase(current.phase)
        finish(current)
    }

    /** The notes on the summary changed. */
    fun editNotes(form: NotesForm) = _uiState.update { if (it.saved == null) it else it.copy(notes = form) }

    /**
     * Saves the notes typed on the summary into the brew just saved; it is sent again with them
     * (#105). Returns false when a field is invalid.
     */
    fun saveNotes(): Boolean {
        val state = _uiState.value
        val brew = state.saved ?: return true
        val form = state.notes ?: return true
        if (!form.valid) return false
        val updated = form.applyTo(brew)
        if (updated == brew) return true
        _uiState.update { it.copy(saved = updated) }
        viewModelScope.launch {
            repository.updateNotes(updated)
            onSaved(updated.id)
        }
        return true
    }

    /** Deletes the brew just saved. */
    fun discard() {
        val brew = _uiState.value.saved ?: return
        viewModelScope.launch {
            repository.delete(brew.id)
            onDiscarded(brew.id)
        }
        _uiState.update { it.copy(saved = null) }
    }

    private fun onReading(reading: ScaleReading) {
        val current = detector
        // Only before the first water: a lift after it pauses a filter brew (the detector) and keeps a French press running.
        val shouldWatchLift = when (mode) {
            BrewMode.FILTER, BrewMode.FRENCH_PRESS -> current?.phase == BrewPhase.READY
            BrewMode.ESPRESSO -> false
        }
        if (shouldWatchLift && current != null &&
            _uiState.value.step == BrewStep.LIVE
        ) {
            val since = if (reading.weightGrams < 0.0) {
                filterLiftBeganAtMs ?: clock().also { filterLiftBeganAtMs = it }
            } else {
                filterLiftBeganAtMs = null
                null
            }
            if (since != null && clock() - since >= FILTER_PREPARE_LIFT_MS) {
                returnToPreparation(reading)
                return
            }
        }
        if (current == null || current.phase == BrewPhase.DONE) {
            _uiState.update { it.copy(weightGrams = reading.weightGrams) }
            return
        }
        val timed = TimedReading(clock(), reading)
        if (readings.size < MAX_READINGS) readings += timed
        val before = current.phase
        current.onReading(timed)?.let(::send)
        val pauseState = current.pauseState
        when {
            before == BrewPhase.BREWING && current.phase == BrewPhase.PAUSED -> {
                openPauseAtMs = pauseState?.startedAtMs
                smoother.reset()
            }
            before == BrewPhase.PAUSED && current.phase == BrewPhase.BREWING -> {
                val pauseStart = openPauseAtMs
                if (pauseStart != null) {
                    // A sustained pour resumes from when the dripper came back, which is earlier than this reading.
                    val resumedAt = (current as? FilterDetector)?.lastResumedAtMs ?: timed.atMs
                    pauseIntervals += pauseStart to resumedAt
                    brewingSinceMs = (brewingSinceMs ?: pauseStart) + (resumedAt - pauseStart)
                }
                openPauseAtMs = null
                smoother.reset()
            }
        }
        espressoMoment(before, current.phase)
        filterMoment(before, current.phase)
        recordDiagnosticPhase(current.phase)
        if (current.phase == BrewPhase.BREWING && brewingSinceMs == null) {
            brewingSinceMs = (current as? FilterDetector)?.startedAtMs ?: timed.atMs
            smoother.reset()
            val steps = _uiState.value.recipe?.steps.orEmpty()
            when (mode) {
                BrewMode.ESPRESSO -> Unit
                BrewMode.FILTER -> if (steps.isNotEmpty()) tracker = StepTracker(steps, FilterConfig().stepTargetHoldMs)
                BrewMode.FRENCH_PRESS -> if (steps.isNotEmpty()) {
                    tracker = StepTracker(steps)
                } else {
                    // A free brew steeps too: pour the planned water, then steep for the chosen time (#218).
                    val freeSteps = freePressSteps()
                    _uiState.update { it.copy(freeSteps = freeSteps) }
                    tracker = StepTracker(freeSteps)
                }
            }
        }
        val since = brewingSinceMs
        val shownWeight = if (mode == BrewMode.FILTER && current.phase == BrewPhase.PAUSED) {
            pauseState?.waterWeightGrams ?: reading.weightGrams
        } else if (mode == BrewMode.FRENCH_PRESS && current.phase == BrewPhase.BREWING) {
            frenchPressWaterGrams = maxOf(frenchPressWaterGrams, reading.weightGrams.coerceAtLeast(0.0))
            frenchPressWaterGrams
        } else {
            reading.weightGrams
        }
        // Feed the running maximum to flow smoothing too: removing the carafe is not a negative pour.
        val flowReading = if (mode == BrewMode.FRENCH_PRESS && current.phase == BrewPhase.BREWING) {
            timed.copy(reading = reading.copy(weightGrams = shownWeight))
        } else {
            timed
        }
        val flow = if (since != null && current.phase == BrewPhase.BREWING) smoother.add(flowReading) else 0.0
        val activeAt = when {
            since == null -> null
            current.phase == BrewPhase.PAUSED -> pauseState?.startedAtMs ?: timed.atMs
            else -> timed.atMs
        }
        val guideWeight = if (current.phase == BrewPhase.PAUSED) shownWeight else reading.weightGrams
        val guide = tracker?.takeIf { since != null && (current.phase == BrewPhase.BREWING || current.phase == BrewPhase.PAUSED) }
            ?.let { t -> advanceGuide(t, timed.atMs, activeAt!! - since!!, guideWeight, flow, removalWeight = reading.weightGrams) }
        val frenchPressRemoved = frenchPressFinishTracker?.removedAtMs != null
        _uiState.update {
            it.copy(
                phase = current.phase,
                tareTimedOut = current.tareTimedOut,
                weightGrams = shownWeight,
                flowGramsPerSecond = flow,
                elapsedMs = activeAt?.let { s -> s - since!! } ?: 0,
                pauseRemainingMs = if (current.phase == BrewPhase.PAUSED) {
                    ((pauseState?.startedAtMs?.let { start -> filterConfig.pauseTimeoutMs - (timed.atMs - start) } ?: 0L).coerceAtLeast(0L))
                } else null,
                guide = visible(guide) ?: it.guide,
                frenchPressRemoved = frenchPressRemoved || it.frenchPressRemoved,
            )
        }
        if (mode == BrewMode.FILTER && current.phase == BrewPhase.BREWING) {
            val live = _uiState.value
            val min = live.recipe?.targetMinMs
            val max = live.recipe?.targetMaxMs
            if (min != null && max != null && min <= max && live.drainWindow != null) {
                if (live.elapsedMs > max) {
                    alertOnce(StepEvent.DrainTimePassed)
                } else if (live.elapsedMs >= min) {
                    alertOnce(StepEvent.DrainTimeEntered)
                }
            }
        }
        if (mode == BrewMode.ESPRESSO && current.phase == BrewPhase.BREWING) {
            // The band is the ring's (#192): a hover on its edge alerts once, as the latch is per brew.
            val ratio = _uiState.value.ratio
            if (ratio != null && targetBand(ratio, _uiState.value.targetRatio) == TargetBand.AT) alertOnce(StepEvent.InTargetBand)
        }
        if (mode == BrewMode.FILTER && _uiState.value.recipe == null && current.phase == BrewPhase.BREWING) {
            // Free filter follows the water target; if one reading jumps across the band, both crossings still alert.
            val ratio = _uiState.value.ratio
            if (ratio != null) when (targetBand(ratio, _uiState.value.targetRatio)) {
                TargetBand.BELOW -> Unit
                TargetBand.AT -> alertOnce(StepEvent.InTargetBand)
                TargetBand.ABOVE -> {
                    alertOnce(StepEvent.InTargetBand)
                    alertOnce(StepEvent.PastTargetBand)
                }
            }
        }
        if (current.phase == BrewPhase.DONE) {
            if (mode == BrewMode.FILTER && _uiState.value.recipe == null && before == BrewPhase.PAUSED && current.result?.interrupted != true) {
                alertOnce(StepEvent.AllDone)
            }
            finish(current)
        }
    }

    /** The filter or French press setup was lifted before the first water: let the user put it back without losing checklist marks. */
    private fun returnToPreparation(reading: ScaleReading) {
        detector = null
        filterLiftBeganAtMs = null
        readings.clear()
        smoother.reset()
        brewingSinceMs = null
        tracker = null
        _uiState.update {
            it.copy(
                step = BrewStep.PREPARE_PORTAFILTER,
                phase = BrewPhase.WAITING_FOR_CUP,
                tareTimedOut = false,
                weightGrams = reading.weightGrams,
                flowGramsPerSecond = 0.0,
                elapsedMs = 0,
                guide = null,
            )
        }
    }

    private fun onRecipes(list: List<Recipe>) {
        val state = _uiState.value
        // Before the brew, an edit elsewhere updates the chosen one; once it runs, the brew keeps what it started with.
        val savedRecipe = state.recipe?.let { chosen -> list.find { it.id == chosen.id } }?.withDefaultFrenchPressSteep()
        val current = savedRecipe?.takeIf { state.step == BrewStep.DOSE && !filterRecipeEdited } ?: state.recipe
        if (state.step == BrewStep.DOSE && !filterRecipeEdited) filterRecipeBase = current?.takeIf { waterIsControl && it.mode == mode }
        val refreshPlan = savedRecipe?.takeIf { waterIsControl && state.step == BrewStep.DOSE && !filterRecipeEdited }
        _uiState.update {
            it.copy(
                recipes = list,
                recipe = current,
                dose = refreshPlan?.doseGrams ?: it.dose,
                targetRatio = refreshPlan?.targetRatio ?: it.targetRatio,
            )
        }
        if (recipeChosen || state.step != BrewStep.DOSE) return
        recipeChosen = true
        // Filter always opens free; other modes keep their last choice or first recipe.
        val last = settings.lastRecipeId(mode)
        apply(if (mode == BrewMode.FILTER) null else if (last == null) list.firstOrNull() else list.find { it.id == last })
    }

    private fun apply(recipe: Recipe?) {
        val forBrew = recipe?.withDefaultFrenchPressSteep()
        filterRecipeBase = forBrew?.takeIf { waterIsControl && it.mode == mode }
        _uiState.update {
            it.copy(
                recipe = forBrew,
                dose = forBrew?.doseGrams ?: settings.lastDose(mode),
                targetRatio = forBrew?.targetRatio ?: settings.targetRatio(mode),
            )
        }
    }

    private fun onScaleState(state: ScaleState) {
        _uiState.update { it.copy(connected = state is ScaleState.Connected) }
        if (state is ScaleState.Connected) {
            scaleWatchJob?.cancel()
            scaleWatchJob = null
            _uiState.update { it.copy(scaleTimedOut = false) }
            // The dose that was waiting goes on to the weighing without another tap (#182).
            if (_uiState.value.waitingForScale) weigh()
        } else if (scaleWatchJob == null) {
            // A flapping connection keeps the same ten seconds; only a fresh wait starts a new count.
            watchScale()
        }
        val current = detector ?: return
        if (state is ScaleState.Connected) steepClockJob?.cancel()
        // A dropped connection ends a brew in progress as interrupted; before it started there is nothing to keep.
        // A French press keeps steeping instead, free or with a recipe (#221, #218).
        if (state is ScaleState.Failed && mode == BrewMode.FRENCH_PRESS && tracker != null && current.phase == BrewPhase.BREWING) {
            keepSteepRunning()
            return
        }
        if (state is ScaleState.Failed && (current.phase == BrewPhase.BREWING || current.phase == BrewPhase.PAUSED)) {
            diagnostics?.recordEvent(DiagnosticEvent.BrewInterrupted)
            current.interrupt(clock())
            recordDiagnosticPhase(current.phase)
            finish(current)
        }
    }

    /**
     * Before the brew the screen keeps listening: ten seconds without a connection and the step admits the scale
     * wasn't found, with Retry, while a scale that arrives later still goes on by itself (#182).
     */
    private fun watchScale() {
        scaleWatchJob?.cancel()
        scaleWatchJob = null
        val state = _uiState.value
        if (state.connected || state.step == BrewStep.LIVE || state.step == BrewStep.SUMMARY) return
        scaleWatchJob = viewModelScope.launch {
            delay(SCALE_WAIT_TIMEOUT_MS)
            if (!_uiState.value.connected) _uiState.update { it.copy(scaleTimedOut = true) }
        }
    }

    /** Espresso's moments (#193): the phase changes that put the cup, the tare, the first drop on the screen. */
    private fun espressoMoment(from: BrewPhase, to: BrewPhase) {
        if (mode != BrewMode.ESPRESSO) return
        when {
            from == BrewPhase.WAITING_FOR_CUP && to == BrewPhase.TARING -> alertOnce(StepEvent.CupDetected)
            from == BrewPhase.TARING && to == BrewPhase.READY -> alertOnce(StepEvent.TareConfirmed)
            from == BrewPhase.READY && to == BrewPhase.BREWING -> alertOnce(StepEvent.FirstDrop)
        }
    }

    /** Filter's first water is only a free-brew moment; pause and resume apply to both filter flows (#209). */
    private fun filterMoment(from: BrewPhase, to: BrewPhase) {
        if (mode != BrewMode.FILTER) return
        when {
            from == BrewPhase.READY && to == BrewPhase.BREWING && _uiState.value.recipe == null -> alertOnce(StepEvent.FirstWater)
            from == BrewPhase.BREWING && to == BrewPhase.PAUSED -> alert(StepEvent.Paused)
            from == BrewPhase.PAUSED && to == BrewPhase.BREWING -> alert(StepEvent.Resumed)
        }
    }

    private fun alertOnce(event: StepEvent) {
        if (alerted.add(event)) alert(event)
    }

    private fun alert(event: StepEvent) {
        val target = settings.guideVibrationOn
        val vibrate = phoneVibrates
        val sound = settings.guideSound
        if (settings.guideVibration && target.watch) onWatchAlert(event)
        if (vibrate || sound) _alerts.tryEmit(GuideAlert(event, vibrate, sound))
    }

    /** Advances the guide to [elapsedMs] and plays its events; [removalWeight] is null when the scale is gone. */
    private fun advanceGuide(t: StepTracker, atMs: Long, elapsedMs: Long, guideWeight: Double, flow: Double, removalWeight: Double?): StepState {
        val events = t.update(elapsedMs, guideWeight, flow)
        if (mode == BrewMode.FRENCH_PRESS && StepEvent.AllDone in events) frenchPressFinishTracker = FrenchPressFinishTracker()
        events.filterNot { mode == BrewMode.FRENCH_PRESS && it == StepEvent.AllDone }.forEach(::alert)
        if (mode == BrewMode.FRENCH_PRESS) pressDeadlineAlerts(atMs, removalWeight, t.state.allDone(t.stepCount))
        return t.state
    }

    /** A free French press shows the ring while pouring; its guide opens with the steep (#218). */
    private fun visible(guide: StepState?): StepState? = guide?.takeIf { _uiState.value.recipe != null || it.index >= 1 }

    private fun freePressSteps(): List<RecipeStep> {
        val state = _uiState.value
        return listOf(
            RecipeStep(StepKind.POUR, state.plannedWater ?: 0.0, 0L),
            RecipeStep(StepKind.PRESS, null, state.steepMs),
        )
    }

    /**
     * The French press steep needs no scale: when the connection drops after the first water, the guide goes on
     * counting on the phone's clock until Done (#221). Reading-driven code resumes when the scale returns.
     */
    private fun keepSteepRunning() {
        val t = tracker ?: return
        val since = brewingSinceMs ?: return
        steepClockJob?.cancel()
        steepClockJob = viewModelScope.launch {
            while (true) {
                delay(STEEP_CLOCK_MS)
                val now = clock()
                val guide = advanceGuide(t, now, now - since, _uiState.value.weightGrams, 0.0, removalWeight = null)
                _uiState.update { it.copy(elapsedMs = now - since, guide = visible(guide) ?: it.guide) }
            }
        }
    }

    /**
     * French press: from the deadline, alert and then repeat every [FRENCH_PRESS_ALERT_INTERVAL_MS] until the scale
     * sees the press leave. A candidate removal pauses the repeat; if the weight returns it alerts again at once (#217).
     */
    private fun pressDeadlineAlerts(atMs: Long, weightGrams: Double?, deadlinePassed: Boolean) {
        val finish = frenchPressFinishTracker ?: return
        val removed = weightGrams?.let { finish.update(atMs, it) } ?: (finish.removedAtMs != null)
        if (!deadlinePassed || removed) return
        if (finish.removalStartedAtMs != null) {
            frenchPressLastAlertAtMs = null
            return
        }
        val last = frenchPressLastAlertAtMs
        if (last != null && atMs - last < FRENCH_PRESS_ALERT_INTERVAL_MS) return
        alert(if (frenchPressDeadlineAlertSent) StepEvent.FrenchPressReminder else StepEvent.FrenchPressDeadline)
        frenchPressDeadlineAlertSent = true
        frenchPressLastAlertAtMs = atMs
    }

    private fun send(command: ScaleCommand) = when (command) {
        ScaleCommand.TARE -> scale.tare()
    }

    private fun finish(done: BrewDetector) {
        steepClockJob?.cancel()
        val detected = done.result
        if (detected == null) {
            _uiState.update { it.copy(step = BrewStep.SUMMARY, phase = BrewPhase.DONE, saved = null) }
            return
        }
        // French press time runs to the confirmed removal even when Done comes later (#223); with none, it is Done's.
        val removedAtMs = if (mode == BrewMode.FRENCH_PRESS) frenchPressFinishTracker?.removedAtMs else null
        val result = if (removedAtMs != null && removedAtMs in detected.startedAtMs until detected.endedAtMs) {
            detected.copy(endedAtMs = removedAtMs)
        } else {
            detected
        }
        diagnostics?.recordEvent(DiagnosticEvent.BrewResultReady)
        val removalAtMs = frenchPressFinishTracker?.removedAtMs ?: frenchPressFinishTracker?.removalStartedAtMs
        val summaryReadings = if (mode == BrewMode.FRENCH_PRESS) {
            var highestWater = 0.0
            readings.asSequence()
                .filter { it.atMs in result.startedAtMs..result.endedAtMs && (removalAtMs == null || it.atMs < removalAtMs) }
                .map { reading ->
                    highestWater = maxOf(highestWater, reading.reading.weightGrams.coerceAtLeast(0.0))
                    reading.copy(reading = reading.reading.copy(weightGrams = highestWater))
                }
                .toList()
        } else if (mode == BrewMode.FILTER && (pauseIntervals.isNotEmpty() || openPauseAtMs != null)) {
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
            } else {
                rebased
            }
        } else {
            readings
        }
        val flowResetTimesMs = if (mode == BrewMode.FILTER) {
            (pauseIntervals.map { it.first } + listOfNotNull(openPauseAtMs)).map { pauseStart ->
                pauseStart - pauseIntervals.filter { (_, end) -> end <= pauseStart }.sumOf { (start, end) -> end - start }
            }
        } else {
            emptyList()
        }
        val summary = BrewSummary.of(mode, summaryReadings, result, flowResetTimesMs)
        val startedAt = wallClock() - (clock() - result.startedAtMs)
        val recipe = _uiState.value.recipe
        val brew = summary.toBrew(newId(), mode, startedAt, requireNotNull(_uiState.value.dose), result.interrupted).copy(
            recipeId = recipe?.id,
            recipeName = recipe?.name,
            grind = recipe?.grind,
            waterTempCelsius = recipe?.waterTempCelsius,
        )
        // Keep the live screen until the timer reaches the saved time, with the curve ready for the summary.
        _uiState.update { it.copy(phase = BrewPhase.DONE, saved = brew, curve = summary.curve) }
        openSummary(brew.durationMs)
        viewModelScope.launch {
            // Past coffees before this brew is in: it has none yet.
            val coffees = repository.recentCoffees()
            repository.save(brew, summary.curve)
            // After the insert, so the upload job always finds the brew.
            onSaved(brew.id)
            _uiState.update { if (it.saved?.id == brew.id) it.copy(notes = NotesForm.of(brew, coffees.firstOrNull()), coffees = coffees) else it }
        }
    }

    /**
     * Rolls the shown time to [savedMs], the time the summary and the saved brew show, then opens the summary. The
     * detector can end an espresso a few seconds after the shown time stopped (#197). Only espresso rolls; filter and
     * French press open the summary at once. With animations off, or when both show the same whole seconds, it opens
     * at once too.
     */
    private fun openSummary(savedMs: Long) {
        val from = _uiState.value.elapsedMs
        if (mode != BrewMode.ESPRESSO || !animations() || from / 1000 == savedMs / 1000) {
            showSummary(savedMs)
            return
        }
        rollJob = viewModelScope.launch {
            repeat(ROLL_FRAMES) { frame ->
                delay(ROLL_MS / ROLL_FRAMES)
                val eased = ROLL_EASING.transform((frame + 1).toFloat() / ROLL_FRAMES)
                _uiState.update { it.copy(elapsedMs = from + ((savedMs - from) * eased).toLong()) }
            }
            showSummary(savedMs)
        }
    }

    private fun showSummary(savedMs: Long) = _uiState.update { it.copy(step = BrewStep.SUMMARY, elapsedMs = savedMs) }

    private fun reset() {
        rollJob?.cancel()
        rollJob = null
        steepClockJob?.cancel()
        steepClockJob = null
        scaleWatchJob?.cancel()
        scaleWatchJob = null
        detector = null
        alerted.clear()
        readings.clear()
        pauseIntervals.clear()
        openPauseAtMs = null
        smoother.reset()
        brewingSinceMs = null
        filterLiftBeganAtMs = null
        frenchPressWaterGrams = 0.0
        tracker = null
        frenchPressFinishTracker = null
        frenchPressDeadlineAlertSent = false
        frenchPressLastAlertAtMs = null
        recipeChosen = false
        filterRecipeEdited = false
        filterRecipeBase = null
        lastDiagnosticPhase = null
        _uiState.value = BrewUiState(
            mode,
            dose = settings.lastDose(mode),
            targetRatio = settings.targetRatio(mode),
            steepMs = settings.lastSteepMs(),
            connected = scale.state.value is ScaleState.Connected,
            weightGrams = scale.reading.value?.weightGrams ?: 0.0,
        )
    }

    private fun recordDiagnosticPhase(phase: BrewPhase) {
        if (phase == lastDiagnosticPhase) return
        lastDiagnosticPhase = phase
        val diagnosticPhase = when (phase) {
            BrewPhase.WAITING_FOR_CUP -> DiagnosticBrewPhase.WAITING_FOR_CUP
            BrewPhase.TARING -> DiagnosticBrewPhase.TARING
            BrewPhase.READY -> DiagnosticBrewPhase.READY
            BrewPhase.BREWING, BrewPhase.PAUSED -> DiagnosticBrewPhase.BREWING
            BrewPhase.DONE -> DiagnosticBrewPhase.DONE
        }
        diagnostics?.recordBrewPhase(mode, diagnosticPhase)
    }

    companion object {
        /** The mode to brew, passed when the ViewModel is created. */
        val MODE = object : CreationExtras.Key<BrewMode> {}

        /** 20 minutes at 10 Hz: longer than any brew; past it the curve stops growing. */
        private const val MAX_READINGS = 12_000
        private const val FILTER_PREPARE_LIFT_MS = 2_000L
        internal const val FRENCH_PRESS_ALERT_INTERVAL_MS = 15_000L

        /** How long a pre-brew step waits for the scale before offering the not-found card (#182). */
        internal const val SCALE_WAIT_TIMEOUT_MS = 10_000L

        /** The steep a free French press accepts: 10 s to an hour. */
        val STEEP_RANGE_MS = 10_000L..3_600_000L
        private const val STEEP_CLOCK_MS = 250L

        /** The timer rolls back to the saved time over this long, in frames of [ROLL_MS] / [ROLL_FRAMES]. */
        private const val ROLL_MS = 600L
        private const val ROLL_FRAMES = 36
        private val ROLL_EASING: Easing = Motion.EmphasizedDecelerate
    }
}
