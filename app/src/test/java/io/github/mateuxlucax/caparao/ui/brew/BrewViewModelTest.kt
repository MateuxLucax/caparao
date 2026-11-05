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
        vm.setDose(15.0)
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, 250.0, 120.0)
        pour(250.0, 0.0, 0.5) // the dripper lifted...
        hold(0.0, 6.0) // ...and held past the lift hold, so the brew ends at the drop

        val state = vm.uiState.value
        assertEquals("the brew is dated back to the drop, not the end of the hold", 120.0, state.saved!!.durationMs / 1000.0, 0.5)
        assertEquals("the summary opens at once, with no roll", BrewStep.SUMMARY, state.step)
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
    fun aFrenchPressDisconnectDuringTheBrewSavesTheHighestWaterWeightAsInterrupted() {
        val vm = viewModel(BrewMode.FRENCH_PRESS)
        hold(320.0, 0.2)
        vm.begin()
        vm.setDose(20.0)
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, 120.0, 12.0)
        pour(120.0, 0.0, 0.4)

        scale.state.value = ScaleState.Failed(ScaleError.CONNECTION_LOST, retrying = true)

        val brew = vm.uiState.value.saved!!
        assertEquals(BrewMode.FRENCH_PRESS, brew.mode)
        assertEquals(120.0, brew.yieldGrams, 0.5)
        assertNull(brew.timeToFirstDropMs)
        assertTrue(brew.interrupted)
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
    fun theFirstRecipeIsPreselectedWhenNoneWasChosenBefore() = kotlinx.coroutines.test.runTest {
        recipes.save(v60)
        recipes.save(espresso)
        val vm = viewModel(BrewMode.FILTER)
        vm.begin()
        val state = vm.uiState.value
        assertEquals(listOf("v60"), state.recipes.map { it.id })
        assertEquals("v60", state.recipe?.id)
        assertEquals(15.0, state.dose!!, 0.0)
        assertEquals(16.0, state.targetRatio, 0.0)
    }

    @Test
    fun theLastChoiceIsRememberedPerModeIncludingFreeBrew() = kotlinx.coroutines.test.runTest {
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
        assertEquals("v60", vm.uiState.value.recipe?.id)
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
    private fun guidedFilter(steps: List<RecipeStep>, pours: () -> Unit): Pair<BrewViewModel, List<GuideAlert>> {
        val alerts = mutableListOf<GuideAlert>()
        recipes.run { kotlinx.coroutines.runBlocking { save(v60.copy(steps = steps)) } }
        val vm = viewModel(BrewMode.FILTER)
        hold(320.0, 0.2)
        vm.begin()
        val collecting = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch { vm.alerts.collect { alerts += it } }
        vm.setDose(15.0)
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
    fun aFrenchPressAlertsOnceAtFourMinutesAndStaysOpenUntilDone() = kotlinx.coroutines.test.runTest {
        val frenchPress = io.github.mateuxlucax.caparao.data.local.BuiltInRecipes.frenchPress(0)
        recipes.save(frenchPress)
        val alerts = mutableListOf<GuideAlert>()
        val vm = viewModel(BrewMode.FRENCH_PRESS)
        val collecting = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch { vm.alerts.collect { alerts += it } }
        hold(320.0, 0.2)
        vm.begin()
        vm.setDose(20.0)
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, 100.0, 10.0)
        hold(100.0, 50.0)
        hold(100.0, 0.2)
        pour(100.0, 300.0, 20.0)
        val beforeFinish = alerts.count { it.event == StepEvent.AllDone }
        assertEquals(0, beforeFinish)
        hold(300.0, 161.0)
        assertEquals("the timer alert does not end the brew", BrewPhase.BREWING, vm.uiState.value.phase)
        assertFalse(vm.uiState.value.frenchPressRemoved)
        pour(300.0, -400.0, 0.4)
        hold(-400.0, 10.0)
        collecting.cancel()

        val finishAlerts = alerts.filter { it.event == StepEvent.AllDone }
        assertEquals(1, finishAlerts.size)
        assertTrue(finishAlerts.single().vibrate)
        assertFalse(finishAlerts.single().sound)
        assertEquals(1, watchAlerts.count { it == StepEvent.AllDone })
        assertEquals("the sustained lift does not end the brew", BrewPhase.BREWING, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.frenchPressRemoved)
        assertEquals("the live readout keeps the on-scale water", 300.0, vm.uiState.value.weightGrams, 0.5)
        assertNull(vm.uiState.value.saved)
        assertEquals(frenchPress.steps.size, vm.uiState.value.guide?.index)

        vm.stop()
        rolled()
        assertEquals(BrewStep.SUMMARY, vm.uiState.value.step)
        assertEquals(BrewMode.FRENCH_PRESS, vm.uiState.value.saved?.mode)
        assertNull(vm.uiState.value.saved?.timeToFirstDropMs)
        assertEquals(300.0, vm.uiState.value.saved?.yieldGrams ?: 0.0, 0.5)
        val curve = dao.samples.getValue("brew-1")
        assertTrue("the off-scale weight is not a curve sample", curve.all { it.weightGrams > 0.0 })
        assertEquals(300.0, curve.last().weightGrams, 0.5)
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
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, 100.0, 10.0)
        hold(100.0, 45.0)
        pour(100.0, 180.0, 8.0)
        hold(180.0, 200.0)
        collecting.cancel()

        val finishAlerts = alerts.filter { it.event == StepEvent.AllDone }
        assertEquals(1, finishAlerts.size)
        assertTrue(finishAlerts.single().vibrate)
        assertFalse(finishAlerts.single().sound)
        assertEquals(1, watchAlerts.count { it == StepEvent.AllDone })
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
        hold(320.0, 0.1)
        hold(0.0, 1.0)
        pour(0.0, 40.0, 8.0)
        assertNull(vm.uiState.value.guide)
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
}
