package dev.mateux.caparao.ui.brews

import dev.mateux.caparao.data.local.BrewRepository
import dev.mateux.caparao.data.local.CoffeeBagRepository
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.BrewTaste
import dev.mateux.caparao.model.CoffeeBag
import dev.mateux.caparao.ui.FakeBrewDao
import dev.mateux.caparao.ui.FakeCoffeeBagDao
import dev.mateux.caparao.ui.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrewRatingViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val brewDao = FakeBrewDao()
    private val repository = BrewRepository(brewDao)
    private val bagRepository = CoffeeBagRepository(FakeCoffeeBagDao())
    private val brew = Brew(
        id = "b1", mode = BrewMode.ESPRESSO, startedAt = 1_000, durationMs = 28_000,
        doseGrams = 18.0, yieldGrams = 36.0, timeToFirstDropMs = 4_000,
        flowAvgGramsPerSecond = 1.3, flowPeakGramsPerSecond = 2.0, interrupted = false, bagId = "bag1",
    )

    @Test
    fun loadsSavedBrewAndItsBagTastingNotes() = runTest {
        repository.save(brew, emptyList())
        bagRepository.save(CoffeeBag("bag1", "Caparaó", weightGrams = 250.0, createdAt = 1, tastingNotes = listOf("Chocolate", "Red fruit")))

        val state = BrewRatingViewModel("b1", repository, bagRepository).uiState.value

        assertFalse(state.loading)
        assertEquals(brew, state.brew)
        assertEquals(listOf("Chocolate", "Red fruit"), state.bagTastingNotes)
    }

    @Test
    fun reopeningARatedBrewPrefillsItsRatingAndFoundNotes() = runTest {
        val rated = brew.copy(
            rating = 4,
            notes = "Peach and honey",
            taste = BrewTaste.BALANCED,
            tastingNotesFound = listOf("Chocolate"),
            ratedAt = 2_000,
        )
        repository.save(rated, emptyList())
        bagRepository.save(CoffeeBag("bag1", "Caparaó", weightGrams = 250.0, createdAt = 1, tastingNotes = listOf("Chocolate", "Red fruit")))

        val state = BrewRatingViewModel("b1", repository, bagRepository).uiState.value

        assertEquals(BrewTaste.BALANCED, state.taste)
        assertEquals(setOf("Chocolate"), state.tastingNotesFound)
        assertEquals(4, state.rating)
        assertEquals("Peach and honey", state.notes)
    }

    @Test
    fun selectedTasteAndBagNotesCanBeClearedAndOnlyBagNotesAreKept() = runTest {
        repository.save(brew, emptyList())
        bagRepository.save(CoffeeBag("bag1", "Caparaó", weightGrams = 250.0, createdAt = 1, tastingNotes = listOf("Chocolate")))
        val vm = BrewRatingViewModel("b1", repository, bagRepository)

        vm.setTaste(BrewTaste.SOUR)
        vm.setTaste(BrewTaste.SOUR)
        vm.setTaste(BrewTaste.BALANCED)
        vm.toggleTastingNote("Chocolate")
        vm.toggleTastingNote("not in the bag")
        vm.toggleTastingNote("Chocolate")

        assertEquals(BrewTaste.BALANCED, vm.uiState.value.taste)
        assertTrue(vm.uiState.value.tastingNotesFound.isEmpty())
        vm.setTaste(BrewTaste.BALANCED)
        assertNull(vm.uiState.value.taste)
        vm.setTaste(BrewTaste.BITTER)
        vm.toggleTastingNote("Chocolate")
        vm.setRating(6)
        assertNull(vm.uiState.value.rating)
        vm.setRating(4)
        vm.setNotes(" A little sweet ")
        assertEquals(4, vm.uiState.value.rating)
        assertEquals(" A little sweet ", vm.uiState.value.notes)
    }

    @Test
    fun saveMarksBrewRatedAndCallsBackAfterPersistence() = runTest {
        repository.save(brew, emptyList())
        bagRepository.save(CoffeeBag("bag1", "Caparaó", weightGrams = 250.0, createdAt = 1, tastingNotes = listOf("Chocolate", "Red fruit")))
        var savedId: String? = null
        var callbackSawPersistedRating = false
        var closed = false
        val vm = BrewRatingViewModel("b1", repository, bagRepository, onSaved = { id ->
            savedId = id
            callbackSawPersistedRating = repository.get(id)?.ratedAt != null
        })

        vm.setTaste(BrewTaste.BALANCED)
        vm.toggleTastingNote("Chocolate")
        vm.setRating(5)
        vm.setNotes("  Peach and honey  ")
        vm.save { closed = true }

        val saved = repository.get("b1")!!
        assertEquals(BrewTaste.BALANCED, saved.taste)
        assertEquals(listOf("Chocolate"), saved.tastingNotesFound)
        assertEquals(5, saved.rating)
        assertEquals("Peach and honey", saved.notes)
        assertNotNull(saved.ratedAt)
        assertEquals(1, saved.revision)
        assertEquals("b1", savedId)
        assertTrue(callbackSawPersistedRating)
        assertTrue(closed)
    }

    @Test
    fun savingWithoutAnyRatingDoesNotMarkOrChangeAnUnratedBrew() = runTest {
        repository.save(brew, emptyList())
        var saved = false
        var closed = false
        val vm = BrewRatingViewModel("b1", repository, bagRepository, onSaved = { saved = true })

        vm.save { closed = true }

        val unchanged = repository.get("b1")!!
        assertNull(unchanged.ratedAt)
        assertEquals(0, unchanged.revision)
        assertNull(unchanged.syncedAt)
        assertFalse(saved)
        assertTrue(closed)
    }

    @Test
    fun savingUnchangedValuesOnARatedBrewDoesNotReupload() = runTest {
        repository.save(brew.copy(
            rating = 4,
            notes = "Bright",
            taste = BrewTaste.SOUR,
            tastingNotesFound = listOf("Chocolate"),
            ratedAt = 2_000,
            syncedAt = 3_000,
            revision = 7,
        ), emptyList())
        bagRepository.save(CoffeeBag("bag1", "Caparaó", weightGrams = 250.0, createdAt = 1, tastingNotes = listOf("Chocolate")))
        var saved = false
        var closed = false
        val vm = BrewRatingViewModel("b1", repository, bagRepository, onSaved = { saved = true })

        vm.save { closed = true }

        val unchanged = repository.get("b1")!!
        assertEquals(7, unchanged.revision)
        assertEquals(3_000L, unchanged.syncedAt)
        assertEquals(2_000L, unchanged.ratedAt)
        assertFalse(saved)
        assertTrue(closed)
    }

    @Test
    fun ratingAfterBagDeletionKeepsPreviouslyFoundNotes() = runTest {
        repository.save(brew.copy(tastingNotesFound = listOf("Chocolate", "Red fruit")), emptyList())
        val vm = BrewRatingViewModel("b1", repository, bagRepository)
        vm.setRating(5)

        vm.save {}

        assertEquals(listOf("Chocolate", "Red fruit"), repository.get("b1")!!.tastingNotesFound)
    }

    @Test
    fun ratingAfterBagNotesChangeKeepsFoundNotesOutsideTheCurrentBagList() = runTest {
        repository.save(brew.copy(tastingNotesFound = listOf("Chocolate")), emptyList())
        bagRepository.save(CoffeeBag("bag1", "Caparaó", weightGrams = 250.0, createdAt = 1, tastingNotes = listOf("Vanilla")))
        val vm = BrewRatingViewModel("b1", repository, bagRepository)
        vm.setRating(5)
        vm.toggleTastingNote("Vanilla")

        vm.save {}

        assertEquals(listOf("Chocolate", "Vanilla"), repository.get("b1")!!.tastingNotesFound)
    }
}
