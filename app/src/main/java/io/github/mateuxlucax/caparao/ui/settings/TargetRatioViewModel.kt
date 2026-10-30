package io.github.mateuxlucax.caparao.ui.settings

import androidx.lifecycle.ViewModel
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.local.VibrationTarget

/**
 * The Settings tab's brew preferences: the guide's alerts and the app's colors. Ratios live in the recipes and, for a
 * free brew, on the dose step.
 */
class TargetRatioViewModel(private val settings: SettingsStore) : ViewModel() {
    var guideVibration: Boolean
        get() = settings.guideVibration
        set(value) {
            settings.guideVibration = value
        }

    var guideVibrationOn: VibrationTarget
        get() = settings.guideVibrationOn
        set(value) {
            settings.guideVibrationOn = value
        }

    var guideSound: Boolean
        get() = settings.guideSound
        set(value) {
            settings.guideSound = value
        }

    var brandColors: Boolean
        get() = settings.brandColors
        set(value) {
            settings.brandColors = value
        }
}
