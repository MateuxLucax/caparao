package io.github.mateuxlucax.caparao.ui.coffees

import io.github.mateuxlucax.caparao.model.CoffeeBag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BagFormTest {
    @Test
    fun needsANameAndAWeight() {
        assertFalse(BagForm(weight = "250").valid)
        assertFalse(BagForm(name = "Caparaó natural", weight = "0").valid)
        assertTrue(BagForm(name = "Caparaó natural", weight = "250,5").valid)
    }

    @Test
    fun roundTripsABag() {
        val bag = CoffeeBag("b", "Caparaó natural", roaster = "Torra", roastedAt = 0, weightGrams = 250.0, createdAt = 5)
        assertEquals(bag, BagForm.of(bag).toModel(now = 99) { "new" })
        val fresh = BagForm(name = " Bourbon ", weight = "500", roaster = " ").toModel(now = 7) { "new" }
        assertEquals("new", fresh.id)
        assertEquals("Bourbon", fresh.name)
        assertNull(fresh.roaster)
        assertEquals(7L, fresh.createdAt)
    }

    @Test
    fun leftAndDaysSinceRoast() {
        val bag = CoffeeBag("b", "x", roastedAt = 0, weightGrams = 250.0, createdAt = 0, usedGrams = 270.0)
        assertEquals(0.0, bag.leftGrams, 0.0)
        assertEquals(3L, bag.daysSinceRoast(3 * 86_400_000L + 5))
        assertNull(bag.copy(roastedAt = null).daysSinceRoast(0))
    }
}
