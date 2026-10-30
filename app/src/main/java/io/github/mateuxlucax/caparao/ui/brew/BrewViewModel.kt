package io.github.mateuxlucax.caparao.ui.brew

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.mateuxlucax.caparao.brew.BrewDetector
import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.brew.BrewSummary
import io.github.mateuxlucax.caparao.brew.EspressoDetector
import io.github.mateuxlucax.caparao.brew.FilterDetector
import io.github.mateuxlucax.caparao.brew.FlowSmoother
import io.github.mateuxlucax.caparao.brew.ScaleCommand
import io.github.mateuxlucax.caparao.brew.StepEvent
import io.github.mateuxlucax.caparao.brew.StepTracker
import io.github.mateuxlucax.caparao.brew.TimedReading
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.RecipeRepository
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.scale.ScaleSource
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.ScaleReading
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.ui.brews.NotesForm
import kotlinx.coroutines.Job
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
 * and asks for tares; the result is saved as soon as it ends (docs/adr/0006-brew-detection-in-app.md).
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(BrewUiState(mode))
    val uiState: StateFlow<BrewUiState> = _uiState.asStateFlow()

    private val _alerts = MutableSharedFlow<GuideAlert>(extraBufferCapacity = 16)

    /** The guide's moments worth a vibration or a tone, with what Settings allows; the screen plays them. */
    val alerts: SharedFlow<GuideAlert> = _alerts.asSharedFlow()

    /** Follows the recipe's steps once a filter brew starts; null for espresso, a free brew or a recipe without steps. */
    private var tracker: StepTracker? = null

    private var job: Job? = null
    private var detector: BrewDetector? = null
    private val readings = ArrayList<TimedReading>()
    private val smoother = FlowSmoother()
    private var brewingSinceMs: Long? = null

    /** Whether the recipe was chosen for this brew, by the user or by preselecting the last one. */
    private var recipeChosen = false

    /** The brew screen opened: start from the dose, listening to the scale. Does nothing while a brew is open. */
    fun begin() {
        if (job?.isActive == true) return
        reset()
        job = viewModelScope.launch {
            launch { scale.readings.collect(::onReading) }
            launch { _uiState.collect(onLive) }
            launch { recipes.recipes.collect { all -> onRecipes(all.filter { it.mode == mode }) } }
            scale.state.collect(::onScaleState)
        }
    }

    /** The screen closed: stop listening; the next [begin] starts over. */
    fun end() {
        job?.cancel()
        job = null
        reset()
        onLive(null)
    }

    /** On the dose step: follow [recipe], or none for a free brew. Its dose fills the dose field. */
    fun selectRecipe(recipe: Recipe?) {
        if (_uiState.value.step != BrewStep.DOSE) return
        recipeChosen = true
        settings.setLastRecipeId(mode, recipe?.id ?: SettingsStore.FREE_BREW)
        apply(recipe)
    }

    /** The dose was weighed or typed: start watching for the brew. */
    fun setDose(grams: Double) {
        if (_uiState.value.step != BrewStep.DOSE) return
        if (_uiState.value.recipe == null) settings.setLastDose(mode, grams)
        val new = when (mode) {
            BrewMode.ESPRESSO -> EspressoDetector()
            BrewMode.FILTER -> FilterDetector()
        }
        detector = new
        _uiState.update { it.copy(step = BrewStep.LIVE, dose = grams, phase = new.phase) }
        // The filter detector tares on its first reading; feed it the current one instead of waiting for a change.
        scale.reading.value?.let(::onReading)
    }

    /** Espresso: the cup on the scale is already tared, skip waiting for it. */
    fun armNow() {
        val espresso = detector as? EspressoDetector ?: return
        espresso.armNow(clock()).also(::send)
        _uiState.update { it.copy(phase = espresso.phase) }
    }

    /** Stop (espresso) or Done (filter); before the brew started, nothing is saved. */
    fun stop() {
        val current = detector ?: return
        if (current.phase == BrewPhase.DONE) return
        current.stop(clock())
        finish(current)
    }

    /** The notes on the summary changed. */
    fun editNotes(form: NotesForm) = _uiState.update { if (it.saved == null) it else it.copy(notes = form) }

    /**
     * Saves the notes typed on the summary into the brew just saved; it is sent again with them
     * (docs/adr/0021-brew-notes.md). Returns false when a field is invalid.
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
        if (current == null || current.phase == BrewPhase.DONE) {
            _uiState.update { it.copy(weightGrams = reading.weightGrams) }
            return
        }
        val timed = TimedReading(clock(), reading)
        if (readings.size < MAX_READINGS) readings += timed
        current.onReading(timed)?.let(::send)
        if (current.phase == BrewPhase.BREWING && brewingSinceMs == null) {
            brewingSinceMs = timed.atMs
            smoother.reset()
            val steps = _uiState.value.recipe?.steps.orEmpty()
            if (mode == BrewMode.FILTER && steps.isNotEmpty()) tracker = StepTracker(steps)
        }
        val since = brewingSinceMs
        val flow = if (since != null) smoother.add(timed) else 0.0
        val guide = tracker?.takeIf { since != null && current.phase == BrewPhase.BREWING }?.let { t ->
            t.update(timed.atMs - since!!, reading.weightGrams, flow).forEach(::alert)
            t.state
        }
        _uiState.update {
            it.copy(
                phase = current.phase,
                weightGrams = reading.weightGrams,
                flowGramsPerSecond = flow,
                elapsedMs = since?.let { s -> timed.atMs - s } ?: 0,
                guide = guide ?: it.guide,
            )
        }
        if (current.phase == BrewPhase.DONE) finish(current)
    }

    private fun onRecipes(list: List<Recipe>) {
        val state = _uiState.value
        // Before the brew, an edit elsewhere updates the chosen one; once it runs, the brew keeps what it started with.
        val current = state.recipe?.let { chosen -> list.find { it.id == chosen.id }?.takeIf { state.step == BrewStep.DOSE } ?: chosen }
        _uiState.update { it.copy(recipes = list, recipe = current) }
        if (recipeChosen || state.step != BrewStep.DOSE) return
        recipeChosen = true
        // Never chosen in this mode: the first recipe; free brew only when it was the last choice.
        val last = settings.lastRecipeId(mode)
        apply(if (last == null) list.firstOrNull() else list.find { it.id == last })
    }

    private fun apply(recipe: Recipe?) = _uiState.update {
        it.copy(
            recipe = recipe,
            dose = recipe?.doseGrams ?: settings.lastDose(mode),
            targetRatio = recipe?.targetRatio ?: settings.targetRatio(mode),
        )
    }

    private fun onScaleState(state: ScaleState) {
        _uiState.update { it.copy(connected = state is ScaleState.Connected) }
        val current = detector ?: return
        // A dropped connection ends a brew in progress as interrupted; before it started there is nothing to keep.
        if (state is ScaleState.Failed && current.phase == BrewPhase.BREWING) {
            current.interrupt(clock())
            finish(current)
        }
    }

    private fun alert(event: StepEvent) {
        val target = settings.guideVibrationOn
        val vibrate = settings.guideVibration && target.phone
        val sound = settings.guideSound
        if (settings.guideVibration && target.watch) onWatchAlert(event)
        if (vibrate || sound) _alerts.tryEmit(GuideAlert(event, vibrate, sound))
    }

    private fun send(command: ScaleCommand) = when (command) {
        ScaleCommand.TARE -> scale.tare()
    }

    private fun finish(done: BrewDetector) {
        val result = done.result
        if (result == null) {
            _uiState.update { it.copy(step = BrewStep.SUMMARY, phase = BrewPhase.DONE, saved = null) }
            return
        }
        val summary = BrewSummary.of(mode, readings, result)
        val startedAt = wallClock() - (clock() - result.startedAtMs)
        val recipe = _uiState.value.recipe
        val brew = summary.toBrew(newId(), mode, startedAt, requireNotNull(_uiState.value.dose), result.interrupted).copy(
            recipeId = recipe?.id,
            recipeName = recipe?.name,
            grind = recipe?.grind,
            waterTempCelsius = recipe?.waterTempCelsius,
        )
        // First, so the notes filled in below land on this brew's summary.
        _uiState.update { it.copy(step = BrewStep.SUMMARY, phase = BrewPhase.DONE, saved = brew) }
        viewModelScope.launch {
            // Past coffees before this brew is in: it has none yet.
            val coffees = repository.recentCoffees()
            repository.save(brew, summary.curve)
            // After the insert, so the upload job always finds the brew.
            onSaved(brew.id)
            _uiState.update { if (it.saved?.id == brew.id) it.copy(notes = NotesForm.of(brew, coffees.firstOrNull()), coffees = coffees) else it }
        }
    }

    private fun reset() {
        detector = null
        readings.clear()
        smoother.reset()
        brewingSinceMs = null
        tracker = null
        recipeChosen = false
        _uiState.value = BrewUiState(
            mode,
            dose = settings.lastDose(mode),
            targetRatio = settings.targetRatio(mode),
            connected = scale.state.value is ScaleState.Connected,
            weightGrams = scale.reading.value?.weightGrams ?: 0.0,
        )
    }

    companion object {
        /** The mode to brew, passed when the ViewModel is created. */
        val MODE = object : CreationExtras.Key<BrewMode> {}

        /** 20 minutes at 10 Hz: longer than any brew; past it the curve stops growing. */
        private const val MAX_READINGS = 12_000
    }
}
