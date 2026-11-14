package dev.mateux.caparao.ui.brews

import dev.mateux.caparao.data.local.BrewRepository
import dev.mateux.caparao.data.local.CoffeeBagEntity
import dev.mateux.caparao.data.local.CoffeeBagRepository
import dev.mateux.caparao.data.local.CoffeeBagUsage
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.BrewSample
import dev.mateux.caparao.model.BrewTaste
import dev.mateux.caparao.ui.FakeBrewDao
import dev.mateux.caparao.ui.FakeCoffeeBagDao
import dev.mateux.caparao.ui.MainDispatcherRule
import dev.mateux.caparao.ui.components.NotesForm
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrewDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repository = BrewRepository(FakeBrewDao())
    private val brew = Brew("b1", BrewMode.ESPRESSO, 1_000, 28_000, 18.0, 36.0, 4_000, 1.3, 2.0, interrupted = false)
    private val curve = listOf(BrewSample(0, 0.0, 0.0), BrewSample(100, 0.4, 4.0))

    @Test
    fun loadsTheBrewAndItsCurve() = runTest {
        repository.save(brew, curve)
        val state = BrewDetailViewModel("b1", repository).uiState.value
        assertFalse(state.loading)
        assertEquals(brew, state.brew)
        assertEquals(curve, state.samples)
    }

    @Test
    fun aMissingBrewStopsLoadingWithNothing() {
        val state = BrewDetailViewModel("gone", repository).uiState.value
        assertFalse(state.loading)
        assertNull(state.brew)
        assertTrue(state.samples.isEmpty())
    }

    @Test
    fun deleteRemovesTheBrewBeforeCallingBack() = runTest {
        repository.save(brew, curve)
        var goneWhenCalled: Boolean? = null
        BrewDetailViewModel("b1", repository).delete { goneWhenCalled = true }
        assertEquals(true, goneWhenCalled)
        assertNull(repository.get("b1"))
        assertTrue(repository.samples("b1").isEmpty())
    }

    @Test
    fun editingCoffeeNotesCannotOverwriteTheRating() = runTest {
        repository.save(brew.copy(
            syncedAt = 5,
            coffee = "Caparaó",
            rating = 4,
            notes = "Sweet",
            taste = BrewTaste.BALANCED,
            tastingNotesFound = listOf("cocoa"),
            ratedAt = 4,
        ), curve)
        val sent = mutableListOf<String>()
        val vm = BrewDetailViewModel("b1", repository, onEdited = { sent += it })
        vm.editNotes()
        assertEquals("Caparaó", vm.uiState.value.notes!!.coffee)
        vm.changeNotes(vm.uiState.value.notes!!.copy(grind = "Fine"))
        vm.saveNotes()
        val saved = vm.uiState.value.brew!!
        assertEquals(4, saved.rating)
        assertEquals("Sweet", saved.notes)
        assertEquals(BrewTaste.BALANCED, saved.taste)
        assertEquals(listOf("cocoa"), saved.tastingNotesFound)
        assertEquals(4L, saved.ratedAt)
        assertEquals("Fine", saved.grind)
        assertNull(saved.syncedAt)
        assertNull(vm.uiState.value.notes)
        assertEquals(listOf("b1"), sent)
    }

    @Test
    fun theDetailObservesRatingChangesMadeOnTheRatingScreen() = runTest {
        repository.save(brew, curve)
        val vm = BrewDetailViewModel("b1", repository)

        repository.saveRating(brew.copy(rating = 5, notes = "Bright", taste = BrewTaste.SOUR, ratedAt = 8))

        assertEquals(5, vm.uiState.value.brew?.rating)
        assertEquals("Bright", vm.uiState.value.brew?.notes)
        assertEquals(BrewTaste.SOUR, vm.uiState.value.brew?.taste)
    }

    @Test
    fun unchangedOrInvalidNotesSendNothing() = runTest {
        repository.save(brew, curve)
        val sent = mutableListOf<String>()
        val vm = BrewDetailViewModel("b1", repository, onEdited = { sent += it })
        vm.editNotes()
        vm.saveNotes()
        vm.editNotes()
        vm.changeNotes(NotesForm(temp = "150"))
        vm.saveNotes()
        assertTrue(sent.isEmpty())
        assertEquals("150", vm.uiState.value.notes!!.temp)
    }

    @Test
    fun editingNotesOffersTheOpenBagsToPickFrom() = runTest {
        repository.save(brew, curve)
        val bagDao = FakeCoffeeBagDao().apply {
            usages.value = listOf(
                CoffeeBagUsage(CoffeeBagEntity("open", "Caparaó", null, null, null, 250.0, null, finished = false, createdAt = 2), 0.0, 0, null, null),
                CoffeeBagUsage(CoffeeBagEntity("done", "Mantiqueira", null, null, null, 250.0, null, finished = true, createdAt = 1), 0.0, 0, null, null),
            )
        }
        val vm = BrewDetailViewModel("b1", repository, coffeeBags = CoffeeBagRepository(bagDao))
        vm.editNotes()
        assertEquals(listOf("open"), vm.uiState.value.bags.map { it.id })
    }
}
