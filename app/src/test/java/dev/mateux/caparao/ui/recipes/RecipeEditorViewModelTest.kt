package dev.mateux.caparao.ui.recipes

import dev.mateux.caparao.ui.components.StepForm

import dev.mateux.caparao.ui.components.RecipeForm

import dev.mateux.caparao.data.local.BuiltInRecipes
import dev.mateux.caparao.data.local.RecipeRepository
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.StepKind
import dev.mateux.caparao.ui.FakeRecipeDao
import dev.mateux.caparao.ui.MainDispatcherRule
import dev.mateux.caparao.ui.recipes.RecipeEditorViewModel.Target
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class RecipeEditorViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repository = RecipeRepository(FakeRecipeDao())

    private fun editor(target: Target) = RecipeEditorViewModel(target, repository, Locale.US, clock = { 100 }, newId = { "new-id" })

    @Test
    fun aNewFilterRecipeIsSavedAfterItsErrorsAreFixed() = runTest {
        val vm = editor(Target.New(BrewMode.FILTER))
        assertTrue(vm.state.value.isNew)
        vm.save()
        assertEquals(setOf(RecipeForm.Field.NAME, RecipeForm.Field.DOSE, RecipeForm.Field.RATIO, RecipeForm.Field.STEPS), vm.state.value.errors)
        assertFalse(vm.state.value.closed)

        vm.edit { it.copy(name = "Kalita", dose = "15") }
        assertEquals(setOf(RecipeForm.Field.RATIO, RecipeForm.Field.STEPS), vm.state.value.errors)
        vm.edit { it.copy(ratio = "16") }
        vm.updateStep(0, StepForm(StepKind.BLOOM, "45", "0:00"))
        vm.addStep()
        assertEquals("0:30", vm.state.value.form!!.steps[1].start)
        vm.updateStep(1, vm.state.value.form!!.steps[1].copy(weight = "240"))
        vm.save()

        assertTrue(vm.state.value.closed)
        val saved = repository.get("new-id")!!
        assertEquals("Kalita", saved.name)
        assertEquals(listOf(45.0, 240.0), saved.steps.map { it.targetWeightGrams })
        assertEquals(100L, saved.createdAt)
    }

    @Test
    fun theEditorOffersStirOnlyForFrenchPress() {
        assertEquals(
            listOf(StepKind.BLOOM, StepKind.POUR, StepKind.WAIT, StepKind.SWIRL),
            stepKindsFor(BrewMode.FILTER),
        )
        assertEquals(StepKind.entries, stepKindsFor(BrewMode.FRENCH_PRESS))
    }

    @Test
    fun aFrenchPressStirKeepsItsKindThroughTheRepository() = runTest {
        val vm = editor(Target.New(BrewMode.FRENCH_PRESS))
        vm.edit {
            it.copy(
                name = "French press",
                dose = "20",
                ratio = "15",
                steps = listOf(
                    StepForm(StepKind.BLOOM, "100", "0:00"),
                    StepForm(StepKind.STIR, start = "0:10"),
                    StepForm(StepKind.PRESS, start = "4:00"),
                ),
            )
        }
        vm.save()

        assertTrue(vm.state.value.closed)
        assertEquals(StepKind.STIR, repository.get("new-id")!!.steps[1].kind)
    }

    @Test
    fun editingKeepsTheIdAndCreationTime() = runTest {
        repository.save(BuiltInRecipes.all(1)[1])
        val vm = editor(Target.Edit(BuiltInRecipes.V60_ID))
        assertFalse(vm.state.value.isNew)
        assertEquals("V60 One and Done", vm.state.value.form!!.name)
        vm.removeStep(2)
        vm.edit { it.copy(name = "V60 short") }
        vm.save()
        val saved = repository.get(BuiltInRecipes.V60_ID)!!
        assertEquals("V60 short", saved.name)
        assertEquals(2, saved.steps.size)
        assertEquals(1L, saved.createdAt)
        assertEquals(100L, saved.updatedAt)
    }

    @Test
    fun deleteRemovesItAndCloses() = runTest {
        repository.save(BuiltInRecipes.all(1)[0])
        val vm = editor(Target.Edit(BuiltInRecipes.ESPRESSO_ID))
        vm.delete()
        assertTrue(vm.state.value.closed)
        assertNull(repository.get(BuiltInRecipes.ESPRESSO_ID))
    }

    @Test
    fun aMissingRecipeClosesTheEditor() = runTest {
        assertTrue(editor(Target.Edit("gone")).state.value.closed)
    }

    @Test
    fun onlyAChangedFormIsDirty() = runTest {
        val vm = editor(Target.New(BrewMode.FILTER))
        assertFalse(vm.state.value.dirty)
        vm.edit { it.copy(name = "Kalita") }
        assertTrue(vm.state.value.dirty)
        // Typing the name back out leaves the form as it opened, so nothing is lost by leaving.
        vm.edit { it.copy(name = "") }
        assertFalse(vm.state.value.dirty)
    }

    @Test
    fun aLoadedRecipeIsCleanUntilEdited() = runTest {
        repository.save(BuiltInRecipes.all(1)[1])
        val vm = editor(Target.Edit(BuiltInRecipes.V60_ID))
        assertFalse(vm.state.value.dirty)
        vm.edit { it.copy(name = "V60 short") }
        assertTrue(vm.state.value.dirty)
    }

    /** The timeline marks each step's start and ends at the ideal time when that is later (#470). */
    @Test
    fun timelineMarksStepStartsAndTheIdealEnd() {
        val form = RecipeForm.new(BrewMode.FILTER).copy(
            maxTime = "2:50",
            steps = listOf(StepForm(StepKind.BLOOM, "40", "0:00"), StepForm(StepKind.POUR, "300", "0:30")),
        )
        assertEquals(listOf(0L, 30_000L, 170_000L), timelineMarks(form))
        assertEquals(listOf(0L, 30_000L), timelineMarks(form.copy(maxTime = "")))
    }

    /** A moved step takes the start of the place it lands on, so the list stays in time order (#471). */
    @Test
    fun movingAStepKeepsTheStartTimesInPlace() {
        val vm = editor(Target.New(BrewMode.FILTER))
        vm.edit {
            it.copy(steps = listOf(StepForm(StepKind.BLOOM, "40", "0:00"), StepForm(StepKind.POUR, "300", "0:30", "5"), StepForm(StepKind.WAIT, start = "2:00")))
        }
        vm.moveStep(2, 1)
        assertEquals(
            listOf(StepForm(StepKind.BLOOM, "40", "0:00"), StepForm(StepKind.WAIT, start = "0:30"), StepForm(StepKind.POUR, "300", "2:00", "5")),
            vm.state.value.form!!.steps,
        )
        vm.moveStep(0, 5)
        assertEquals(StepKind.BLOOM, vm.state.value.form!!.steps.first().kind)
    }

    /** A new step starts 30 s after the last one and pours at the last pour's flow (#471). */
    @Test
    fun aNewStepTakesItsDefaultsFromTheStepsBefore() {
        val vm = editor(Target.New(BrewMode.FILTER))
        vm.edit { it.copy(steps = listOf(StepForm(StepKind.BLOOM, "40", "0:00", "4"), StepForm(StepKind.WAIT, start = "0:30"))) }
        vm.addStep()
        assertEquals(StepForm(StepKind.POUR, start = "1:00", flowTarget = "4"), vm.state.value.form!!.steps.last())
    }
}
