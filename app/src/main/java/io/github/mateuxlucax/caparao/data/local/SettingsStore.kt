package io.github.mateuxlucax.caparao.data.local

import android.content.Context
import android.content.SharedPreferences
import io.github.mateuxlucax.caparao.model.BrewMode

/** Small persisted settings in the `settings` SharedPreferences file. File name and keys must never change. */
class SettingsStore(private val prefs: SharedPreferences) {
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    /** The dose used last time in [mode], to start the next brew from. */
    fun lastDose(mode: BrewMode): Double? =
        prefs.getFloat(KEY_LAST_DOSE + mode.name.lowercase(), -1f).takeIf { it > 0 }?.toDouble()

    fun setLastDose(mode: BrewMode, grams: Double) =
        prefs.edit().putFloat(KEY_LAST_DOSE + mode.name.lowercase(), grams.toFloat()).apply()

    companion object {
        private const val FILE = "settings"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_LAST_DOSE = "last_dose_"

        fun open(context: Context): SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }
}
