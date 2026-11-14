package dev.mateux.caparao.ui.settings

import androidx.lifecycle.ViewModel
import dev.mateux.caparao.data.local.SettingsStore
import dev.mateux.caparao.data.local.VibrationTarget

/**
 * The Settings tab's brew preferences: the guide's alerts. Ratios live in the recipes and, for a
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

    var ratingReminderDelayMinutes: Int
        get() = settings.ratingReminderDelayMinutes
        set(value) { settings.ratingReminderDelayMinutes = value }
}
