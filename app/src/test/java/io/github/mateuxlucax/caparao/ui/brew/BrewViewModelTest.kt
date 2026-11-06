package io.github.mateuxlucax.caparao.ui.brew

import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.brew.StepEvent
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticRecorder
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticRecorderState
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.RecipeRepository
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.local.VibrationTarget
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.ScaleError
import io.github.mateuxlucax.caparao.model.ScaleReading
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.model.StepKind
import io.github.mateuxlucax.caparao.ui.FakeBrewDao
import io.github.mateuxlucax.caparao.ui.FakePreferences
import io.github.mateuxlucax.caparao.ui.FakeRecipeDao
import io.github.mateuxlucax.caparao.ui.FakeScaleSource
import io.github.mateuxlucax.caparao.ui.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** A whole brew through the ViewModel: fake scale in, saved brew out. The detectors' edge cases are in brew/. */
class BrewViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val scale = FakeScaleSource().apply { state.value = ScaleState.Connected(null) }
    private val dao = FakeBrewDao()
    private val settings = SettingsStore(FakePreferences())
    private val recipes = RecipeRepository(FakeRecipeDao())
    private var now = 0L

    /** Upload scheduling calls, each with whether the brew was in the database at that moment. */
    private val uploads = mutableListOf<String>()

    /** What the watch was asked to vibrate for. */
    private val watchAlerts = mutableListOf<StepEvent>()

    private fun viewModel(mode: BrewMode, diagnostics: DiagnosticRecorder? = null, animations: () -> Boolean = { true }) = BrewViewModel(
        mode, scale, BrewRepository(dao), settings, recipes, clock = { now }, wallClock = { 1_000_000 + now }, newId = { "brew-1" },
        onSaved = { id -> uploads += "saved $id, stored ${dao.brews.value.any { it.id == id }}" },
        onDiscarded = { id -> uploads += "discarded $id" },
        onWatchAlert = { watchAlerts += it }, diagnostics = diagnostics, animations = animations,
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

    /** Lets the timer roll back to the saved time, then the summary open (#197). */
    private fun rolled() = main.dispatcher.scheduler.advanceUntilIdle()

    @Test
    fun anEspressoIsDetectedAndSavedWithItsDose() {
        val vm = viewModel(BrewMode.ESPRESSO)
        vm.begin()
        vm.setDose(18.0)
        assertEquals(BrewStep.PREPARE_PORTAFILTER, vm.uiState.value.step)
        assertTrue(scale.commands.isEmpty())
        pour(0.0, 320.0, 0.5) // portafilter placed on the scale during preparation
        hold(320.0, 1.5)
        assertTrue("preparation does not tare", scale.commands.isEmpty())
        assertEquals(BrewStep.PREPARE_PORTAFILTER, vm.uiState.value.step)
        assertEquals(BrewPhase.WAITING_FOR_CUP, vm.uiState.value.phase)
        vm.ready()
        assertEquals(BrewPhase.WAITING_FOR_CUP, vm.uiState.value.phase)
        hold(320.0, 1.5) // the portafilter weight is the baseline, not a cup
        assertTrue("the Ready baseline is not treated as a cup", scale.commands.isEmpty())
        pour(320.0, 350.0, 0.5) // the cup goes on
        hold(350.0, 1.5)
        assertEquals(listOf("tare"), scale.commands)
        hold(0.0, 1.0) // the scale zeroed
        assertEquals(BrewPhase.READY, vm.uiState.value.phase)

        hold(0.0, 3.0) // pump on, no drops yet
        pour(0.0, 36.0, 25.0)
        assertEquals(BrewPhase.BREWING, vm.uiState.value.phase)
        assertEquals(2.0, vm.uiState.value.ratio!!, 0.01)
        hold(36.0, 6.0) // flow stopped
        rolled()

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
        assertEquals(18.0, settings.lastDose(BrewMode.ESPRESSO), 0.0)
    }

    /** An espresso brew whose live timer runs on past the saved end, as the detector dates it back (#197). */
    private fun shotThatEndsOnAFlatWeight(vm: BrewViewModel) {
        vm.begin()
        vm.setDose(18.0)
        vm.ready()
        pour(0.0, 150.0, 0.5)
        hold(150.0, 1.5)
        hold(0.0, 1.0)
        hold(0.0, 3.0)
        pour(0.0, 36.0, 25.0)
        hold(36.0, 6.0)
    }

    @Test
    fun theTimerRollsBackToTheSavedTimeBeforeTheSummaryOpens() {
        val vm = viewModel(BrewMode.ESPRESSO)
        shotThatEndsOnAFlatWeight(vm)

        val live = vm.uiState.value
        assertEquals(BrewStep.LIVE, live.step)
        assertEquals(BrewPhase.DONE, live.phase)
        val savedMs = live.saved!!.durationMs
        assertTrue("the shown time runs past the saved end", live.elapsedMs / 1000 > savedMs / 1000)

        main.dispatcher.scheduler.advanceTimeBy(300)
        val midRoll = vm.uiState.value
        assertEquals(BrewStep.LIVE, midRoll.step)
        assertTrue("the timer is between the two", midRoll.elapsedMs in savedMs until live.elapsedMs)

        rolled()
        val summary = vm.uiState.value
        assertEquals(BrewStep.SUMMARY, summary.step)
        assertEquals("the live timer and the summary end on the same number", savedMs / 1000, summary.elapsedMs / 1000)
        assertEquals("the saved brew is unchanged", savedMs, summary.saved!!.durationMs)
    }

    @Test
    fun theRollPassesThroughTheRecipesWindowColour() {
        recipes.run { kotlinx.coroutines.runBlocking { save(espresso.copy(targetMinMs = 22_000, targetMaxMs = 26_000)) } }
        val vm = viewModel(BrewMode.ESPRESSO)
        shotThatEndsOnAFlatWeight(vm)

        assertEquals("the shown time is past the window", BrewUiState.Window.PAST, vm.uiState.value.window)
        rolled()
        assertEquals("the saved time is inside it", BrewUiState.Window.INSIDE, vm.uiState.value.window)
    }

    @Test
    fun withAnimationsOffTheTimerJumpsToTheSavedTime() {
        val vm = viewModel(BrewMode.ESPRESSO, animations = { false })
        shotThatEndsOnAFlatWeight(vm)

        val state = vm.uiState.value
        assertEquals(BrewStep.SUMMARY, state.step)
        assertEquals(state.saved!!.durationMs, state.elapsedMs)
    }

    @Test
    fun aFilterBrewEndedByALiftDatedBackDoesNotRoll() {
        val vm = viewModel(BrewMode.FILTER)
        hold(320.0, 0.2) // dripper and server on the scale
        vm.begin()
        vm.setDose(17.0) // 250 g at 17 g is 1:14.7, below the band, so the lift pauses rather than ends
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, 250.0, 120.0)
        pour(250.0, 0.0, 0.5) // the dripper lifted, so the timer pauses at the drop
        hold(0.0, 6.0)
        assertEquals(BrewPhase.PAUSED, vm.uiState.value.phase)
        vm.stop()

        val state = vm.uiState.value
        assertEquals("the brew is dated back to the drop, not the end of the hold", 120.0, state.saved!!.durationMs / 1000.0, 0.5)
        assertEquals("the summary opens at once, with no roll", BrewStep.SUMMARY, state.step)
    }

    @Test
    fun aFilterLiftOfAnyLengthAfterWaterPausesAndNeverReturnsToPreparation() {
        val vm = viewModel(BrewMode.FILTER)
        hold(320.0, 0.2)
        vm.begin()
        vm.setDose(15.0)
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, 50.0, 4.0)
        hold(50.0, 1.1)
        pour(50.0, 0.0, 0.3)
        hold(0.0, 10.0) // far past the 2 s that sends a lift back to Prepare before the first water
        assertEquals(BrewPhase.PAUSED, vm.uiState.value.phase)
        assertEquals(BrewStep.LIVE, vm.uiState.value.step)
    }

    @Test
    fun aFreeFilterLiftPastTheTargetBandAlsoEndsTheBrew() {
        val vm = viewModel(BrewMode.FILTER)
        hold(320.0, 0.2)
        vm.begin()
        vm.setDose(15.0) // 240 g at 1:16
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, 260.0, 10.0) // 17.3 : 1, past the +5% edge
        hold(260.0, 1.2)
        pour(260.0, 0.0, 0.5)

        val state = vm.uiState.value
        assertEquals("a lift past the band ends the brew with no Done", BrewStep.SUMMARY, state.step)
        assertEquals(260.0, state.saved!!.yieldGrams, 0.3)
    }

    @Test
    fun aFreeFilterLiftEndsTheBrewOnlyOnceTheWaterIsInTheTargetBand() {
        val vm = viewModel(BrewMode.FILTER)
        hold(320.0, 0.2)
        vm.begin()
        vm.setDose(15.0) // 240 g of water at the default 1:16 ratio
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, 200.0, 10.0) // 13.3 : 1, below the band
        pour(200.0, 0.0, 0.5)
        assertEquals("a lift before the target pauses", BrewPhase.PAUSED, vm.uiState.value.phase)
        hold(0.0, 0.2)
        pour(0.0, 200.0, 0.5)
        hold(200.0, 2.1) // the settling window after the dripper returns
        assertEquals(BrewPhase.BREWING, vm.uiState.value.phase)
        pour(200.0, 240.0, 3.0) // 16 : 1, in the band
        hold(240.0, 1.2)
        pour(240.0, 0.0, 0.5)

        val state = vm.uiState.value
        assertEquals("a lift once in the band ends the brew at once, with no Done", BrewStep.SUMMARY, state.step)
        assertEquals(BrewPhase.DONE, state.phase)
        assertEquals(240.0, state.saved!!.yieldGrams, 0.3)
    }

    @Test
    fun aGuidedFilterLiftEndsTheBrewOnlyAfterTheLastPourTarget() {
        val (vm, _) = guidedFilter(twoPours) {
            pour(0.0, 40.0, 8.0)
            hold(40.0, 12.0)
            pour(40.0, 60.0, 5.0) // short of the last pour's 100 g
            pour(60.0, 0.0, 0.5)
        }
        assertEquals("a lift before the last pour's target pauses", BrewPhase.PAUSED, vm.uiState.value.phase)
        assertEquals(BrewStep.LIVE, vm.uiState.value.step)
        vm.end()
    }

    @Test
    fun aGuidedFilterLiftRightAfterTheLastPourReachesItsTargetEndsTheBrew() {
        // No hold at the target: the guide's own 1 s hold has not passed when the dripper comes off.
        val (vm, _) = guidedFilter(twoPours) {
            pour(0.0, 40.0, 8.0)
            hold(40.0, 12.0)
            pour(40.0, 100.0, 12.0)
            pour(100.0, 0.0, 0.5)
        }
        val state = vm.uiState.value
        assertEquals("the lift once the target is reached ends the brew", BrewStep.SUMMARY, state.step)
        // The detector confirms a rise after a short settle, so the unconfirmed last few grams are not the yield (as Done).
        assertTrue(state.saved!!.yieldGrams in 94.0..100.0)
        vm.end()
    }

    @Test
    fun aGuidedFilterLiftAfterTheLastPourTargetEndsTheBrewAtTheLift() {
        val (vm, _) = guidedFilter(twoPours) {
            pour(0.0, 40.0, 8.0)
            hold(40.0, 12.0)
            pour(40.0, 100.0, 12.0)
            hold(100.0, 1.2)
            pour(100.0, 0.0, 0.5)
        }
        val state = vm.uiState.value
        assertEquals("a lift after the last pour ends the brew with no Done", BrewStep.SUMMARY, state.step)
        assertEquals(BrewPhase.DONE, state.phase)
        assertEquals(100.0, state.saved!!.yieldGrams, 0.3)
        vm.end()
    }

    @Test
    fun readyWithoutAReadingTakesTheFirstReadingAsBaselineNotACup() {
        // Ready after a disconnect: the scale has no reading yet, so the portafilter's weight is the first one.
        val vm = viewModel(BrewMode.ESPRESSO)
        vm.begin()
        vm.setDose(18.0)
        vm.ready()
        hold(320.0, 2.0)
        assertTrue("the first reading is the baseline, not a cup", scale.commands.isEmpty())
        assertEquals(BrewPhase.WAITING_FOR_CUP, vm.uiState.value.phase)
        pour(320.0, 350.0, 0.5) // the cup goes on, 30 g above the baseline
        hold(350.0, 1.5)
        assertEquals(listOf("tare"), scale.commands)
    }

    @Test
    fun aFilterPourEndsWithDone() {
        val vm = viewModel(BrewMode.FILTER)
        hold(320.0, 0.2) // dripper and server on the scale
        vm.begin()
        vm.setDose(15.0)
        vm.ready()
        hold(320.0, 0.1)
        assertEquals(listOf("tare"), scale.commands)
        hold(0.0, 1.0)
        assertEquals(BrewPhase.READY, vm.uiState.value.phase)
        pour(0.0, 250.0, 120.0)
        vm.stop()

        val brew = vm.uiState.value.saved!!
        assertEquals("Done while the final rise is unconfirmed saves settled weight", 248.8, brew.yieldGrams, 0.1)
        assertNull(brew.timeToFirstDropMs)
        assertEquals(1, dao.brews.value.size)
    }

    @Test
    fun aFrenchPressStartsOnFirstWaterAndWaitsForDoneAfterTheCarafeIsLifted() {
        val vm = viewModel(BrewMode.FRENCH_PRESS)
        hold(320.0, 0.2)
        vm.begin()
        vm.setDose(20.0)
        assertEquals(BrewStep.PREPARE_PORTAFILTER, vm.uiState.value.step)
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        assertEquals(BrewPhase.READY, vm.uiState.value.phase)

        pour(0.0, 300.0, 30.0)
        assertEquals(BrewPhase.BREWING, vm.uiState.value.phase)
        pour(300.0, 0.0, 0.4)
        hold(0.0, 10.0)
        assertEquals("lifting the carafe does not end a French press", BrewPhase.BREWING, vm.uiState.value.phase)

        vm.stop()
        val brew = vm.uiState.value.saved!!
        assertEquals(BrewMode.FRENCH_PRESS, brew.mode)
        assertEquals(300.0, brew.yieldGrams, 0.5)
        assertNull(brew.timeToFirstDropMs)
        assertFalse(brew.interrupted)
    }

    @Test
    fun frenchPressWaitsForReadyAndReturnsToPreparationIfLiftedBeforeWater() {
        val vm = viewModel(BrewMode.FRENCH_PRESS)
        vm.begin()
        vm.setDose(20.0)
        vm.setPreparationCheck(1, true)
        hold(600.0, 2.0) // the press goes on after Beans, while the optional checklist is open
        assertEquals(BrewStep.PREPARE_PORTAFILTER, vm.uiState.value.step)
        assertTrue("placing the press does not tare before Ready", scale.commands.isEmpty())

        vm.ready()
        assertEquals(listOf("tare"), scale.commands)
        hold(0.0, 1.0)
        assertEquals(BrewPhase.READY, vm.uiState.value.phase)
        hold(-2.0, 2.2)
        assertEquals(BrewStep.PREPARE_PORTAFILTER, vm.uiState.value.step)
        assertEquals("checklist marks survive the return", setOf(1), vm.uiState.value.preparationChecks)

        hold(600.0, 0.3)
        vm.ready()
        assertEquals(listOf("tare", "tare"), scale.commands)
        hold(0.0, 1.0)
        pour(0.0, 10.0, 0.5)
        assertEquals(BrewPhase.BREWING, vm.uiState.value.phase)
        hold(-80.0, 2.5)
        assertEquals("a lift after water does not return to preparation", BrewStep.LIVE, vm.uiState.value.step)
        assertEquals(BrewPhase.BREWING, vm.uiState.value.phase)
    }

    @Test
    fun aFreePressKeepsCountingWhenTheScaleDropsDuringThePourAndSavesTheWaterAlreadyRead() {
        val alerts = mutableListOf<GuideAlert>()
        val vm = viewModel(BrewMode.FRENCH_PRESS)
        val collecting = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch { vm.alerts.collect { alerts += it } }
        hold(320.0, 0.2)
        vm.begin()
        vm.setDose(20.0)
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, 120.0, 12.0)
        pour(120.0, 0.0, 0.4)

        scale.state.value = ScaleState.Failed(ScaleError.CONNECTION_LOST, retrying = true)
        assertEquals("the brew is not interrupted", BrewPhase.BREWING, vm.uiState.value.phase)
        assertNull(vm.uiState.value.saved)
        assertFalse(vm.uiState.value.connected)

        now += 240_000
        main.dispatcher.scheduler.advanceTimeBy(300)
        main.dispatcher.scheduler.runCurrent()
        assertTrue("the deadline counts from the first water", vm.uiState.value.frenchPressDeadlineReached)
        assertEquals(1, alerts.count { it.event == StepEvent.FrenchPressDeadline })
        assertEquals(0, alerts.count { it.event == StepEvent.AllDone })
        collecting.cancel()

        vm.stop()
        rolled()
        val brew = vm.uiState.value.saved!!
        assertEquals(BrewMode.FRENCH_PRESS, brew.mode)
        assertEquals(120.0, brew.yieldGrams, 0.5)
        assertNull(brew.timeToFirstDropMs)
        assertFalse(brew.interrupted)
        vm.end()
    }

    @Test
    fun stoppingBeforeTheFirstDropSavesNothing() {
        val vm = viewModel(BrewMode.ESPRESSO)
        vm.begin()
        vm.setDose(18.0)
        vm.ready()
        hold(0.0, 1.0)
        vm.stop()
        assertEquals(BrewStep.SUMMARY, vm.uiState.value.step)
        assertNull(vm.uiState.value.saved)
        assertTrue(dao.brews.value.isEmpty())
    }

    @Test
    fun diagnosticsKeepFalseStartsAsPhasesWithoutInventingACompletedBrew() {
        val directory = Files.createTempDirectory("caparao-brew-diagnostics-").toFile()
        val recorderJob = SupervisorJob()
        val recorderScope = CoroutineScope(recorderJob + Dispatchers.IO)
        val diagnostics = DiagnosticRecorder(File(directory, "sessions"), recorderScope)
        assertTrue(runBlocking { diagnostics.start() })

        val vm = viewModel(BrewMode.ESPRESSO, diagnostics)
        vm.begin()
        vm.setDose(18.0)
        vm.ready()
        hold(0.0, 10.0) // a quiet false start: no cup or first drop was detected
        assertEquals(BrewPhase.WAITING_FOR_CUP, vm.uiState.value.phase)
        vm.stop()
        vm.end()

        val saved = runBlocking { diagnostics.stop() } as DiagnosticRecorderState.Ready
        assertTrue(saved.complete)
        val rows = saved.file.readLines().drop(1).map(::JSONObject)
        val notes = rows.map { it.getString("note") }
        assertTrue("brew_espresso_waiting_for_cup" in notes)
        assertTrue("brew_stop_requested" in notes)
        assertFalse("brew_result_ready" in notes)
        assertFalse(saved.file.readText().contains("recipe"))
        recorderJob.cancel()
        directory.deleteRecursively()
    }

    @Test
    fun aDisconnectMidBrewSavesItAsInterrupted() {
        val vm = viewModel(BrewMode.ESPRESSO)
        vm.begin()
        vm.setDose(18.0)
        vm.ready()
        vm.armNow()
        hold(0.0, 1.0)
        pour(0.0, 20.0, 10.0)
        scale.state.value = ScaleState.Failed(ScaleError.CONNECTION_LOST, retrying = true)
        val brew = vm.uiState.value.saved!!
        assertTrue(brew.interrupted)
        assertEquals(20.0, brew.yieldGrams, 0.3)
    }

    @Test
    fun retryTareWhileDisconnectedKeepsTheErrorAndSendsNothing() {
        val vm = viewModel(BrewMode.ESPRESSO)
        vm.begin()
        vm.setDose(18.0)
        vm.ready()
        vm.armNow()
        hold(150.0, 9.0) // never zeroes: the retry goes out at 3 s, the timeout shows at 8 s
        assertTrue(vm.uiState.value.tareTimedOut)
        assertEquals(listOf("tare", "tare"), scale.commands)

        scale.state.value = ScaleState.Failed(ScaleError.CONNECTION_LOST, retrying = true)
        vm.retryTare()
        // The Dot drops the command while disconnected, so Retry must neither send nor clear the error.
        assertEquals(listOf("tare", "tare"), scale.commands)
        assertTrue(vm.uiState.value.tareTimedOut)
        assertEquals(BrewPhase.TARING, vm.uiState.value.phase)

        scale.state.value = ScaleState.Connected(null)
        vm.retryTare()
        assertEquals(listOf("tare", "tare", "tare"), scale.commands)
        assertFalse(vm.uiState.value.tareTimedOut)
    }

    @Test
    fun discardDeletesTheSavedBrew() {
        val vm = viewModel(BrewMode.ESPRESSO)
        vm.begin()
        vm.setDose(15.0)
        vm.ready()
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

    private val v60 = Recipe(
        id = "v60", name = "V60", mode = BrewMode.FILTER, method = "V60", doseGrams = 15.0, targetRatio = 16.0,
        grind = "22", waterTempCelsius = 94, createdAt = 0, updatedAt = 0,
    )
    private val espresso = v60.copy(id = "esp", name = "Espresso", mode = BrewMode.ESPRESSO, method = "Espresso", doseGrams = 18.0, targetRatio = 2.4)

    @Test
    fun filterStartsFreeEvenWhenRecipesExist() = kotlinx.coroutines.test.runTest {
        recipes.save(v60)
        recipes.save(espresso)
        val vm = viewModel(BrewMode.FILTER)
        vm.begin()
        val state = vm.uiState.value
        assertEquals(listOf("v60"), state.recipes.map { it.id })
        assertNull(state.recipe)
        assertEquals(SettingsStore.defaultDose(BrewMode.FILTER), state.dose!!, 0.0)
        assertEquals(SettingsStore.defaultTargetRatio(BrewMode.FILTER), state.targetRatio, 0.0)
    }

    @Test
    fun espressoStillStartsWithItsFirstRecipe() = kotlinx.coroutines.test.runTest {
        recipes.save(espresso)
        val vm = viewModel(BrewMode.ESPRESSO)
        vm.begin()
        assertEquals("esp", vm.uiState.value.recipe?.id)
    }

    @Test
    fun filterRecipeWaterOutsideTheDoseLimitsDoesNotChangeThePlan() = kotlinx.coroutines.test.runTest {
        val recipe = v60.copy(
            targetRatio = 16.7,
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 45.0, 0),
                RecipeStep(StepKind.POUR, 90.0, 30_000),
                RecipeStep(StepKind.POUR, 250.0, 60_000),
            ),
        )
        recipes.save(recipe)
        val vm = viewModel(BrewMode.FILTER)
        vm.begin()
        vm.selectRecipe(recipe)

        vm.setFilterRecipeWater(1700.0)
        assertEquals(15.0, vm.uiState.value.dose!!, 0.0)
        assertEquals(recipe.steps, vm.uiState.value.recipe!!.steps)
        vm.setFilterRecipeWater(1.0)
        assertEquals(15.0, vm.uiState.value.dose!!, 0.0)
        assertEquals(recipe.steps, vm.uiState.value.recipe!!.steps)
        assertEquals(recipe, recipes.get(recipe.id))
    }

    @Test
    fun selectingTheAlreadySelectedRecipeKeepsThisBrewsScaledWater() = kotlinx.coroutines.test.runTest {
        val recipe = v60.copy(
            targetRatio = 16.7,
            steps = listOf(RecipeStep(StepKind.BLOOM, 45.0, 0), RecipeStep(StepKind.POUR, 250.0, 30_000)),
        )
        recipes.save(recipe)
        val vm = viewModel(BrewMode.FILTER)
        vm.begin()
        vm.selectRecipe(recipe)
        vm.setFilterRecipeWater(300.0)

        val scaled = vm.uiState.value.recipe
        vm.selectRecipe(recipe)

        assertEquals(scaled, vm.uiState.value.recipe)
        assertEquals(300.0, vm.uiState.value.recipe!!.steps.last().targetWeightGrams!!, 0.0)
    }

    @Test
    fun aRecipeUpdatedElsewhereRefreshesDoseAndRatioBeforeBrewing() = kotlinx.coroutines.test.runTest {
        val recipe = v60.copy(targetRatio = 16.7, steps = listOf(RecipeStep(StepKind.POUR, 250.0, 0)))
        recipes.save(recipe)
        val vm = viewModel(BrewMode.FILTER)
        vm.begin()
        vm.selectRecipe(recipe)
        val updated = recipe.copy(doseGrams = 18.0, targetRatio = 17.0, updatedAt = 1)

        recipes.save(updated)
        main.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(updated, vm.uiState.value.recipe)
        assertEquals(18.0, vm.uiState.value.dose!!, 0.0)
        assertEquals(17.0, vm.uiState.value.targetRatio, 0.0)
    }

    @Test
    fun aRunningGuideUsesTheRecipeStepsScaledByWater() {
        val recipe = v60.copy(
            targetRatio = 16.7,
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 45.0, 0),
                RecipeStep(StepKind.POUR, 90.0, 0),
                RecipeStep(StepKind.POUR, 250.0, 0),
            ),
        )
        runBlocking { recipes.save(recipe) }
        val vm = viewModel(BrewMode.FILTER)
        hold(320.0, 0.2)
        vm.begin()
        vm.selectRecipe(recipe)
        vm.setFilterRecipeWater(310.0)
        val scaled = listOf(55.8, 111.6, 310.0)

        vm.setDose(vm.uiState.value.dose!!)
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, scaled[0], 8.0)
        hold(scaled[0], 1.0)
        pour(scaled[0], scaled[1], 8.0)
        hold(scaled[1], 1.0)
        pour(scaled[1], scaled[2], 20.0)
        hold(scaled[2], 1.1)

        assertEquals(BrewPhase.BREWING, vm.uiState.value.phase)
        assertEquals(scaled, vm.uiState.value.steps.map { it.targetWeightGrams })
        assertEquals(recipe.steps.size, vm.uiState.value.guide!!.index)
    }

    @Test
    fun scalingFilterWaterKeepsFlowTargetsAndDrainRangeInTheGuide() = kotlinx.coroutines.test.runTest {
        val recipe = v60.copy(
            targetMinMs = 8_000,
            targetMaxMs = 12_000,
            steps = listOf(RecipeStep(StepKind.BLOOM, 60.0, 0, targetFlowGramsPerSecond = 5.0)),
        )
        recipes.save(recipe)
        val vm = viewModel(BrewMode.FILTER)
        hold(320.0, 0.2)
        vm.begin()
        vm.selectRecipe(recipe)
        vm.setFilterRecipeWater(100.0)

        val scaledPlan = vm.uiState.value.recipe!!
        assertEquals(100.0, scaledPlan.steps.single().targetWeightGrams!!, 0.0)
        assertEquals(5.0, scaledPlan.steps.single().targetFlowGramsPerSecond!!, 0.0)
        assertEquals(8_000L, scaledPlan.targetMinMs)
        assertEquals(12_000L, scaledPlan.targetMaxMs)

        vm.setDose(scaledPlan.doseGrams)
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, 100.0, 2.0)
        hold(100.0, 1.2)
        assertTrue(vm.uiState.value.guide!!.allDone(vm.uiState.value.steps.size))
        assertEquals(BrewUiState.Window.BEFORE, vm.uiState.value.drainWindow)
        hold(100.0, 6.0)
        assertEquals(BrewUiState.Window.INSIDE, vm.uiState.value.drainWindow)
        assertEquals(5.0, vm.uiState.value.recipe!!.steps.single().targetFlowGramsPerSecond!!, 0.0)
        assertEquals(8_000L, vm.uiState.value.recipe!!.targetMinMs)
        assertEquals(12_000L, vm.uiState.value.recipe!!.targetMaxMs)
    }

    @Test
    fun frenchPressWaterScalesDoseAndStepWeightsButNotTimesOrTheSavedRecipe() = kotlinx.coroutines.test.runTest {
        val recipe = io.github.mateuxlucax.caparao.data.local.BuiltInRecipes.frenchPress(0)
        recipes.save(recipe)
        val vm = viewModel(BrewMode.FRENCH_PRESS)
        vm.begin()
        vm.selectRecipe(recipe)

        vm.setFilterRecipeWater(400.0)

        val scaled = vm.uiState.value.recipe!!
        assertEquals(26.7, vm.uiState.value.dose!!, 0.05)
        assertEquals(15.0, vm.uiState.value.targetRatio, 0.0)
        assertEquals(listOf(133.3, null, 400.0, null), scaled.steps.map { it.targetWeightGrams?.let { w -> Math.round(w * 10) / 10.0 } })
        assertEquals(400.0, scaled.steps[2].targetWeightGrams!!, 0.0)
        assertEquals(recipe.steps.map { it.targetAtMs }, scaled.steps.map { it.targetAtMs })
        assertEquals(240_000L, scaled.steps.last().targetAtMs)
        assertEquals(recipe, recipes.get(recipe.id))
    }

    @Test
    fun anOlderFrenchPressRecipeScalesWithItsDefaultSteep() = kotlinx.coroutines.test.runTest {
        val full = io.github.mateuxlucax.caparao.data.local.BuiltInRecipes.frenchPress(0)
        val recipe = full.copy(steps = full.steps.dropLast(1))
        recipes.save(recipe)
        val vm = viewModel(BrewMode.FRENCH_PRESS)
        vm.begin()
        vm.selectRecipe(recipe)

        vm.setFilterRecipeWater(150.0)

        val steps = vm.uiState.value.recipe!!.steps
        assertEquals(StepKind.PRESS, steps.last().kind)
        assertEquals(240_000L, steps.last().targetAtMs)
        assertEquals(150.0, steps[2].targetWeightGrams!!, 0.0)
    }

    @Test
    fun filterRecipeDoseChangesScaleOnlyThisBrew() = kotlinx.coroutines.test.runTest {
        val recipe = v60.copy(
            targetRatio = 16.7,
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 45.0, 0),
                RecipeStep(StepKind.POUR, 90.0, 30_000),
                RecipeStep(StepKind.POUR, 250.0, 60_000),
            ),
        )
        recipes.save(recipe)
        val vm = viewModel(BrewMode.FILTER)
        vm.begin()
        vm.selectRecipe(recipe)

        vm.setFilterRecipeDose(20.0)

        val targets = vm.uiState.value.recipe!!.steps.map { it.targetWeightGrams!! }
        assertEquals(60.12, targets[0], 1e-9)
        assertEquals(120.24, targets[1], 1e-9)
        assertEquals(334.0, targets[2], 0.0)
        assertEquals(20.0, vm.uiState.value.dose!!, 0.0)
        assertEquals(recipe, recipes.get(recipe.id))
    }

    @Test
    fun filterStartsFreeAfterSelectingARecipeForThePreviousBrew() = kotlinx.coroutines.test.runTest {
        recipes.save(v60)
        settings.setLastDose(BrewMode.FILTER, 20.0)
        val vm = viewModel(BrewMode.FILTER)
        vm.begin()
        vm.selectRecipe(null)
        assertNull(vm.uiState.value.recipe)
        assertEquals(20.0, vm.uiState.value.dose!!, 0.0)
        assertEquals(SettingsStore.defaultTargetRatio(BrewMode.FILTER), vm.uiState.value.targetRatio, 0.0)
        vm.end()
        vm.begin()
        assertNull(vm.uiState.value.recipe)
        vm.selectRecipe(v60)
        vm.end()
        vm.begin()
        assertNull(vm.uiState.value.recipe)
    }

    @Test
    fun aBrewFromARecipeCarriesItsNameGrindAndTemperature() = kotlinx.coroutines.test.runTest {
        recipes.save(espresso)
        val vm = viewModel(BrewMode.ESPRESSO)
        vm.begin()
        vm.setDose(18.0)
        // The recipe's dose stays the last free dose only for free brews.
        assertEquals(SettingsStore.defaultDose(BrewMode.ESPRESSO), settings.lastDose(BrewMode.ESPRESSO), 0.0)
        vm.ready()
        vm.armNow()
        hold(0.0, 1.0)
        pour(0.0, 43.0, 28.0)
        hold(43.0, 6.0)
        val brew = vm.uiState.value.saved!!
        assertEquals("esp", brew.recipeId)
        assertEquals("Espresso", brew.recipeName)
        assertEquals("22", brew.grind)
        assertEquals(94, brew.waterTempCelsius)
        assertEquals("Espresso", BrewRepository(dao).get(brew.id)!!.recipeName)
    }

    /** A filter brew following [steps]: tared, then poured as [pours] describes; the alerts it raised. */
    private fun guidedFilter(
        steps: List<RecipeStep>,
        targetMinMs: Long? = null,
        targetMaxMs: Long? = null,
        pours: () -> Unit,
    ): Pair<BrewViewModel, List<GuideAlert>> {
        val alerts = mutableListOf<GuideAlert>()
        recipes.run { kotlinx.coroutines.runBlocking { save(v60.copy(steps = steps, targetMinMs = targetMinMs, targetMaxMs = targetMaxMs)) } }
        val vm = viewModel(BrewMode.FILTER)
        hold(320.0, 0.2)
        vm.begin()
        vm.selectRecipe(v60.copy(steps = steps, targetMinMs = targetMinMs, targetMaxMs = targetMaxMs))
        val collecting = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch { vm.alerts.collect { alerts += it } }
        vm.setDose(15.0)
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pours()
        collecting.cancel()
        return vm to alerts
    }

    private val twoPours = listOf(RecipeStep(StepKind.BLOOM, 40.0, 0), RecipeStep(StepKind.POUR, 100.0, 20_000))

    @Test
    fun aFilterRecipeGuidesStepByStepAndRaisesAlerts() {
        val (vm, alerts) = guidedFilter(twoPours) {
            pour(0.0, 40.0, 8.0)
            hold(40.0, 12.0)
            pour(40.0, 100.0, 12.0)
            hold(100.0, 1.0)
        }
        val guide = vm.uiState.value.guide!!
        assertEquals(2, guide.index)
        assertEquals(twoPours, vm.uiState.value.steps)
        val events = alerts.map { it.event }
        assertTrue(StepEvent.Reached(0) in events)
        assertTrue(StepEvent.Started(1) in events)
        assertTrue(StepEvent.Countdown(1, 5) in events)
        assertEquals(StepEvent.AllDone, events.last())
        // Vibration on, sound off by default.
        assertTrue(alerts.all { it.vibrate && !it.sound })
        vm.stop()
        assertEquals("V60", vm.uiState.value.saved!!.recipeName)
    }

    @Test
    fun filterDrainRangeAlertsOnceWhenItBeginsAndWhenItPasses() {
        settings.guideSound = true
        val (vm, alerts) = guidedFilter(twoPours, targetMinMs = 28_000, targetMaxMs = 34_000) {
            pour(0.0, 40.0, 8.0)
            hold(40.0, 12.0)
            pour(40.0, 100.0, 12.0)
            hold(100.0, 8.0)
        }

        assertEquals(BrewPhase.BREWING, vm.uiState.value.phase)
        assertEquals(1, alerts.count { it.event == StepEvent.DrainTimeEntered })
        assertEquals(1, alerts.count { it.event == StepEvent.DrainTimePassed })
        assertTrue(alerts.filter { it.event == StepEvent.DrainTimeEntered || it.event == StepEvent.DrainTimePassed }.all { it.vibrate && it.sound })
        assertEquals(1, watchAlerts.count { it == StepEvent.DrainTimeEntered })
        assertEquals(1, watchAlerts.count { it == StepEvent.DrainTimePassed })
    }

    @Test
    fun filterDrainThatJumpsPastTheWholeRangeOnlyAlertsAsPassed() {
        val lateSteps = listOf(
            RecipeStep(StepKind.BLOOM, 40.0, 0),
            RecipeStep(StepKind.POUR, 100.0, 160_000),
        )
        val (vm, alerts) = guidedFilter(lateSteps, targetMinMs = 120_000, targetMaxMs = 150_000) {
            pour(0.0, 40.0, 8.0)
            hold(40.0, 152.0)
            pour(40.0, 100.0, 5.0)
            hold(100.0, 1.1)
        }

        assertEquals(1, alerts.count { it.event == StepEvent.DrainTimePassed })
        assertEquals(0, alerts.count { it.event == StepEvent.DrainTimeEntered })
        assertEquals(1, watchAlerts.count { it == StepEvent.DrainTimePassed })
        assertEquals(0, watchAlerts.count { it == StepEvent.DrainTimeEntered })
        assertEquals(BrewUiState.Window.PAST, vm.uiState.value.drainWindow)
    }

    @Test
    fun filterDrainAlertsFollowTheVibrationAndSoundSettings() {
        settings.guideVibration = false
        settings.guideSound = false
        val (vm, alerts) = guidedFilter(twoPours, targetMinMs = 28_000, targetMaxMs = 34_000) {
            pour(0.0, 40.0, 8.0)
            hold(40.0, 12.0)
            pour(40.0, 100.0, 12.0)
            hold(100.0, 8.0)
        }

        val drainEvents = setOf(StepEvent.DrainTimeEntered, StepEvent.DrainTimePassed)
        assertEquals(BrewUiState.Window.PAST, vm.uiState.value.drainWindow)
        assertTrue(alerts.none { it.event in drainEvents })
        assertTrue(watchAlerts.none { it in drainEvents })
    }

    @Test
    fun filterDrainRangeAlertsOnceAndALiftInsideItEndsTheBrew() {
        val recipe = v60.copy(steps = twoPours, targetMinMs = 28_000, targetMaxMs = 34_000)
        runBlocking { recipes.save(recipe) }
        val vm = viewModel(BrewMode.FILTER)
        val alerts = mutableListOf<GuideAlert>()
        val collecting = CoroutineScope(Dispatchers.Main).launch { vm.alerts.collect { alerts += it } }
        hold(320.0, 0.2)
        vm.begin()
        vm.selectRecipe(recipe)
        vm.setDose(15.0)
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, 40.0, 8.0)
        hold(40.0, 12.0)
        pour(40.0, 100.0, 12.0)
        hold(100.0, 1.1)

        assertEquals(BrewUiState.Window.INSIDE, vm.uiState.value.drainWindow)
        assertEquals(1, alerts.count { it.event == StepEvent.DrainTimeEntered })
        // The drain window only opens after the last guided pour, so a lift now is past it and ends the brew (#277).
        pour(100.0, 0.0, 0.5)
        assertEquals(BrewStep.SUMMARY, vm.uiState.value.step)
        assertEquals(BrewPhase.DONE, vm.uiState.value.phase)
        hold(0.0, 5.0)
        collecting.cancel()
        assertEquals(1, alerts.count { it.event == StepEvent.DrainTimeEntered })
        assertEquals("the brew ended at the lift, so the drain time never passed", 0, alerts.count { it.event == StepEvent.DrainTimePassed })
        assertEquals("ending at the lift is not a pause", 0, alerts.count { it.event == StepEvent.Paused })
    }

    @Test
    fun aFilterPauseFreezesTheRecipeDeadlineAndRebasesTheSavedCurve() = kotlinx.coroutines.test.runTest {
        val steps = listOf(
            RecipeStep(StepKind.BLOOM, 50.0, 0),
            RecipeStep(StepKind.WAIT, null, 0),
            RecipeStep(StepKind.POUR, 100.0, 25_000),
        )
        recipes.save(v60.copy(steps = steps))
        settings.setLastRecipeId(BrewMode.FILTER, v60.id)
        val vm = viewModel(BrewMode.FILTER)
        val alerts = mutableListOf<GuideAlert>()
        val collecting = CoroutineScope(Dispatchers.Main).launch { vm.alerts.collect { alerts += it } }
        hold(320.0, 0.2)
        vm.begin()
        vm.selectRecipe(v60.copy(steps = steps))
        vm.setDose(15.0)
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        val firstWaterEpochMs = 1_000_000 + now + 100
        pour(0.0, 50.0, 4.0)
        hold(50.0, 1.1)
        assertEquals(1, vm.uiState.value.guide!!.index)
        hold(50.0, 16.0)

        pour(50.0, 0.0, 0.3)
        assertEquals(BrewPhase.PAUSED, vm.uiState.value.phase)
        assertEquals("the displayed water stays at the pre-drop level", 50.0, vm.uiState.value.weightGrams, 0.3)
        val frozenTime = vm.uiState.value.elapsedMs
        val frozenDue = vm.uiState.value.guide!!.dueInMs
        assertTrue("the next step is due before the pause ends", frozenDue!! < 5_000L)
        val countdownsBeforePause = alerts.count { it.event is StepEvent.Countdown }
        hold(0.0, 5.0)
        assertEquals(frozenTime, vm.uiState.value.elapsedMs)
        assertEquals(frozenDue, vm.uiState.value.guide!!.dueInMs)
        assertEquals("no countdown alert fires while paused", countdownsBeforePause, alerts.count { it.event is StepEvent.Countdown })

        pour(0.0, 80.0, 0.3)
        hold(80.0, 1.0)
        assertEquals(BrewPhase.BREWING, vm.uiState.value.phase)
        assertEquals("the wait still has time left after the pause", 1, vm.uiState.value.guide!!.index)
        val resumedTime = vm.uiState.value.elapsedMs
        assertTrue("the active timer resumes from its frozen value", resumedTime - frozenTime in 0L..100L)
        assertEquals("flow starts fresh after resuming", 0.0, vm.uiState.value.flowGramsPerSecond, 0.01)
        hold(80.0, 3.0)
        assertEquals("the active wait still has time left", 1, vm.uiState.value.guide!!.index)
        hold(80.0, 2.0)
        assertEquals(2, vm.uiState.value.guide!!.index)
        val activeEndTime = vm.uiState.value.elapsedMs
        assertTrue("the deadline is reached only after active time resumes", activeEndTime - frozenTime in 5_000L..5_200L)

        collecting.cancel()
        assertEquals(1, alerts.count { it.event == StepEvent.Paused })
        assertEquals(1, alerts.count { it.event == StepEvent.Resumed })
        vm.stop()
        rolled()
        val brew = vm.uiState.value.saved!!
        assertEquals("pause time is absent from the duration", activeEndTime, brew.durationMs)
        assertEquals("the original water start survives the pause", firstWaterEpochMs, brew.startedAt)
        assertEquals("water added while the dripper is away is kept", 80.0, brew.yieldGrams, 0.3)
        assertTrue("the saved flow curve does not spike across the pause seam", brew.flowPeakGramsPerSecond <= 13.5)
        val curve = dao.samples.getValue(brew.id)
        assertTrue("samples during the pause are omitted", curve.all { it.weightGrams >= 1.0 })
        assertEquals("the curve ends at the saved duration", brew.durationMs, curve.last().atMs)
    }

    @Test
    fun aPauseBeforeTheFirstRecipeTargetKeepsTheFirstStepPending() = kotlinx.coroutines.test.runTest {
        recipes.save(v60.copy(steps = twoPours))
        settings.setLastRecipeId(BrewMode.FILTER, v60.id)
        val vm = viewModel(BrewMode.FILTER)
        hold(320.0, 0.2)
        vm.begin()
        vm.selectRecipe(v60.copy(steps = twoPours))
        vm.setDose(15.0)
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, 20.0, 2.0)
        hold(20.0, 1.2)
        assertEquals(0, vm.uiState.value.guide!!.index)

        pour(20.0, 0.0, 0.3)
        assertEquals(BrewPhase.PAUSED, vm.uiState.value.phase)
        assertEquals(20.0, vm.uiState.value.weightGrams, 0.3)
        hold(0.0, 5.0)
        assertEquals("the first step stays pending while paused", 0, vm.uiState.value.guide!!.index)

        pour(0.0, 20.0, 0.3)
        hold(20.0, 1.0)
        assertEquals(BrewPhase.BREWING, vm.uiState.value.phase)
        assertEquals("the first step stays pending after resuming", 0, vm.uiState.value.guide!!.index)
        vm.stop()
    }

    @Test
    fun aFrenchPressAlertsOnceAtFourMinutesAndStaysOpenUntilDone() = kotlinx.coroutines.test.runTest {
        val frenchPress = io.github.mateuxlucax.caparao.data.local.BuiltInRecipes.frenchPress(0)
        recipes.save(frenchPress)
        val alerts = mutableListOf<GuideAlert>()
        val vm = viewModel(BrewMode.FRENCH_PRESS)
        val collecting = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch { vm.alerts.collect { alerts += it } }
        hold(320.0, 0.2)
        vm.begin()
        vm.setDose(20.0)
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, 100.0, 10.0)
        hold(100.0, 50.0)
        hold(100.0, 0.2)
        pour(100.0, 300.0, 20.0)
        val beforeFinish = alerts.count { it.event == StepEvent.FrenchPressDeadline }
        assertEquals(0, beforeFinish)
        hold(300.0, 161.0)
        assertEquals("the timer alert does not end the brew", BrewPhase.BREWING, vm.uiState.value.phase)
        assertFalse(vm.uiState.value.frenchPressRemoved)
        pour(300.0, -400.0, 0.4)
        hold(-400.0, 10.0)
        collecting.cancel()

        val finishAlerts = alerts.filter { it.event == StepEvent.FrenchPressDeadline }
        assertEquals(1, finishAlerts.size)
        assertTrue(finishAlerts.single().vibrate)
        assertFalse(finishAlerts.single().sound)
        assertEquals(1, watchAlerts.count { it == StepEvent.FrenchPressDeadline })
        assertEquals("the sustained lift does not end the brew", BrewPhase.BREWING, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.frenchPressRemoved)
        assertEquals("the live readout keeps the on-scale water", 300.0, vm.uiState.value.weightGrams, 0.5)
        assertNull(vm.uiState.value.saved)
        assertEquals(frenchPress.steps.size, vm.uiState.value.guide?.index)

        val elapsedAtDone = vm.uiState.value.elapsedMs
        vm.stop()
        rolled()
        assertEquals(BrewStep.SUMMARY, vm.uiState.value.step)
        assertEquals(BrewMode.FRENCH_PRESS, vm.uiState.value.saved?.mode)
        assertNull(vm.uiState.value.saved?.timeToFirstDropMs)
        assertEquals(300.0, vm.uiState.value.saved?.yieldGrams ?: 0.0, 0.5)
        // The 10 s between the removal and Done are not part of the saved time (#223).
        val savedMs = vm.uiState.value.saved!!.durationMs
        assertEquals(elapsedAtDone - 10_000.0, savedMs.toDouble(), 700.0)
        assertTrue(savedMs >= 240_000L)
        assertEquals(savedMs, vm.uiState.value.elapsedMs)
        val curve = dao.samples.getValue("brew-1")
        assertTrue("the off-scale weight is not a curve sample", curve.all { it.weightGrams > 0.0 })
        assertEquals(300.0, curve.last().weightGrams, 0.5)
    }

    @Test
    fun aFrenchPressDoneWithNoRemovalSavesTheTimeOfDone() = kotlinx.coroutines.test.runTest {
        recipes.save(io.github.mateuxlucax.caparao.data.local.BuiltInRecipes.frenchPress(0))
        val vm = viewModel(BrewMode.FRENCH_PRESS)
        hold(320.0, 0.2)
        vm.begin()
        vm.setDose(20.0)
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, 300.0, 20.0)
        hold(300.0, 230.0)
        assertFalse(vm.uiState.value.frenchPressRemoved)

        val elapsedAtDone = vm.uiState.value.elapsedMs
        vm.stop()
        rolled()

        assertEquals(elapsedAtDone.toDouble(), vm.uiState.value.saved!!.durationMs.toDouble(), 300.0)
    }

    @Test
    fun aFrenchPressDeadlineAlertsBelowTheFinalWaterTargetAndDoneKeepsMeasuredWater() = kotlinx.coroutines.test.runTest {
        val frenchPress = io.github.mateuxlucax.caparao.data.local.BuiltInRecipes.frenchPress(0)
        recipes.save(frenchPress)
        val alerts = mutableListOf<GuideAlert>()
        val vm = viewModel(BrewMode.FRENCH_PRESS)
        val collecting = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            vm.alerts.collect(alerts::add)
        }
        hold(320.0, 0.2)
        vm.begin()
        vm.setDose(20.0)
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, 100.0, 10.0)
        hold(100.0, 45.0)
        pour(100.0, 180.0, 8.0)
        hold(180.0, 200.0)
        collecting.cancel()

        val finishAlerts = alerts.filter { it.event == StepEvent.FrenchPressDeadline }
        assertEquals(1, finishAlerts.size)
        assertTrue(finishAlerts.single().vibrate)
        assertFalse(finishAlerts.single().sound)
        assertEquals(1, watchAlerts.count { it == StepEvent.FrenchPressDeadline })
        assertTrue(vm.uiState.value.elapsedMs >= 240_000L)
        assertEquals(BrewPhase.BREWING, vm.uiState.value.phase)
        assertEquals(180.0, vm.uiState.value.weightGrams, 0.5)
        assertFalse(vm.uiState.value.frenchPressRemoved)
        assertNull(vm.uiState.value.saved)

        pour(180.0, -400.0, 0.4)
        hold(-400.0, 10.0)
        assertEquals(BrewPhase.BREWING, vm.uiState.value.phase)
        assertEquals(180.0, vm.uiState.value.weightGrams, 0.5)
        assertTrue(vm.uiState.value.frenchPressRemoved)
        assertNull(vm.uiState.value.saved)

        vm.stop()
        val brew = vm.uiState.value.saved!!
        assertEquals(BrewMode.FRENCH_PRESS, brew.mode)
        assertEquals(180.0, brew.yieldGrams, 0.5)
        assertNull(brew.timeToFirstDropMs)
        assertFalse(brew.interrupted)
        val curve = dao.samples.getValue("brew-1")
        assertTrue("the off-scale weight is not a curve sample", curve.all { it.weightGrams > 0.0 })
        assertEquals(180.0, curve.last().weightGrams, 0.5)
    }

    @Test
    fun frenchPressDeadlineRepeatsEveryFifteenSecondsAndResumesAfterWeightReturns() = kotlinx.coroutines.test.runTest {
        recipes.save(io.github.mateuxlucax.caparao.data.local.BuiltInRecipes.frenchPress(0))
        val alerts = mutableListOf<GuideAlert>()
        val vm = viewModel(BrewMode.FRENCH_PRESS)
        val collecting = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch { vm.alerts.collect(alerts::add) }
        hold(320.0, 0.2)
        vm.begin()
        vm.setDose(20.0)
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, 100.0, 10.0)
        hold(100.0, 50.0)
        pour(100.0, 300.0, 20.0)
        hold(300.0, 176.0)

        assertEquals(1, alerts.count { it.event == StepEvent.FrenchPressDeadline })
        assertEquals(1, alerts.count { it.event == StepEvent.FrenchPressReminder })
        assertTrue(vm.uiState.value.frenchPressDeadlineReached)
        assertEquals(1, watchAlerts.count { it == StepEvent.FrenchPressDeadline })
        assertEquals(1, watchAlerts.count { it == StepEvent.FrenchPressReminder })

        pour(300.0, -400.0, 0.1)
        hold(-400.0, 1.0)
        assertEquals("a removal candidate stops reminders", 1, alerts.count { it.event == StepEvent.FrenchPressReminder })
        pour(-400.0, 300.0, 0.1)
        assertEquals("the reminder resumes when weight returns", 2, alerts.count { it.event == StepEvent.FrenchPressReminder })

        vm.stop()
        collecting.cancel()
    }

    @Test
    fun aFrenchPressSteepKeepsCountingWhenTheScaleDropsAndAlertsAtTheDeadline() = kotlinx.coroutines.test.runTest {
        recipes.save(io.github.mateuxlucax.caparao.data.local.BuiltInRecipes.frenchPress(0))
        val alerts = mutableListOf<GuideAlert>()
        val vm = viewModel(BrewMode.FRENCH_PRESS)
        val collecting = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch { vm.alerts.collect(alerts::add) }
        try {
        hold(320.0, 0.2)
        vm.begin()
        vm.setDose(20.0)
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, 100.0, 10.0)
        hold(100.0, 50.0)
        pour(100.0, 300.0, 20.0)
        hold(300.0, 60.0)

        scale.state.value = ScaleState.Failed(ScaleError.CONNECTION_LOST, retrying = true)
        assertEquals("the brew is not interrupted", BrewPhase.BREWING, vm.uiState.value.phase)
        assertFalse(vm.uiState.value.connected)
        assertTrue(alerts.none { it.event == StepEvent.FrenchPressDeadline })

        now += 110_000
        main.dispatcher.scheduler.advanceTimeBy(300)
        main.dispatcher.scheduler.runCurrent()
        assertEquals(1, alerts.count { it.event == StepEvent.FrenchPressDeadline })
        assertTrue(vm.uiState.value.frenchPressDeadlineReached)

        now += 15_000
        main.dispatcher.scheduler.advanceTimeBy(300)
        main.dispatcher.scheduler.runCurrent()
        assertEquals("it keeps repeating until Done, as removal can't be seen", 1, alerts.count { it.event == StepEvent.FrenchPressReminder })

        vm.stop()
        rolled()
        val brew = vm.uiState.value.saved!!
        assertEquals(300.0, brew.yieldGrams, 0.5)
        assertFalse(brew.interrupted)
        } finally {
            vm.end()
            collecting.cancel()
        }
    }

    @Test
    fun frenchPressDeadlineScreenRemainsVisibleWhenSettingsSilenceAlerts() = kotlinx.coroutines.test.runTest {
        settings.guideVibration = false
        settings.guideSound = false
        recipes.save(io.github.mateuxlucax.caparao.data.local.BuiltInRecipes.frenchPress(0))
        val alerts = mutableListOf<GuideAlert>()
        val vm = viewModel(BrewMode.FRENCH_PRESS)
        val collecting = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch { vm.alerts.collect(alerts::add) }
        hold(320.0, 0.2)
        vm.begin()
        vm.setDose(20.0)
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, 100.0, 10.0)
        hold(100.0, 50.0)
        pour(100.0, 300.0, 20.0)
        hold(300.0, 161.0)

        assertTrue(alerts.isEmpty())
        assertTrue(vm.uiState.value.frenchPressDeadlineReached)
        assertEquals(0, watchAlerts.count { it == StepEvent.AllDone })
        collecting.cancel()
    }

    /** A free French press brewing from the dose screen to the first water, with [steepMs] chosen when it is not the default. */
    private fun freePressPouring(steepMs: Long? = null): BrewViewModel {
        val vm = viewModel(BrewMode.FRENCH_PRESS)
        hold(320.0, 0.2)
        vm.begin()
        steepMs?.let(vm::setSteep)
        vm.setDose(20.0)
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        return vm
    }

    @Test
    fun aFreePressSteepStartsAtFourMinutesAndRemembersTheLastOne() {
        val vm = viewModel(BrewMode.FRENCH_PRESS)
        vm.begin()
        assertEquals(240_000L, vm.uiState.value.steepMs)
        vm.setSteep(5 * 60_000L)
        assertEquals(300_000L, vm.uiState.value.steepMs)
        vm.setSteep(1_000L)
        assertEquals("a steep under 10 s is ignored", 300_000L, vm.uiState.value.steepMs)

        val next = viewModel(BrewMode.FRENCH_PRESS)
        next.begin()
        assertEquals("the next free brew starts from it", 300_000L, next.uiState.value.steepMs)
    }

    @Test
    fun aFreePressSteepIsNotChosenOnceBrewingOrForARecipe() = kotlinx.coroutines.test.runTest {
        val vm = freePressPouring()
        vm.setSteep(90_000L)
        assertEquals(240_000L, vm.uiState.value.steepMs)

        recipes.save(io.github.mateuxlucax.caparao.data.local.BuiltInRecipes.frenchPress(0))
        val withRecipe = viewModel(BrewMode.FRENCH_PRESS)
        withRecipe.begin()
        withRecipe.setSteep(90_000L)
        assertEquals("a recipe carries its own steep", 240_000L, withRecipe.uiState.value.steepMs)
    }

    @Test
    fun aFreePressPoursUnguidedThenSteepsOnReachingTheTargetWater() {
        val alerts = mutableListOf<GuideAlert>()
        val vm = freePressPouring()
        val collecting = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch { vm.alerts.collect { alerts += it } }
        pour(0.0, 200.0, 15.0)
        assertNull("the ring shows while pouring", vm.uiState.value.guide)
        assertNull(vm.uiState.value.saved)

        pour(200.0, 300.0, 8.0)
        val steeping = vm.uiState.value
        assertEquals(1, steeping.guide?.index)
        assertEquals(listOf(StepKind.POUR, StepKind.PRESS), steeping.steps.map { it.kind })
        assertEquals(240_000L, steeping.steps.last().targetAtMs)
        assertNull("a free brew records no recipe", steeping.recipe)
        assertTrue(steeping.guide!!.dueInMs!! in 200_000L..240_000L)

        hold(300.0, 195.0)
        assertFalse(vm.uiState.value.frenchPressDeadlineReached)
        hold(300.0, 25.0)
        assertTrue("4:00 counts from the first water", vm.uiState.value.frenchPressDeadlineReached)
        assertEquals(1, alerts.count { it.event == StepEvent.FrenchPressDeadline })
        assertEquals(1, watchAlerts.count { it == StepEvent.FrenchPressDeadline })
        collecting.cancel()

        vm.stop()
        rolled()
        val saved = vm.uiState.value.saved!!
        assertNull(saved.recipeId)
        assertEquals(300.0, saved.yieldGrams, 0.5)
    }

    @Test
    fun aFreePressDoesNotSteepJustBecauseTheWeightRests() {
        val vm = freePressPouring()
        pour(0.0, 214.0, 15.0)
        hold(214.0, 40.0)
        assertNull("only the target water starts the steep", vm.uiState.value.guide)
        assertEquals(BrewPhase.BREWING, vm.uiState.value.phase)
    }

    @Test
    fun aFreePressDeadlinePassingDuringThePourGoesStraightToTheAlert() {
        val alerts = mutableListOf<GuideAlert>()
        val vm = freePressPouring(steepMs = 30_000L)
        val collecting = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch { vm.alerts.collect { alerts += it } }
        pour(0.0, 100.0, 20.0)
        assertNull(vm.uiState.value.guide)
        pour(100.0, 150.0, 12.0)
        assertTrue("the deadline counts from the first water", vm.uiState.value.frenchPressDeadlineReached)
        assertEquals(1, alerts.count { it.event == StepEvent.FrenchPressDeadline })
        assertEquals(0, alerts.count { it.event == StepEvent.AllDone })
        collecting.cancel()

        vm.stop()
        rolled()
        assertEquals(150.0, vm.uiState.value.saved!!.yieldGrams, 0.5)
    }

    @Test
    fun aFreePressSteepKeepsCountingWhenTheScaleDrops() {
        val vm = freePressPouring()
        pour(0.0, 300.0, 20.0)
        assertEquals(1, vm.uiState.value.guide?.index)
        scale.state.value = ScaleState.Failed(ScaleError.CONNECTION_LOST, retrying = true)
        assertEquals("the brew is not interrupted", BrewPhase.BREWING, vm.uiState.value.phase)
        now += 230_000
        main.dispatcher.scheduler.advanceTimeBy(300)
        main.dispatcher.scheduler.runCurrent()
        assertTrue(vm.uiState.value.frenchPressDeadlineReached)
        vm.end()
    }

    @Test
    fun noAlertsWhenBothAreOffButTheGuideStillRuns() {
        settings.guideVibration = false
        val (vm, alerts) = guidedFilter(twoPours) {
            pour(0.0, 40.0, 8.0)
            hold(40.0, 1.0)
        }
        assertEquals(1, vm.uiState.value.guide!!.index)
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun theWatchVibratesTooUnlessOnlyThePhoneIsChosen() {
        val (_, alerts) = guidedFilter(twoPours) { pour(0.0, 40.0, 8.0) }
        assertEquals(alerts.map { it.event }, watchAlerts)
        watchAlerts.clear()
        settings.guideVibrationOn = VibrationTarget.PHONE
        guidedFilter(twoPours) { pour(0.0, 40.0, 8.0) }
        assertTrue(watchAlerts.isEmpty())
    }

    @Test
    fun onlyTheWatchVibratesWhenChosen() {
        settings.guideVibrationOn = VibrationTarget.WATCH
        val (_, alerts) = guidedFilter(twoPours) { pour(0.0, 40.0, 8.0) }
        assertTrue(alerts.none { it.vibrate })
        assertTrue(watchAlerts.isNotEmpty())
    }

    /** The beans buzz and the guide's alerts share this: the phone vibrates only when Settings lets it (#198). */
    @Test
    fun thePhoneVibratesOnlyWhenSettingsAllowsIt() {
        val vm = viewModel(BrewMode.ESPRESSO)
        assertTrue(vm.phoneVibrates)
        settings.guideVibrationOn = VibrationTarget.WATCH
        assertFalse(vm.phoneVibrates)
        settings.guideVibrationOn = VibrationTarget.BOTH
        assertTrue(vm.phoneVibrates)
        settings.guideVibration = false
        assertFalse(vm.phoneVibrates)
    }

    @Test
    fun aFreeFilterBrewHasNoGuide() {
        val vm = viewModel(BrewMode.FILTER)
        hold(320.0, 0.2)
        vm.begin()
        vm.setDose(15.0)
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, 40.0, 8.0)
        assertNull(vm.uiState.value.guide)
    }

    @Test
    fun aFreeFilterAlertsAtFirstWaterTargetBandPauseResumeAndEndOnce() {
        val vm = viewModel(BrewMode.FILTER)
        val alerts = alertsFrom(vm)
        hold(320.0, 0.2)
        vm.begin()
        vm.setDose(15.0) // 240 g of water at the default 1:16 ratio
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)

        pour(0.0, 2.0, 0.4)
        pour(2.0, 200.0, 9.0) // below the ±5% band, whose lower edge is 228 g
        hold(200.0, 1.1)
        pour(200.0, 0.0, 0.5) // a lift below the band pauses
        assertEquals(BrewPhase.PAUSED, vm.uiState.value.phase)
        hold(0.0, 0.2)
        pour(0.0, 200.0, 0.5)
        hold(200.0, 2.1) // the settling window after the dripper returns
        assertEquals(BrewPhase.BREWING, vm.uiState.value.phase)
        pour(200.0, 230.0, 3.0) // enters the band at 228 g
        hold(230.0, 1.1)
        pour(230.0, 260.0, 2.0) // passes the +5% edge at 252 g
        hold(260.0, 1.1)

        // Hovering on and around the band edges doesn't repeat either milestone.
        listOf(251.8, 252.0, 251.8, 252.0, 260.0).forEach { grams ->
            now += 100
            scale.send(ScaleReading(grams, 0.0, 0, overload = false))
        }
        vm.stop()

        val events = alerts.map { it.event }
        assertEquals(1, events.count { it == StepEvent.FirstWater })
        assertEquals(1, events.count { it == StepEvent.InTargetBand })
        assertEquals(1, events.count { it == StepEvent.PastTargetBand })
        assertEquals(1, events.count { it == StepEvent.Paused })
        assertEquals(1, events.count { it == StepEvent.Resumed })
        assertEquals(1, events.count { it == StepEvent.AllDone })
        assertTrue(alerts.all { it.vibrate && !it.sound })
        assertEquals(events, watchAlerts)
    }

    @Test
    fun eachFilterLiftAlertsPauseAndResumeWithinOneBrew() {
        val vm = viewModel(BrewMode.FILTER)
        val alerts = alertsFrom(vm)
        hold(320.0, 0.2)
        vm.begin()
        vm.setDose(15.0)
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, 50.0, 4.0)
        hold(50.0, 1.1)

        repeat(2) {
            pour(50.0, 0.0, 0.3)
            assertEquals(BrewPhase.PAUSED, vm.uiState.value.phase)
            hold(0.0, 0.2)
            pour(0.0, 50.0, 0.3)
            hold(50.0, 1.1)
            assertEquals(BrewPhase.BREWING, vm.uiState.value.phase)
            hold(50.0, 2.1) // allow the post-resume settling window before the next lift
        }

        assertEquals(2, alerts.count { it.event == StepEvent.Paused })
        assertEquals(2, alerts.count { it.event == StepEvent.Resumed })
        assertEquals(2, watchAlerts.count { it == StepEvent.Paused })
        assertEquals(2, watchAlerts.count { it == StepEvent.Resumed })
    }

    @Test
    fun freeFilterAlertsRespectTheExistingOffSettings() {
        settings.guideVibration = false
        settings.guideSound = false
        val vm = viewModel(BrewMode.FILTER)
        val alerts = alertsFrom(vm)
        hold(320.0, 0.2)
        vm.begin()
        vm.setDose(15.0)
        vm.ready()
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, 2.0, 0.4)
        assertTrue(alerts.isEmpty())
        assertTrue(watchAlerts.isEmpty())
    }

    @Test
    fun theSummaryAsksForNotesWithTheLastCoffeeAndSavesThem() = kotlinx.coroutines.test.runTest {
        BrewRepository(dao).save(
            io.github.mateuxlucax.caparao.model.Brew("old", BrewMode.FILTER, 1, 1_000, 15.0, 250.0, null, 1.0, 2.0, false, coffee = "Caparaó"),
            emptyList(),
        )
        val (vm, _) = guidedFilter(twoPours) { pour(0.0, 40.0, 8.0) }
        vm.stop()
        val notes = vm.uiState.value.notes!!
        assertEquals("Caparaó", notes.coffee)
        assertEquals("22", notes.grind)
        assertEquals(listOf("Caparaó"), vm.uiState.value.coffees)

        vm.editNotes(notes.copy(rating = 5))
        assertTrue(vm.saveNotes())
        val stored = BrewRepository(dao).get("brew-1")!!
        assertEquals(5, stored.rating)
        assertEquals("Caparaó", stored.coffee)
        // Sent after the save and again with the notes.
        assertEquals(2, uploads.count { it.startsWith("saved brew-1") })

        vm.editNotes(notes.copy(temp = "300"))
        assertFalse(vm.saveNotes())
    }

    /** The alerts [vm] raises from now on, the way the screen plays them. */
    private fun alertsFrom(vm: BrewViewModel): MutableList<GuideAlert> {
        val alerts = mutableListOf<GuideAlert>()
        CoroutineScope(Dispatchers.Main).launch { vm.alerts.collect { alerts += it } }
        return alerts
    }

    /** An espresso brew from the cup to its first drop, poured on to [yieldTo] grams in 3 s (#193). */
    private fun espressoPastFirstDrop(vm: BrewViewModel, yieldTo: Double) {
        vm.begin()
        vm.setDose(18.0)
        vm.ready()
        pour(0.0, 150.0, 0.5) // the cup goes on
        hold(150.0, 1.5)
        hold(0.0, 1.0) // the tare confirms
        hold(0.0, 3.0)
        pour(0.0, yieldTo, 3.0) // the first drop, then the band
    }

    @Test
    fun anEspressoWithoutARecipeAlertsOnceAtEachMoment() {
        val vm = viewModel(BrewMode.ESPRESSO)
        val alerts = alertsFrom(vm)
        espressoPastFirstDrop(vm, yieldTo = 36.0)

        val events = listOf(StepEvent.CupDetected, StepEvent.TareConfirmed, StepEvent.FirstDrop, StepEvent.InTargetBand)
        assertEquals(events, alerts.map { it.event })
        assertEquals(events, watchAlerts)
        assertTrue(alerts.all { it.vibrate })
    }

    @Test
    fun anEspressoFromARecipeAlertsAtItsOwnBand() = kotlinx.coroutines.test.runTest {
        recipes.save(espresso)
        val vm = viewModel(BrewMode.ESPRESSO)
        val alerts = alertsFrom(vm)
        espressoPastFirstDrop(vm, yieldTo = 43.2) // 1:2.4, the recipe's target

        assertEquals("esp", vm.uiState.value.recipe?.id)
        assertEquals(1, alerts.count { it.event == StepEvent.InTargetBand })
        assertEquals(1, alerts.count { it.event == StepEvent.FirstDrop })
    }

    @Test
    fun theBandAlertDoesNotRepeatWhenTheWeightHoversOnItsEdge() {
        val vm = viewModel(BrewMode.ESPRESSO)
        val alerts = alertsFrom(vm)
        espressoPastFirstDrop(vm, yieldTo = 34.2) // 1:1.9, the lower edge of the band
        listOf(34.1, 34.2, 34.1, 34.2, 34.1, 34.2).forEach { grams ->
            now += 100
            scale.send(ScaleReading(grams, 0.0, 0, overload = false))
        }

        assertEquals(BrewPhase.BREWING, vm.uiState.value.phase)
        assertEquals(1, alerts.count { it.event == StepEvent.InTargetBand })
    }

    @Test
    fun turningTheVibrationOffSilencesTheEspressoAlerts() {
        settings.guideVibration = false
        val vm = viewModel(BrewMode.ESPRESSO)
        val alerts = alertsFrom(vm)
        espressoPastFirstDrop(vm, yieldTo = 36.0)

        assertTrue(alerts.isEmpty())
        assertTrue(watchAlerts.isEmpty())
    }

    @Test
    fun soundAloneStillPlaysTheEspressoAlertsWithoutVibration() {
        settings.guideVibration = false
        settings.guideSound = true
        val vm = viewModel(BrewMode.ESPRESSO)
        val alerts = alertsFrom(vm)
        espressoPastFirstDrop(vm, yieldTo = 36.0)

        assertEquals(4, alerts.size)
        assertTrue(alerts.all { !it.vibrate && it.sound })
        assertTrue(watchAlerts.isEmpty())
    }

    @Test
    fun theWatchOnlyVibratesWhenItIsTheVibrationTarget() {
        settings.guideVibrationOn = VibrationTarget.PHONE
        val vm = viewModel(BrewMode.ESPRESSO)
        val alerts = alertsFrom(vm)
        espressoPastFirstDrop(vm, yieldTo = 36.0)

        assertEquals(4, alerts.size)
        assertTrue(watchAlerts.isEmpty())
    }

    /** Moves the virtual clock by [ms] and runs what is due at exactly that time, such as the ten-second wait. */
    private fun elapse(ms: Long) {
        main.dispatcher.scheduler.advanceTimeBy(ms)
        main.dispatcher.scheduler.runCurrent()
    }

    /** A brew screen open on the dose with no scale connected yet, the dose already typed in. */
    private fun doseWaitingForScale(mode: BrewMode = BrewMode.ESPRESSO): BrewViewModel {
        scale.state.value = ScaleState.Idle
        return viewModel(mode).also {
            it.begin()
            it.planDose(18.0)
        }
    }

    @Test
    fun nextWithoutTheScaleWaitsOnTheDoseAndTheWeighingOpensWhenItConnects() {
        val vm = doseWaitingForScale()
        vm.weigh()
        assertEquals(BrewStep.DOSE, vm.uiState.value.step)
        assertTrue(vm.uiState.value.waitingForScale)
        assertFalse(scale.commands.contains("tare"))

        scale.state.value = ScaleState.Connected(null)

        assertEquals(BrewStep.WEIGH, vm.uiState.value.step)
        assertFalse(vm.uiState.value.waitingForScale)
        assertEquals(1, scale.commands.count { it == "tare" })
    }

    @Test
    fun aScaleThatConnectsWithinTenSecondsContinuesWithoutAnotherTap() {
        val vm = doseWaitingForScale()
        vm.weigh()
        elapse(4_000)

        scale.state.value = ScaleState.Connected(null)
        elapse(20_000)

        assertEquals(BrewStep.WEIGH, vm.uiState.value.step)
        assertFalse(vm.uiState.value.scaleTimedOut)
    }

    @Test
    fun aScaleThatNeverConnectsShowsNotFoundAfterTenSecondsAndStillListens() {
        val vm = doseWaitingForScale()
        vm.weigh()
        elapse(BrewViewModel.SCALE_WAIT_TIMEOUT_MS - 1)
        assertFalse(vm.uiState.value.scaleTimedOut)

        elapse(1)
        assertTrue(vm.uiState.value.scaleTimedOut)
        assertEquals(BrewStep.DOSE, vm.uiState.value.step)
        assertTrue(vm.uiState.value.waitingForScale)

        scale.state.value = ScaleState.Connected(null)

        assertEquals(BrewStep.WEIGH, vm.uiState.value.step)
        assertFalse(vm.uiState.value.scaleTimedOut)
    }

    @Test
    fun retryAsksTheScaleAgainAndStartsANewTenSecondWait() {
        val vm = doseWaitingForScale()
        vm.weigh()
        elapse(BrewViewModel.SCALE_WAIT_TIMEOUT_MS)

        vm.retryScale()
        assertFalse(vm.uiState.value.scaleTimedOut)
        assertEquals(listOf("stop", "start"), scale.commands)

        elapse(BrewViewModel.SCALE_WAIT_TIMEOUT_MS - 1)
        assertFalse(vm.uiState.value.scaleTimedOut)
        elapse(1)
        assertTrue(vm.uiState.value.scaleTimedOut)
    }

    @Test
    fun aFlappingConnectionDoesNotPostponeTheNotFoundCard() {
        val vm = doseWaitingForScale()
        vm.weigh()
        elapse(5_000)
        scale.state.value = ScaleState.Failed(ScaleError.CONNECTION_LOST, retrying = true)

        elapse(BrewViewModel.SCALE_WAIT_TIMEOUT_MS - 5_000)

        assertTrue(vm.uiState.value.scaleTimedOut)
    }

    @Test
    fun aConnectedScaleNeverShowsNotFound() {
        val vm = viewModel(BrewMode.ESPRESSO)
        vm.begin()
        elapse(20_000)

        assertFalse(vm.uiState.value.scaleTimedOut)
    }
}
