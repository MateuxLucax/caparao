package dev.mateux.caparao.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mateux.caparao.model.CoffeeBag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A bag's used grams, brew count, last brew and average rating come from the brews linked to it (#288). */
@RunWith(RobolectricTestRunner::class)
class CoffeeBagRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, BrewDatabase::class.java).allowMainThreadQueries().build()
    private val bags = CoffeeBagRepository(db.coffeeBags())

    @After
    fun tearDown() = db.close()

    @Test
    fun usedGramsCountLastBrewAndAverageRatingOnlyCountLinkedBrews() = runBlocking {
        bags.save(bag("bag", "Caparaó natural"))
        bags.save(bag("other", "Mantiqueira"))
        db.brews().insertWithSamples(brew("a", startedAt = 1_000, dose = 15.0, rating = 4, bagId = "bag"), emptyList())
        db.brews().insertWithSamples(brew("b", startedAt = 3_000, dose = 18.5, rating = 5, bagId = "bag"), emptyList())
        db.brews().insertWithSamples(brew("c", startedAt = 2_000, dose = 20.0, rating = null, bagId = "bag"), emptyList())
        db.brews().insertWithSamples(brew("unlinked", startedAt = 9_000, dose = 30.0, rating = 1, bagId = null), emptyList())

        val bag = bags.get("bag")!!
        assertEquals(53.5, bag.usedGrams, 0.0)
        assertEquals(3, bag.brewCount)
        assertEquals(3_000L, bag.lastBrewedAt)
        assertEquals(4.5, bag.averageRating!!, 0.0)
        assertEquals(196.5, bag.leftGrams, 0.0)

        val empty = bags.get("other")!!
        assertEquals(0.0, empty.usedGrams, 0.0)
        assertEquals(0, empty.brewCount)
        assertNull(empty.lastBrewedAt)
        assertNull(empty.averageRating)

        val listed = bags.bags.first().associateBy { it.id }
        assertEquals(bag, listed.getValue("bag"))
        assertEquals(empty, listed.getValue("other"))
    }

    @Test
    fun deletingABagUnlinksItsBrewsAndKeepsTheirCoffeeName() = runBlocking {
        bags.save(bag("bag", "Caparaó natural"))
        db.brews().insertWithSamples(brew("a", startedAt = 1_000, dose = 15.0, rating = 4, bagId = "bag", coffee = "Caparaó natural"), emptyList())

        bags.delete("bag")

        assertNull(bags.get("bag"))
        assertEquals(emptyList<CoffeeBag>(), bags.bags.first())
        val brew = db.brews().get("a")!!
        assertNull(brew.bagId)
        assertEquals("Caparaó natural", brew.coffee)
        assertEquals(15.0, brew.doseGrams, 0.0)
    }

    private fun bag(id: String, name: String) = CoffeeBag(id, name, weightGrams = 250.0, createdAt = 0)

    private fun brew(id: String, startedAt: Long, dose: Double, rating: Int?, bagId: String?, coffee: String? = null) = BrewEntity(
        id = id,
        mode = "FILTER",
        startedAt = startedAt,
        durationMs = 60_000,
        doseGrams = dose,
        yieldGrams = 250.0,
        timeToFirstDropMs = null,
        flowAvg = 1.0,
        flowPeak = 2.0,
        interrupted = false,
        syncedAt = null,
        coffee = coffee,
        rating = rating,
        bagId = bagId,
    )
}
