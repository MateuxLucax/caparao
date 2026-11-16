package dev.mateux.caparao.ui.coffees

import dev.mateux.caparao.model.CoffeeBag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BagFormTest {
    @Test
    fun fieldSuggestionsIgnoreCaseAndAccentsUseNewestFirstAndStopAtThree() {
        val bags = listOf(
            CoffeeBag("old", "x", roaster = "Café antigo", weightGrams = 1.0, createdAt = 1),
            CoffeeBag("new", "x", roaster = "Café Aurora", finished = true, weightGrams = 1.0, createdAt = 5),
            CoffeeBag("middle", "x", roaster = "CAFÉ Alto", weightGrams = 1.0, createdAt = 4),
            CoffeeBag("third", "x", roaster = "cafe do vale", weightGrams = 1.0, createdAt = 3),
            CoffeeBag("fourth", "x", roaster = "Cafeteria", weightGrams = 1.0, createdAt = 2),
        )
        assertEquals(
            listOf("Café Aurora", "CAFÉ Alto", "cafe do vale"),
            coffeeFieldSuggestions(bags, "CAFE", null, CoffeeBag::roaster),
        )
        assertEquals(emptyList<String>(), coffeeFieldSuggestions(bags, "café aurora", null, CoffeeBag::roaster))
    }

    @Test
    fun tastingNotesUseNewestBagsBeforeDefaultsAndOmitDuplicatesAndSelectedNotes() {
        val bags = listOf(
            CoffeeBag("old", "x", weightGrams = 1.0, createdAt = 1, tastingNotes = listOf("Chocolate", "Laranja")),
            CoffeeBag("new", "x", finished = true, weightGrams = 1.0, createdAt = 5, tastingNotes = listOf("Jasmim", "chocolate")),
        )
        assertEquals(
            listOf("Jasmim", "chocolate", "Caramelo", "Cítrico"),
            coffeeNoteSuggestions(bags, listOf("Caramelo", "Jasmim", "Cítrico"), listOf("LARANJA"), null),
        )
    }

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

    @Test
    fun withNoteTrimsDeduplicatesAndCapsAtSix() {
        val form = BagForm(tastingNotes = listOf("Caramel", "Citrus"))
        assertEquals(listOf("Caramel", "Citrus", "Chocolate"), form.withNote("  Chocolate ").tastingNotes)
        assertTrue(form.withNote("caramel") === form) // case-insensitive duplicate
        assertTrue(form.withNote("   ") === form) // blank
        val full = BagForm(tastingNotes = listOf("a", "b", "c", "d", "e", "f"))
        assertTrue(full.withNote("g") === full) // already six
    }
}
