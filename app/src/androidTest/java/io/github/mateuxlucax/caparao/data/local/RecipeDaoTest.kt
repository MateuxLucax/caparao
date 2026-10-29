package io.github.mateuxlucax.caparao.data.local

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recipes and their steps on SQLite, in memory, through the repository. */
class RecipeDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, BrewDatabase::class.java).build()
    private val repository = RecipeRepository(db.recipes())

    private val filter = Recipe(
        id = "f", name = "Kalita", mode = BrewMode.FILTER, method = "Kalita", doseGrams = 15.0, targetRatio = 16.0,
        grind = "22", waterTempCelsius = 94, notes = "Slow last pour",
        steps = listOf(
            RecipeStep(StepKind.BLOOM, 45.0, 0),
            RecipeStep(StepKind.WAIT, null, 30_000),
            RecipeStep(StepKind.POUR, 240.0, 45_000),
        ),
        createdAt = 1, updatedAt = 1,
    )

    @After
    fun tearDown() = db.close()

    @Test
    fun savesAndReadsBackWithStepsInOrder() = runBlocking {
        repository.save(filter)
        assertEquals(filter, repository.get("f"))
        assertEquals(listOf(filter), repository.recipes.first())
    }

    @Test
    fun savingAgainReplacesTheSteps() = runBlocking {
        repository.save(filter)
        val edited = filter.copy(name = "Kalita 2", steps = listOf(RecipeStep(StepKind.POUR, 240.0, 0)), updatedAt = 2)
        repository.save(edited)
        assertEquals(edited, repository.get("f"))
    }

    @Test
    fun listsByModeThenName() = runBlocking {
        repository.save(filter.copy(id = "b", name = "b"))
        repository.save(filter.copy(id = "a", name = "A"))
        repository.save(filter.copy(id = "e", name = "z", mode = BrewMode.ESPRESSO, steps = emptyList()))
        assertEquals(listOf("e", "a", "b"), repository.recipes.first().map { it.id })
    }

    @Test
    fun deleteCascadesToStepsAndKeepsBrews() = runBlocking {
        repository.save(filter)
        db.brews().insert(BrewEntity("x", "FILTER", 1, 180_000, 15.0, 240.0, null, 2.0, 5.0, false, null, recipeId = "f", recipeName = "Kalita"))
        repository.delete("f")
        assertNull(repository.get("f"))
        assertTrue(db.recipes().steps("f").isEmpty())
        assertEquals("Kalita", BrewRepository(db.brews()).get("x")!!.recipeName)
    }
}
