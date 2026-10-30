package io.github.mateuxlucax.caparao.ui.settings

import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.local.VibrationTarget
import io.github.mateuxlucax.caparao.ui.FakePreferences
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
}
