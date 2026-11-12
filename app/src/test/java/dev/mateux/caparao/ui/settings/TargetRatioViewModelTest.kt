package dev.mateux.caparao.ui.settings

import dev.mateux.caparao.data.local.SettingsStore
import dev.mateux.caparao.data.local.VibrationTarget
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.ui.FakePreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class TargetRatioViewModelTest {
    private val settingsPrefs = FakePreferences()
    private val settings = SettingsStore(settingsPrefs)
    private val vm = TargetRatioViewModel(settings)

    @Test
    fun guideVibratesOnBothByDefaultAndRemembersTheChoice() {
        assertEquals(VibrationTarget.BOTH, vm.guideVibrationOn)
        vm.guideVibrationOn = VibrationTarget.WATCH
        assertEquals(VibrationTarget.WATCH, SettingsStore(settingsPrefs).guideVibrationOn)
    }

    @Test
    fun frenchPressFreeBrewDefaultsToItsRecipeRatioAndDose() {
        assertEquals(15.0, SettingsStore.defaultTargetRatio(BrewMode.FRENCH_PRESS), 0.0)
        assertEquals(20.0, SettingsStore.defaultDose(BrewMode.FRENCH_PRESS), 0.0)
    }
}
