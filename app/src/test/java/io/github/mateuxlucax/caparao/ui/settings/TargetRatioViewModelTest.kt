package io.github.mateuxlucax.caparao.ui.settings

import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.ui.FakePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetRatioViewModelTest {
    private val settings = SettingsStore(FakePreferences())
    private val vm = TargetRatioViewModel(settings)

    @Test
    fun defaultsAreOneToTwoAndOneToSixteen() {
        assertEquals(2.0, vm.ratio(BrewMode.ESPRESSO), 0.001)
        assertEquals(16.0, vm.ratio(BrewMode.FILTER), 0.001)
    }

    @Test
    fun aValidRatioIsSavedPerModeWithACommaOrADot() {
        assertTrue(vm.save(BrewMode.ESPRESSO, "2,5"))
        assertTrue(vm.save(BrewMode.FILTER, "15.5"))
        assertEquals(2.5, settings.targetRatio(BrewMode.ESPRESSO), 0.001)
        assertEquals(15.5, settings.targetRatio(BrewMode.FILTER), 0.001)
    }

    @Test
    fun emptyOrOutOfRangeIsNotSaved() {
        assertFalse(vm.save(BrewMode.FILTER, ""))
        assertFalse(vm.save(BrewMode.FILTER, "0"))
        assertFalse(vm.save(BrewMode.FILTER, "99"))
        assertEquals(16.0, settings.targetRatio(BrewMode.FILTER), 0.001)
    }
}
