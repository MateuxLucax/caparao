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
import io.github.mateuxlucax.caparao.brew.TimedReading
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.scale.ScaleSource
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.ScaleReading
import io.github.mateuxlucax.caparao.model.ScaleState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    /** Monotonic, for the detector. */
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    /** Epoch, for the saved start time. */
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    /** Called once a brew is in the database (UploadScheduler.brewSaved). */
    private val onSaved: (String) -> Unit = {},
    /** Called when a saved brew is discarded (UploadScheduler.brewDeleted). */
    private val onDiscarded: (String) -> Unit = {},
) : ViewModel() {
    private val _uiState = MutableStateFlow(BrewUiState(mode))
    val uiState: StateFlow<BrewUiState> = _uiState.asStateFlow()

    private var job: Job? = null
    private var detector: BrewDetector? = null
    private val readings = ArrayList<TimedReading>()
    private val smoother = FlowSmoother()
    private var brewingSinceMs: Long? = null

    /** The brew screen opened: start from the dose, listening to the scale. Does nothing while a brew is open. */
    fun begin() {
        if (job?.isActive == true) return
        reset()
        job = viewModelScope.launch {
            launch { scale.readings.collect(::onReading) }
            scale.state.collect(::onScaleState)
        }
    }

    /** The screen closed: stop listening; the next [begin] starts over. */
    fun end() {
        job?.cancel()
        job = null
        reset()
    }

    /** The dose was weighed or typed: start watching for the brew. */
    fun setDose(grams: Double) {
        if (_uiState.value.step != BrewStep.DOSE) return
        settings.setLastDose(mode, grams)
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
        }
        val since = brewingSinceMs
        val flow = if (since != null) smoother.add(timed) else 0.0
        _uiState.update {
            it.copy(
                phase = current.phase,
                weightGrams = reading.weightGrams,
                flowGramsPerSecond = flow,
                elapsedMs = since?.let { s -> timed.atMs - s } ?: 0,
            )
        }
        if (current.phase == BrewPhase.DONE) finish(current)
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
        val brew = summary.toBrew(newId(), mode, startedAt, requireNotNull(_uiState.value.dose), result.interrupted)
        viewModelScope.launch {
            repository.save(brew, summary.curve)
            // After the insert, so the upload job always finds the brew.
            onSaved(brew.id)
        }
        _uiState.update { it.copy(step = BrewStep.SUMMARY, phase = BrewPhase.DONE, saved = brew) }
    }

    private fun reset() {
        detector = null
        readings.clear()
        smoother.reset()
        brewingSinceMs = null
        _uiState.value = BrewUiState(
            mode,
            dose = settings.lastDose(mode),
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
