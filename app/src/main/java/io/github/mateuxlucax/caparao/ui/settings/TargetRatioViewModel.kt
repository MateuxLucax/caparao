package io.github.mateuxlucax.caparao.ui.settings

import androidx.lifecycle.ViewModel
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.model.BrewMode

/**
 * The Settings tab's brew preferences: the 1:X a free brew aims for in each mode, which the live brew shows as progress,
 * and the guide's alerts.
 */
class TargetRatioViewModel(private val settings: SettingsStore) : ViewModel() {
    var guideVibration: Boolean
        get() = settings.guideVibration
        set(value) {
            settings.guideVibration = value
        }

    var guideSound: Boolean
        get() = settings.guideSound
        set(value) {
            settings.guideSound = value
        }

    fun ratio(mode: BrewMode): Double = settings.targetRatio(mode)

    /** Saves [text] (a comma or a dot) when it is a ratio a brew could aim for; false otherwise. */
    fun save(mode: BrewMode, text: String): Boolean {
        val ratio = parse(text) ?: return false
        settings.setTargetRatio(mode, ratio)
        return true
    }

    companion object {
        /** From a ristretto to a very long filter. */
        val RANGE = 0.5..30.0

        fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in RANGE }
    }
}
