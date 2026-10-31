package io.github.mateuxlucax.caparao.ui.recipes

import io.github.mateuxlucax.caparao.data.local.BuiltInRecipes
import io.github.mateuxlucax.caparao.data.local.RecipeRepository
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.StepKind
import io.github.mateuxlucax.caparao.ui.FakeRecipeDao
import io.github.mateuxlucax.caparao.ui.MainDispatcherRule
import io.github.mateuxlucax.caparao.ui.recipes.RecipeEditorViewModel.Target
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
    fun editingKeepsTheIdAndCreationTime() = runTest {
        repository.save(BuiltInRecipes.all(1)[1])
        val vm = editor(Target.Edit(BuiltInRecipes.V60_ID))
        assertFalse(vm.state.value.isNew)
        assertEquals("V60 One and Done", vm.state.value.form!!.name)
        vm.removeStep(3)
        vm.edit { it.copy(name = "V60 short") }
        vm.save()
        val saved = repository.get(BuiltInRecipes.V60_ID)!!
        assertEquals("V60 short", saved.name)
        assertEquals(4, saved.steps.size)
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
}
