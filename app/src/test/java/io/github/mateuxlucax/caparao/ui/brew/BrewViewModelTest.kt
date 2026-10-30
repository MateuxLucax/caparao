package io.github.mateuxlucax.caparao.ui.brew

import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.brew.StepEvent
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
    private val recipes = RecipeRepository(FakeRecipeDao())
    private var now = 0L

    /** Upload scheduling calls, each with whether the brew was in the database at that moment. */
    private val uploads = mutableListOf<String>()

    /** What the watch was asked to vibrate for. */
    private val watchAlerts = mutableListOf<StepEvent>()

    private fun viewModel(mode: BrewMode) = BrewViewModel(
        mode, scale, BrewRepository(dao), settings, recipes, clock = { now }, wallClock = { 1_000_000 + now }, newId = { "brew-1" },
        onSaved = { id -> uploads += "saved $id, stored ${dao.brews.value.any { it.id == id }}" },
        onDiscarded = { id -> uploads += "discarded $id" },
        onWatchAlert = { watchAlerts += it },
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
        assertNull(settings.lastDose(BrewMode.ESPRESSO))
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
    fun noAlertsWhenBothAreOffButTheGuideStillRuns() {
        settings.guideVibration = false
        val (vm, alerts) = guidedFilter(twoPours) { pour(0.0, 40.0, 8.0) }
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
}
