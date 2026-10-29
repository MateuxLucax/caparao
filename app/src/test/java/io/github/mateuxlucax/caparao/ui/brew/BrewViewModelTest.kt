package io.github.mateuxlucax.caparao.ui.brew

import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.ScaleError
import io.github.mateuxlucax.caparao.model.ScaleReading
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.ui.FakeBrewDao
import io.github.mateuxlucax.caparao.ui.FakePreferences
import io.github.mateuxlucax.caparao.ui.FakeScaleSource
import io.github.mateuxlucax.caparao.ui.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** A whole brew through the ViewModel: fake scale in, saved brew out. The detectors' edge cases are in brew/. */
class BrewViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val scale = FakeScaleSource().apply { state.value = ScaleState.Connected(null) }
    private val dao = FakeBrewDao()
    private val settings = SettingsStore(FakePreferences())
    private var now = 0L

    /** Upload scheduling calls, each with whether the brew was in the database at that moment. */
    private val uploads = mutableListOf<String>()

    private fun viewModel(mode: BrewMode) = BrewViewModel(
        mode, scale, BrewRepository(dao), settings, clock = { now }, wallClock = { 1_000_000 + now }, newId = { "brew-1" },
        onSaved = { id -> uploads += "saved $id, stored ${dao.brews.value.any { it.id == id }}" },
        onDiscarded = { id -> uploads += "discarded $id" },
    )

    /** [seconds] of readings at 10 Hz, the weight moving linearly from [from] to [to]. */
    private fun pour(from: Double, to: Double, seconds: Double) {
        val steps = (seconds * 10).toInt()
        repeat(steps) { i ->
            now += 100
            scale.send(ScaleReading(Math.round((from + (to - from) * (i + 1) / steps) * 10) / 10.0, 0.0, 0, overload = false))
        }
    }

    private fun hold(weight: Double, seconds: Double) = pour(weight, weight, seconds)

    @Test
    fun anEspressoIsDetectedAndSavedWithItsDose() {
        val vm = viewModel(BrewMode.ESPRESSO)
        vm.begin()
        vm.setDose(18.0)
        assertEquals(BrewPhase.WAITING_FOR_CUP, vm.uiState.value.phase)

        pour(0.0, 150.0, 0.5) // the cup goes on
        hold(150.0, 1.5)
        assertEquals(listOf("tare"), scale.commands)
        hold(0.0, 1.0) // the scale zeroed
        assertEquals(BrewPhase.READY, vm.uiState.value.phase)

        hold(0.0, 3.0) // pump on, no drops yet
        pour(0.0, 36.0, 25.0)
        assertEquals(BrewPhase.BREWING, vm.uiState.value.phase)
        assertEquals(2.0, vm.uiState.value.ratio!!, 0.01)
        hold(36.0, 6.0) // flow stopped

        val state = vm.uiState.value
        assertEquals(BrewStep.SUMMARY, state.step)
        val brew = state.saved!!
        assertEquals(36.0, brew.yieldGrams, 0.3)
        assertEquals(25.0, brew.durationMs / 1000.0, 1.0)
        assertEquals(18.0, brew.doseGrams, 0.0)
        assertEquals(2.0, brew.ratio, 0.05)
        assertFalse(brew.interrupted)
        assertEquals(listOf("brew-1"), dao.brews.value.map { it.id })
        assertTrue("the curve is saved", dao.samples.getValue("brew-1").size > 200)
        assertEquals(18.0, settings.lastDose(BrewMode.ESPRESSO)!!, 0.0)
    }

    @Test
    fun aFilterPourEndsWithDone() {
        val vm = viewModel(BrewMode.FILTER)
        hold(320.0, 0.2) // dripper and server on the scale
        vm.begin()
        vm.setDose(15.0)
        hold(320.0, 0.1)
        assertEquals(listOf("tare"), scale.commands)
        hold(0.0, 1.0)
        assertEquals(BrewPhase.READY, vm.uiState.value.phase)
        pour(0.0, 250.0, 120.0)
        vm.stop()

        val brew = vm.uiState.value.saved!!
        assertEquals(250.0, brew.yieldGrams, 0.5)
        assertNull(brew.timeToFirstDropMs)
        assertEquals(1, dao.brews.value.size)
    }

    @Test
    fun stoppingBeforeTheFirstDropSavesNothing() {
        val vm = viewModel(BrewMode.ESPRESSO)
        vm.begin()
        vm.setDose(18.0)
        hold(0.0, 1.0)
        vm.stop()
        assertEquals(BrewStep.SUMMARY, vm.uiState.value.step)
        assertNull(vm.uiState.value.saved)
        assertTrue(dao.brews.value.isEmpty())
    }

    @Test
    fun aDisconnectMidBrewSavesItAsInterrupted() {
        val vm = viewModel(BrewMode.ESPRESSO)
        vm.begin()
        vm.setDose(18.0)
        vm.armNow()
        hold(0.0, 1.0)
        pour(0.0, 20.0, 10.0)
        scale.state.value = ScaleState.Failed(ScaleError.CONNECTION_LOST, retrying = true)
        val brew = vm.uiState.value.saved!!
        assertTrue(brew.interrupted)
        assertEquals(20.0, brew.yieldGrams, 0.3)
    }

    @Test
    fun discardDeletesTheSavedBrew() {
        val vm = viewModel(BrewMode.ESPRESSO)
        vm.begin()
        vm.setDose(15.0)
        vm.armNow()
        hold(0.0, 1.0)
        pour(0.0, 30.0, 20.0)
        vm.stop()
        assertEquals(1, dao.brews.value.size)
        vm.discard()
        assertTrue(dao.brews.value.isEmpty())
        assertEquals(listOf("saved brew-1, stored true", "discarded brew-1"), uploads)
    }

    @Test
    fun aNewBrewStartsFromTheDoseWithTheLastOne() {
        val vm = viewModel(BrewMode.ESPRESSO)
        vm.begin()
        vm.setDose(18.5)
        vm.end()
        vm.begin()
        assertEquals(BrewStep.DOSE, vm.uiState.value.step)
        assertEquals(18.5, vm.uiState.value.dose!!, 0.0)
        assertEquals(emptyList<String>(), scale.commands)
    }

    @Test
    fun theTargetRatioComesFromSettings() {
        settings.setTargetRatio(BrewMode.ESPRESSO, 2.5)
        val vm = viewModel(BrewMode.ESPRESSO)
        vm.begin()
        assertEquals(2.5, vm.uiState.value.targetRatio, 0.001)
        assertEquals(16.0, viewModel(BrewMode.FILTER).apply { begin() }.uiState.value.targetRatio, 0.001)
    }
}
