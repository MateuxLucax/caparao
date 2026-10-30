package io.github.mateuxlucax.caparao.ui.brews

import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesFormTest {
    private val brew = Brew("b", BrewMode.FILTER, 1, 180_000, 15.0, 250.0, null, 1.4, 5.0, false, grind = "22", waterTempCelsius = 94)

    @Test
    fun theRecipeFieldsArePrefilledAndTheLastCoffeeSuggested() {
        assertEquals(NotesForm(coffee = "Caparaó", grind = "22", temp = "94"), NotesForm.of(brew, "Caparaó"))
        assertEquals("Own", NotesForm.of(brew.copy(coffee = "Own"), "Caparaó").coffee)
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
