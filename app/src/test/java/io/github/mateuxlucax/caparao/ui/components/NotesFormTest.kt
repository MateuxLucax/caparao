package io.github.mateuxlucax.caparao.ui.components

import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.CoffeeBag
import io.github.mateuxlucax.caparao.ui.components.NotesForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesFormTest {
    private val brew = Brew("b", BrewMode.FILTER, 1, 180_000, 15.0, 250.0, null, 1.4, 5.0, false, grind = "22", waterTempCelsius = 94)

    @Test
    fun theRecipeFieldsArePrefilledWithTheCoffeeAndItsBagAsSaved() {
        assertEquals(NotesForm(grind = "22", temp = "94"), NotesForm.of(brew))
        assertEquals(NotesForm(coffee = "Caparaó", bagId = "bag-1", grind = "22", temp = "94"), NotesForm.of(brew.copy(coffee = "Caparaó", bagId = "bag-1")))
    }

    @Test
    fun typingABagsNameLinksItAndAnyOtherTextUnlinksIt() {
        val bags = listOf(CoffeeBag("bag-1", "Caparaó", weightGrams = 250.0, createdAt = 0))
        val linked = NotesForm().withCoffee("Caparaó", bags)
        assertEquals("bag-1", linked.bagId)
        assertNull(linked.withCoffee("Caparaó x", bags).bagId)
        assertNull(NotesForm(coffee = "Caparaó", bagId = "bag-1").withCoffee("", bags).bagId)
    }

    @Test
    fun aPickedBagSetsItsNameAndLink() {
        val picked = NotesForm(coffee = "Old").withBag(CoffeeBag("bag-2", "Mantiqueira", weightGrams = 500.0, createdAt = 0))
        assertEquals(NotesForm(coffee = "Mantiqueira", bagId = "bag-2"), picked)
    }

    @Test
    fun aBlankCoffeeSavesNoBagLink() {
        assertNull(NotesForm(coffee = " ", bagId = "bag-1").applyTo(brew).bagId)
        assertEquals("bag-1", NotesForm(coffee = "Caparaó", bagId = "bag-1").applyTo(brew).bagId)
    }

    @Test
    fun blanksBecomeNullAndTextIsTrimmed() {
        val applied = NotesForm(coffee = "  ", grind = " 18 ", temp = "", rating = 3, notes = " Bright ").applyTo(brew)
        assertNull(applied.coffee)
        assertEquals("18", applied.grind)
        assertNull(applied.waterTempCelsius)
        assertEquals(3, applied.rating)
        assertEquals("Bright", applied.notes)
    }

    @Test
    fun theTemperatureMustBeEmptyOrPlausible() {
        assertTrue(NotesForm(temp = "").valid)
        assertTrue(NotesForm(temp = "94").valid)
        assertFalse(NotesForm(temp = "0").valid)
        assertFalse(NotesForm(temp = "101").valid)
    }
}
