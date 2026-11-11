package io.github.mateuxlucax.caparao.ui.brew

import android.os.SystemClock
import androidx.compose.animation.core.Easing
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.mateuxlucax.caparao.ui.components.RecipeForm
import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.brew.BrewSummary
import io.github.mateuxlucax.caparao.brew.BrewSession
import io.github.mateuxlucax.caparao.brew.DrainWindow as BrewDrainWindow
import io.github.mateuxlucax.caparao.brew.FilterConfig
import io.github.mateuxlucax.caparao.brew.ScaleCommand
import io.github.mateuxlucax.caparao.brew.StepEvent
import io.github.mateuxlucax.caparao.brew.StepState
import io.github.mateuxlucax.caparao.brew.withDefaultFrenchPressSteep
import io.github.mateuxlucax.caparao.brew.scaleRecipeToWater
import io.github.mateuxlucax.caparao.brew.TimedReading
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticBrewPhase
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticEvent
import io.github.mateuxlucax.caparao.data.diagnostics.Diagnostics
import io.github.mateuxlucax.caparao.data.diagnostics.NoDiagnostics
import io.github.mateuxlucax.caparao.data.diagnostics.recordBrewPhase
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.CoffeeBagRepository
import io.github.mateuxlucax.caparao.data.local.RecipeRepository
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.scale.ScaleSource
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.CoffeeBag
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind
import io.github.mateuxlucax.caparao.model.ScaleReading
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.ui.components.NotesForm
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
 * One brew in [mode]: the dose, then readings fed to the mode's [BrewSession], which decides when it starts and ends
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
    private val diagnostics: Diagnostics = NoDiagnostics,
    /** Whether the system animates (ValueAnimator.areAnimatorsEnabled); with it off, the timer jumps to the saved time. */
    private val animations: () -> Boolean = { true },
    /** The coffee bags to pick from before weighing and on the summary (#291); null leaves the coffee step out. */
    private val coffeeBags: CoffeeBagRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BrewUiState(mode))
    val uiState: StateFlow<BrewUiState> = _uiState.asStateFlow()

    private val _alerts = MutableSharedFlow<GuideAlert>(extraBufferCapacity = 16)

    /** The guide's moments worth a vibration or a tone, with what Settings allows; the screen plays them. */
    val alerts: SharedFlow<GuideAlert> = _alerts.asSharedFlow()

    /** Whether Settings lets the phone vibrate for the guide; the guide's alerts and the beans buzz both follow it. */
    val phoneVibrates: Boolean get() = settings.guideVibration && settings.guideVibrationOn.phone

    /** Counts the steep on the phone's clock while the scale is gone (#221). */
    private var steepClockJob: Job? = null
    /** Counts the seconds without a scale before the brew starts; the screen keeps listening (#182). */
    private var scaleWatchJob: Job? = null

    private var job: Job? = null
    /** The timer rolling back to the saved time, before the summary opens (#197). */
    private var rollJob: Job? = null
    private var session: BrewSession? = null
    private val filterConfig = FilterConfig()

    /** Whether the recipe was chosen for this brew, by the user or by preselecting the last one. */
    private var recipeChosen = false
    /** The user picked a coffee on the coffee step, so going back to Dose and on keeps that pick (#291). */
    private var coffeeBagChosen = false
    /** This brew's filter scaling is local to the dose step and never written back to the recipe store. */
    private var filterRecipeEdited = false
    /** The selected filter recipe is the stable basis for repeated water changes; rounding never compounds. */
    private var filterRecipeBase: Recipe? = null
    private var lastDiagnosticPhase: BrewPhase? = null

    /** The brew screen opened: start from the dose, listening to the scale. Does nothing while a brew is open. */
    fun begin() {
        if (job?.isActive == true) return
        reset()
        diagnostics.event(DiagnosticEvent.BrewScreenOpened)
        watchScale()
        job = viewModelScope.launch {
            launch { scale.readings.collect(::onReading) }
            launch { _uiState.collect(onLive) }
            launch { recipes.recipes.collect { all -> onRecipes(all.filter { it.mode == mode }) } }
            coffeeBags?.let { bags -> launch { bags.bags.collect { all -> _uiState.update { it.copy(bags = all.filterNot(CoffeeBag::finished)) } } } }
            scale.state.collect(::onScaleState)
        }
    }

    /** The screen closed: stop listening; the next [begin] starts over. */
    fun end() {
        diagnostics.event(DiagnosticEvent.BrewScreenClosed)
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
     * From the dose to the coffee, when there are open bags (#291); otherwise straight to weighing the beans. The coffee
     * step opens with the bag last used selected.
     */
    fun weigh() {
        val state = _uiState.value
        if (state.step != BrewStep.DOSE || state.dose == null) return
        if (state.bags.isEmpty()) {
            beginWeighing()
            return
        }
        // Back from the coffee keeps the pick: the last bag used is only the first suggestion.
        _uiState.update {
            it.copy(step = BrewStep.COFFEE, bagId = if (coffeeBagChosen) it.bagId else (lastUsedBag(it.bags) ?: it.bags.first()).id)
        }
    }

    /** On the coffee step: the bag this brew uses, or null for No bag. */
    fun selectBag(bagId: String?) {
        val state = _uiState.value
        if (state.step != BrewStep.COFFEE || bagId != null && state.bags.none { it.id == bagId }) return
        coffeeBagChosen = true
        _uiState.update { it.copy(bagId = bagId) }
    }

    /** From the coffee to weighing the beans. */
    fun continueToBeans() {
        if (_uiState.value.step != BrewStep.COFFEE) return
        beginWeighing()
    }

    /**
     * Tares, so a container already on the scale isn't counted. Without the scale the step waits and the weighing opens by
     * itself once it connects (#182).
     */
    private fun beginWeighing() {
        val state = _uiState.value
        if ((state.step != BrewStep.DOSE && state.step != BrewStep.COFFEE) || state.dose == null) return
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

    /**
     * Back one step before Live: Beans to Coffee (or to Dose without open bags), Coffee to Dose, Prepare to Beans (#185,
     * #291). The coffee picked and the checklist marks stay, and going forward again tares on Ready as before. Other
     * steps do nothing here; the screen closes the brew for them.
     */
    fun backOneStep() {
        val state = _uiState.value
        val previous = when (state.step) {
            BrewStep.WEIGH -> if (state.bags.isEmpty()) BrewStep.DOSE else BrewStep.COFFEE
            BrewStep.COFFEE -> BrewStep.DOSE
            BrewStep.PREPARE_PORTAFILTER -> BrewStep.WEIGH
            else -> return
        }
        // A wait for the scale belongs to the step it was waiting on.
        _uiState.update { it.copy(step = previous, waitingForScale = false) }
    }

    /**
     * Back in Live (#287): drops the brew in progress and what was recorded, and returns to Prepare with the checklist
     * marks and the chosen recipe kept. Nothing is saved or uploaded; the next Ready starts a fresh brew.
     */
    fun restart() {
        if (_uiState.value.step != BrewStep.LIVE) return
        clearRun()
        _uiState.update {
            it.copy(
                step = BrewStep.PREPARE_PORTAFILTER,
                phase = BrewPhase.WAITING_FOR_CUP,
                tareTimedOut = false,
                weightGrams = scale.reading.value?.weightGrams ?: 0.0,
                flowGramsPerSecond = 0.0,
                elapsedMs = 0,
                pauseRemainingMs = null,
                guide = null,
                freeSteps = emptyList(),
                frenchPressRemoved = false,
                frenchPressRemovalPending = false,
            )
        }
        watchScale()
    }

    /** Ready on the preparation step: arm detection from the current scale weight. */
    fun ready() {
        val state = _uiState.value
        if (state.step != BrewStep.PREPARE_PORTAFILTER) return
        val dose = state.dose ?: return
        armDetector(dose, scale.reading.value)
    }

    private fun armDetector(grams: Double, espressoBaseline: ScaleReading? = null) {
        val state = _uiState.value
        val recipe = state.recipe
        val freeSteps = if (mode == BrewMode.FRENCH_PRESS && recipe?.steps.isNullOrEmpty()) {
            listOf(
                RecipeStep(StepKind.POUR, state.plannedWater ?: 0.0, 0L),
                RecipeStep(StepKind.PRESS, null, state.steepMs),
            )
        } else emptyList()
        val new = BrewSession(mode, grams, state.targetRatio, recipe?.steps.orEmpty(), recipe?.targetMinMs, recipe?.targetMaxMs, freeSteps, filterConfig)
        session = new
        scaleWatchJob?.cancel()
        scaleWatchJob = null
        _uiState.update { it.copy(step = BrewStep.LIVE, dose = grams, phase = new.phase) }
        diagnostics.event(DiagnosticEvent.BrewArmed)
        recordDiagnosticPhase(new.phase)
        // Espresso without a reading (Ready after a disconnect) takes the first one as its baseline.
        if (mode == BrewMode.ESPRESSO) {
            new.beginWaitingFromBaseline(espressoBaseline?.let { TimedReading(clock(), it) })
        } else {
            // The filter detector tares on its first reading; feed it the current one instead of waiting for a change.
            scale.reading.value?.let(::onReading)
        }
    }

    /** Espresso: the cup on the scale is already tared, skip waiting for it. */
    fun armNow() {
        val current = session ?: return
        val before = current.phase
        current.armNow(clock())?.also(::send)
        current.phaseEvents(before, current.phase).forEach(::alert)
        _uiState.update { it.copy(phase = current.phase) }
    }

    /** Taring timed out: asks for the tare once more. Does nothing outside the tare or while the scale is disconnected. */
    fun retryTare() {
        val current = session ?: return
        if (current.phase != BrewPhase.TARING || !_uiState.value.connected) return
        send(current.retryTare(clock()))
        _uiState.update { it.copy(tareTimedOut = false) }
    }

    /** Stop (espresso) or Done (filter); before the brew started, nothing is saved. */
    fun stop() = endBrew(DiagnosticEvent.BrewStopRequested)

    /** Ends the brew as Done does, recording [event] as why (a Done tap or a lift past the last pour). */
    private fun endBrew(event: DiagnosticEvent) {
        val current = session ?: return
        if (current.phase == BrewPhase.DONE) return
        diagnostics.event(event)
        current.stop(clock()).forEach(::alert)
        recordDiagnosticPhase(current.phase)
        finish()
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
        val current = session
        if (current == null || current.phase == BrewPhase.DONE) {
            _uiState.update { it.copy(weightGrams = reading.weightGrams) }
            return
        }
        val state = current.onReading(TimedReading(clock(), reading))
        if (state.returnedToPreparation) {
            returnToPreparation(reading)
            return
        }
        state.command?.let(::send)
        // A lift once the water is past the last pour ends the brew at the lift, as Done would; before it, the brew pauses.
        if (state.filterLiftEndsBrew) {
            endBrew(DiagnosticEvent.BrewLiftEnded)
            return
        }
        recordDiagnosticPhase(state.phase)
        state.guideEvents.forEach(::alert)
        _uiState.update {
            it.copy(
                phase = state.phase,
                tareTimedOut = state.tareTimedOut,
                weightGrams = state.weightGrams,
                flowGramsPerSecond = state.flowGramsPerSecond,
                elapsedMs = state.elapsedMs,
                pauseRemainingMs = state.pauseRemainingMs,
                drainWindow = state.drainWindow?.let(::drainWindow),
                guide = visible(state.guide) ?: it.guide,
                freeSteps = if (mode == BrewMode.FRENCH_PRESS && it.recipe == null && state.guide != null) current.guideSteps else it.freeSteps,
                frenchPressRemoved = state.frenchPressRemoved || it.frenchPressRemoved,
                frenchPressRemovalPending = state.frenchPressRemovalPending,
            )
        }
        if (state.phase == BrewPhase.DONE) {
            finish()
        }
    }

    /** The filter or French press setup was lifted before the first water: let the user put it back without losing checklist marks. */
    private fun returnToPreparation(reading: ScaleReading) {
        session?.clearRun()
        session = null
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
            if (_uiState.value.waitingForScale) beginWeighing()
        } else if (scaleWatchJob == null) {
            // A flapping connection keeps the same ten seconds; only a fresh wait starts a new count.
            watchScale()
        }
        val current = session ?: return
        if (state is ScaleState.Connected) steepClockJob?.cancel()
        if (state is ScaleState.Failed) {
            val keepSteeping = mode == BrewMode.FRENCH_PRESS && current.phase == BrewPhase.BREWING && current.guideSteps.isNotEmpty()
            current.onScaleLost(clock())
            if (keepSteeping) {
                keepSteepRunning()
            } else if (current.phase == BrewPhase.DONE && current.result?.interrupted == true) {
                diagnostics.event(DiagnosticEvent.BrewInterrupted)
                recordDiagnosticPhase(current.phase)
                finish()
            }
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

    private fun alert(event: StepEvent) {
        val target = settings.guideVibrationOn
        val vibrate = phoneVibrates
        val sound = settings.guideSound
        if (settings.guideVibration && target.watch) onWatchAlert(event)
        if (vibrate || sound) _alerts.tryEmit(GuideAlert(event, vibrate, sound))
    }

    /** A free French press shows the ring while pouring; its guide opens with the steep (#218). */
    private fun visible(guide: StepState?): StepState? = guide?.takeIf { _uiState.value.recipe != null || it.index >= 1 }

    /**
     * The French press steep needs no scale: when the connection drops after the first water, the guide goes on
     * counting on the phone's clock until Done (#221). Reading-driven code resumes when the scale returns.
     */
    private fun keepSteepRunning() {
        val current = session ?: return
        if (current.guideSteps.isEmpty()) return
        steepClockJob?.cancel()
        steepClockJob = viewModelScope.launch {
            while (true) {
                delay(STEEP_CLOCK_MS)
                val state = current.onClock(clock())
                state.guideEvents.forEach(::alert)
                _uiState.update {
                    it.copy(
                        elapsedMs = state.elapsedMs,
                        drainWindow = state.drainWindow?.let(::drainWindow),
                        guide = visible(state.guide) ?: it.guide,
                        frenchPressRemoved = state.frenchPressRemoved || it.frenchPressRemoved,
                        frenchPressRemovalPending = state.frenchPressRemovalPending,
                    )
                }
            }
        }
    }

    private fun send(command: ScaleCommand) = when (command) {
        ScaleCommand.TARE -> scale.tare()
    }

    private fun drainWindow(window: BrewDrainWindow) = when (window) {
        BrewDrainWindow.BEFORE -> BrewUiState.Window.BEFORE
        BrewDrainWindow.INSIDE -> BrewUiState.Window.INSIDE
        BrewDrainWindow.PAST -> BrewUiState.Window.PAST
    }

    private fun finish() {
        val done = session ?: return
        steepClockJob?.cancel()
        val result = done.finish()
        if (result == null) {
            _uiState.update { it.copy(step = BrewStep.SUMMARY, phase = BrewPhase.DONE, saved = null) }
            return
        }
        val detected = result.result
        val summary = result.summary
        diagnostics.event(DiagnosticEvent.BrewResultReady)
        val startedAt = wallClock() - (clock() - detected.startedAtMs)
        val recipe = _uiState.value.recipe
        // The bag picked on the coffee step: its id and name, so the summary and the coffee history agree.
        val bag = _uiState.value.let { state -> state.bags.find { it.id == state.bagId } }
        val brew = summary.toBrew(newId(), mode, startedAt, requireNotNull(_uiState.value.dose), detected.interrupted).copy(
            recipeId = recipe?.id,
            recipeName = recipe?.name,
            coffee = bag?.name,
            bagId = bag?.id,
            grind = recipe?.grind,
            waterTempCelsius = recipe?.waterTempCelsius,
        )
        // Keep the live screen until the timer reaches the saved time, with the curve ready for the summary.
        _uiState.update { it.copy(phase = BrewPhase.DONE, saved = brew, curve = summary.curve) }
        openSummary(brew.durationMs)
        viewModelScope.launch {
            repository.save(brew, summary.curve)
            // After the insert, so the upload job always finds the brew.
            onSaved(brew.id)
            _uiState.update { if (it.saved?.id == brew.id) it.copy(notes = NotesForm.of(brew)) else it }
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

    /** Drops the brew in progress and everything recorded for it; the recipe and dose choices stay until [reset]. */
    private fun clearRun() {
        rollJob?.cancel()
        rollJob = null
        steepClockJob?.cancel()
        steepClockJob = null
        scaleWatchJob?.cancel()
        scaleWatchJob = null
        session?.clearRun()
        session = null
        lastDiagnosticPhase = null
    }

    private fun reset() {
        clearRun()
        recipeChosen = false
        coffeeBagChosen = false
        filterRecipeEdited = false
        filterRecipeBase = null
        _uiState.value = BrewUiState(
            mode,
            dose = settings.lastDose(mode),
            targetRatio = settings.targetRatio(mode),
            steepMs = settings.lastSteepMs(),
            connected = scale.state.value is ScaleState.Connected,
            weightGrams = scale.reading.value?.weightGrams ?: 0.0,
        )
    }

    /** The open bag a brew used most recently, or null when none has been used. */
    private fun lastUsedBag(bags: List<CoffeeBag>): CoffeeBag? =
        bags.mapNotNull { bag -> bag.lastBrewedAt?.let { bag to it } }.maxByOrNull { it.second }?.first

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
        diagnostics.recordBrewPhase(mode, diagnosticPhase)
    }

    companion object {
        /** The mode to brew, passed when the ViewModel is created. */
        val MODE = object : CreationExtras.Key<BrewMode> {}

        internal const val FRENCH_PRESS_ALERT_INTERVAL_MS = BrewSession.FRENCH_PRESS_ALERT_INTERVAL_MS

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
