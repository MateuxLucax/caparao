package dev.mateux.caparao.ui.brew

import android.os.SystemClock
import androidx.compose.animation.core.Easing
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras

import dev.mateux.caparao.brew.BrewPhase
import dev.mateux.caparao.brew.BrewPlan
import dev.mateux.caparao.brew.BrewSummary
import dev.mateux.caparao.brew.BrewSession
import dev.mateux.caparao.brew.FilterConfig
import dev.mateux.caparao.brew.ScaleCommand
import dev.mateux.caparao.brew.StepEvent
import dev.mateux.caparao.brew.TimedReading
import dev.mateux.caparao.data.diagnostics.DiagnosticBrewPhase
import dev.mateux.caparao.data.diagnostics.DiagnosticEvent
import dev.mateux.caparao.data.diagnostics.Diagnostics
import dev.mateux.caparao.data.diagnostics.NoDiagnostics
import dev.mateux.caparao.data.diagnostics.recordBrewPhase
import dev.mateux.caparao.data.local.BagRecipeAdjustmentRepository
import dev.mateux.caparao.model.BagRecipeAdjustment
import dev.mateux.caparao.data.local.BrewRepository
import dev.mateux.caparao.data.local.CoffeeBagRepository
import dev.mateux.caparao.data.local.RecipeRepository
import dev.mateux.caparao.data.local.SettingsStore
import dev.mateux.caparao.data.scale.ScaleSource
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.BrewLimits
import dev.mateux.caparao.model.CoffeeBag
import dev.mateux.caparao.model.Recipe
import dev.mateux.caparao.model.ScaleReading
import dev.mateux.caparao.model.ScaleState
import dev.mateux.caparao.ui.theme.Motion
import dev.mateux.caparao.watch.WatchBrewState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    /** Optional local diagnostic sink; it stores only fixed lifecycle and detector events. */
    private val diagnostics: Diagnostics = NoDiagnostics,
    /** Whether the system animates (ValueAnimator.areAnimatorsEnabled); with it off, the timer jumps to the saved time. */
    private val animations: () -> Boolean = { true },
    /** The coffee bags to pick from before weighing and on the summary (#291); null leaves the coffee step out. */
    private val coffeeBags: CoffeeBagRepository? = null,
    /** What each coffee changes in a recipe (#463); null brews every bag as the recipe says. */
    private val adjustments: BagRecipeAdjustmentRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BrewUiState(mode))
    val uiState: StateFlow<BrewUiState> = _uiState.asStateFlow()

    private val _alerts = MutableSharedFlow<GuideAlert>(extraBufferCapacity = 16)

    /** The guide's moments worth a vibration or a tone, with what Settings allows; the screen plays them. */
    val alerts: SharedFlow<GuideAlert> = _alerts.asSharedFlow()

    private val _watchAlerts = MutableSharedFlow<StepEvent>(extraBufferCapacity = 16)

    /** The guide moments the watch should vibrate for; AppContainer connects them to the watch link. */
    val watchAlerts: SharedFlow<StepEvent> = _watchAlerts.asSharedFlow()

    /** What the watch mirrors: the session's live run or the pre-brew flag, never the screen's state (#389). */
    val watchBrew: StateFlow<WatchBrewState?> =
        _uiState.map { it.toWatchBrew() }.stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value.toWatchBrew())

    /** Whether Settings lets the phone vibrate for the guide; the guide's alerts and the beans buzz both follow it. */
    val phoneVibrates: Boolean get() = settings.guideVibration && settings.guideVibrationOn.phone

    /** The Saved screen's local reminder preference; the notification is handled separately (#465). */
    val remindToRate: Boolean get() = settings.remindToRate

    fun setRemindToRate(enabled: Boolean) {
        settings.remindToRate = enabled
    }

    /** Counts the steep on the phone's clock while the scale is gone (#221). */
    private var steepClockJob: Job? = null
    /** Counts the seconds without a scale before the brew starts; the screen keeps listening (#182). */
    private var scaleWatchJob: Job? = null

    private var job: Job? = null
    /** The timer rolling back to the saved time, before the summary opens (#197). */
    private var rollJob: Job? = null
    private var session: BrewSession? = null
    private val filterConfig = FilterConfig()
    private var brewPlan = BrewPlan.create(mode, null, settings.lastDose(mode), settings.targetRatio(mode), settings.lastSteepMs())

    /** Whether the recipe was chosen for this brew, by the user or by preselecting the last one. */
    private var recipeChosen = false
    /** The user picked a coffee on the coffee step, so going back to Dose and on keeps that pick (#291). */
    private var coffeeBagChosen = false
    /** The plan as the Dose step left it, before a coffee's adjustment; the coffee step builds this brew's plan from it. */
    private var basePlan: BrewPlan? = null
    private var adjustmentsJob: Job? = null
    private var bagAdjustments: Map<String, BagRecipeAdjustment> = emptyMap()
    private var lastDiagnosticPhase: BrewPhase? = null

    /** The brew screen opened: start from the dose, listening to the scale. Does nothing while a brew is open. */
    fun begin() {
        if (job?.isActive == true) return
        reset()
        diagnostics.event(DiagnosticEvent.BrewScreenOpened)
        watchScale()
        job = viewModelScope.launch {
            launch { scale.readings.collect(::onReading) }
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
    }

    /** On the dose step: follow [recipe], or none for a free brew. Its dose fills the dose field. */
    fun selectRecipe(recipe: Recipe?) {
        val state = _uiState.value
        if (state.step != BrewStep.DOSE) return
        if (brewPlan.editedForThisBrew && state.recipe?.id == recipe?.id) {
            settings.setLastRecipeId(mode, recipe?.id ?: SettingsStore.FREE_BREW)
            return
        }
        recipeChosen = true
        settings.setLastRecipeId(mode, recipe?.id ?: SettingsStore.FREE_BREW)
        apply(recipe)
    }

    /** Change this brew's water target in ml/g; recipe pours scale while their times and ratio stay fixed. */
    fun setFilterRecipeWater(grams: Double) {
        val state = _uiState.value
        if (state.step != BrewStep.DOSE || state.recipe == null) return
        val next = brewPlan.withWater(grams) ?: return
        usePlan(next)
    }

    /** Change this brew's dose while keeping its recipe ratio. */
    fun setFilterRecipeDose(grams: Double) {
        if (grams !in BrewLimits.DOSE_GRAMS) return
        setFilterRecipeWater(grams * _uiState.value.targetRatio)
    }

    /** On the dose step: brew at 1:[ratio]. A free brew remembers it for next time. */
    fun setRatio(ratio: Double) {
        if (_uiState.value.step != BrewStep.DOSE) return
        val clamped = ratio.coerceIn(BrewLimits.RATIO.start, BrewLimits.RATIO.endInclusive)
        if (_uiState.value.recipe == null) settings.setTargetRatio(mode, clamped)
        usePlan(brewPlan.withRatio(clamped))
    }

    /** On the dose step of a free French press: how long to steep; the next free one starts from it (#218). */
    fun setSteep(ms: Long) {
        val state = _uiState.value
        if (state.step != BrewStep.DOSE || mode != BrewMode.FRENCH_PRESS || state.recipe != null || ms !in BrewLimits.STEEP_MS) return
        settings.setLastSteepMs(ms)
        usePlan(brewPlan.withSteep(ms))
    }

    /** On the dose step: the dose to weigh, typed or from the water. */
    fun planDose(grams: Double) {
        if (_uiState.value.step != BrewStep.DOSE || grams <= 0) return
        usePlan(brewPlan.withDose(grams))
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
        basePlan = brewPlan
        // Back from the coffee keeps the pick: the last bag used is only the first suggestion.
        _uiState.update {
            it.copy(step = BrewStep.COFFEE, bagId = if (coffeeBagChosen) it.bagId else (lastUsedBag(it.bags) ?: it.bags.first()).id)
        }
        watchAdjustments()
    }

    /** Follows the open bags' adjustments for the chosen recipe while the coffee is picked, so an edit shows at once. */
    private fun watchAdjustments() {
        adjustmentsJob?.cancel()
        val state = _uiState.value
        val recipeId = brewPlan.recipe?.id
        if (adjustments == null || recipeId == null || mode == BrewMode.FRENCH_PRESS || state.bags.isEmpty()) {
            bagAdjustments = emptyMap()
            _uiState.update { it.copy(adjustedBagIds = emptySet(), adjustment = null) }
            return
        }
        adjustmentsJob = viewModelScope.launch {
            combine(state.bags.map { adjustments.forBag(it.id) }) { perBag ->
                perBag.flatMap { it.toList() }
                    .filter { it.recipeId == recipeId && (it.grind != null || it.waterTempCelsius != null || it.doseGrams != null) }
                    .associateBy { it.bagId }
            }.collect { found ->
                bagAdjustments = found
                _uiState.update { it.copy(adjustedBagIds = found.keys, adjustment = found[it.bagId]) }
            }
        }
    }

    /** On the coffee step: brew this once as the recipe says, or with the coffee's adjustment again. */
    fun setFollowRecipe(follow: Boolean) {
        if (_uiState.value.step != BrewStep.COFFEE) return
        _uiState.update { it.copy(followRecipe = follow) }
    }

    /** On the coffee step: the bag this brew uses, or null for No bag. */
    fun selectBag(bagId: String?) {
        val state = _uiState.value
        if (state.step != BrewStep.COFFEE || bagId != null && state.bags.none { it.id == bagId }) return
        coffeeBagChosen = true
        _uiState.update { it.copy(bagId = bagId, adjustment = bagAdjustments[bagId], followRecipe = false) }
    }

    /** From the coffee to weighing the beans. */
    fun continueToBeans() {
        val state = _uiState.value
        if (state.step != BrewStep.COFFEE) return
        // The one place the plan takes the coffee's adjustment; the session is created from the plan as before.
        val base = basePlan ?: brewPlan
        val active = state.activeAdjustment
        usePlan(base.withAdjustment(active))
        _uiState.update { it.copy(adjustedDose = active?.doseGrams != null && brewPlan.doseGrams == active.doseGrams) }
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
        brewPlan = brewPlan.withDose(grams)
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
        // Before the beans the plan is the Dose step's again; the coffee step applies the adjustment on the way forward.
        if (state.step == BrewStep.WEIGH || state.step == BrewStep.COFFEE) basePlan?.let(::usePlan)
        if (previous == BrewStep.DOSE) {
            adjustmentsJob?.cancel()
            basePlan = null
        }
        // A wait for the scale belongs to the step it was waiting on.
        _uiState.update { it.copy(step = previous, waitingForScale = false, adjustedDose = if (previous == BrewStep.WEIGH) it.adjustedDose else false) }
    }

    /**
     * Back in Live (#287): drops the brew in progress and what was recorded, and returns to Prepare with the checklist
     * marks and the chosen recipe kept. Nothing is saved or uploaded; the next Ready starts a fresh brew.
     */
    fun restart() {
        if (_uiState.value.step != BrewStep.LIVE) return
        if (session?.phase == BrewPhase.BREWING || session?.phase == BrewPhase.PAUSED) {
            diagnostics.event(DiagnosticEvent.BrewInterrupted)
        }
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
            )
                .clearSessionMarkers()
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
        val new = BrewSession(brewPlan.withDose(grams), filterConfig)
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
        _uiState.update { it.withSessionState(state, current.guideSteps) }
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
        val savedRecipe = brewPlan.recipe?.let { chosen -> list.find { it.id == chosen.id } }
        if (savedRecipe != null) brewPlan = brewPlan.withSavedRecipe(savedRecipe, beforeBrew = state.step == BrewStep.DOSE)
        _uiState.update {
            it.copy(
                recipes = list,
                recipe = brewPlan.brewRecipe,
                dose = brewPlan.doseGrams,
                targetRatio = brewPlan.targetRatio,
                steepMs = brewPlan.steepMs,
            )
        }
        if (recipeChosen || state.step != BrewStep.DOSE) return
        recipeChosen = true
        usePlan(BrewPlan.preselected(mode, list, settings.lastRecipeId(mode), settings.lastDose(mode), settings.targetRatio(mode), settings.lastSteepMs()))
    }

    private fun apply(recipe: Recipe?) {
        usePlan(brewPlan.withRecipe(recipe, settings.lastDose(mode), settings.targetRatio(mode), settings.lastSteepMs()))
    }

    private fun usePlan(plan: BrewPlan) {
        brewPlan = plan
        _uiState.update {
            it.copy(
                recipe = plan.brewRecipe,
                dose = plan.doseGrams,
                targetRatio = plan.targetRatio,
                steepMs = plan.steepMs,
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
            current.onScaleLost(clock())
            if (current.phase == BrewPhase.BREWING) {
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
        if (settings.guideVibration && target.watch) _watchAlerts.tryEmit(event)
        if (vibrate || sound) _alerts.tryEmit(GuideAlert(event, vibrate, sound))
    }

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
                _uiState.update { it.withSessionClock(state) }
            }
        }
    }

    private fun send(command: ScaleCommand) = when (command) {
        ScaleCommand.TARE -> scale.tare()
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
        basePlan = null
        adjustmentsJob?.cancel()
        bagAdjustments = emptyMap()
        brewPlan = BrewPlan.create(mode, null, settings.lastDose(mode), settings.targetRatio(mode), settings.lastSteepMs())
        _uiState.value = BrewUiState(
            mode,
            dose = settings.lastDose(mode),
            targetRatio = settings.targetRatio(mode),
            steepMs = settings.lastSteepMs(),
            connected = scale.state.value is ScaleState.Connected,
            weightGrams = scale.reading.value?.weightGrams ?: 0.0,
        )
    }

    private fun BrewUiState.toWatchBrew() = WatchBrewState(
        beforeBrew = step == BrewStep.DOSE || step == BrewStep.COFFEE,
        mode = mode,
        phase = if (step == BrewStep.SUMMARY || phase == BrewPhase.DONE) BrewPhase.DONE else phase,
        weightGrams = weightGrams,
        flowGramsPerSecond = flowGramsPerSecond,
        elapsedMs = elapsedMs,
        guide = guide,
        steps = steps,
        frenchPressRemoved = frenchPressRemoved,
    )

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

        private const val STEEP_CLOCK_MS = 250L

        /** The timer rolls back to the saved time over this long, in frames of [ROLL_MS] / [ROLL_FRAMES]. */
        private const val ROLL_MS = 600L
        private const val ROLL_FRAMES = 36
        private val ROLL_EASING: Easing = Motion.EmphasizedDecelerate
    }
}
