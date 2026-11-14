package dev.mateux.caparao.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mateux.caparao.model.BagRecipeAdjustment
import dev.mateux.caparao.model.BagRecipeAdjustmentReason
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.CoffeeBag
import dev.mateux.caparao.model.Recipe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BagRecipeAdjustmentRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, BrewDatabase::class.java).allowMainThreadQueries().build()
    private val adjustments = BagRecipeAdjustmentRepository(db.bagRecipeAdjustments())
    private val bags = CoffeeBagRepository(db.coffeeBags())
    private val recipes = RecipeRepository(db.recipes())

    @After
    fun tearDown() = db.close()

    @Test
    fun savesAdjustmentAndBeforeAfterHistoryForManualAndSuggestedChanges() = runBlocking {
        bags.save(CoffeeBag("bag", "Caparaó", weightGrams = 250.0, createdAt = 1))
        recipes.save(recipe())
        val first = BagRecipeAdjustment("bag", "recipe", grind = 18, doseGrams = 15.0)
        adjustments.save(first, changedAt = 100)
        adjustments.save(
            first.copy(grind = 19, waterTempCelsius = 92, tuned = true),
            changedAt = 200,
            reason = BagRecipeAdjustmentReason.SUGGESTION,
            brewId = "brew-1",
        )

        assertEquals(first.copy(grind = 19, waterTempCelsius = 92, tuned = true), adjustments.get("bag", "recipe"))
        val history = adjustments.history("bag", "recipe").first()
        assertEquals(2, history.size)
        assertEquals(100L, history[1].changedAt)
        assertNull(history[1].oldGrind)
        assertEquals(18, history[1].newGrind)
        assertEquals(BagRecipeAdjustmentReason.MANUAL, history[1].reason)
        assertEquals(18, history[0].oldGrind)
        assertEquals(19, history[0].newGrind)
        assertEquals(92, history[0].newWaterTempCelsius)
        assertEquals(15.0, history[0].oldDoseGrams!!, 0.0)
        assertEquals("brew-1", history[0].brewId)
        assertEquals(BagRecipeAdjustmentReason.SUGGESTION, history[0].reason)
    }

    @Test
    fun unchangedAdjustmentDoesNotCreateAnotherHistoryRow() = runBlocking {
        bags.save(CoffeeBag("bag", "Caparaó", weightGrams = 250.0, createdAt = 1))
        recipes.save(recipe())
        val adjustment = BagRecipeAdjustment("bag", "recipe", grind = 18)
        adjustments.save(adjustment, changedAt = 100)
        adjustments.save(adjustment, changedAt = 200)

        assertEquals(1, adjustments.history("bag", "recipe").first().size)
    }

    @Test
    fun deletingBagOrRecipeCascadesThroughAdjustmentHistory() = runBlocking {
        bags.save(CoffeeBag("bag", "Caparaó", weightGrams = 250.0, createdAt = 1))
        bags.save(CoffeeBag("other-bag", "Mantiqueira", weightGrams = 250.0, createdAt = 1))
        recipes.save(recipe())
        recipes.save(recipe("other-recipe"))
        adjustments.save(BagRecipeAdjustment("bag", "recipe", grind = 18), changedAt = 100)
        adjustments.save(BagRecipeAdjustment("other-bag", "other-recipe", grind = 20), changedAt = 100)

        bags.delete("bag")

        assertNull(adjustments.get("bag", "recipe"))
        assertEquals(emptyList<Any>(), adjustments.history("bag", "recipe").first())
        assertEquals(20, adjustments.get("other-bag", "other-recipe")!!.grind)

        recipes.delete("other-recipe")

        assertNull(adjustments.get("other-bag", "other-recipe"))
        assertEquals(emptyList<Any>(), adjustments.history("other-bag", "other-recipe").first())
    }

    private fun recipe(id: String = "recipe") = Recipe(
        id = id,
        name = "V60",
        mode = BrewMode.FILTER,
        method = "V60",
        doseGrams = 15.0,
        targetRatio = 16.0,
        createdAt = 1,
        updatedAt = 1,
    )
}
