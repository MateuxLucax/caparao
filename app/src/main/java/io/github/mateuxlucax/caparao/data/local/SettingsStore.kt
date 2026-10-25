package io.github.mateuxlucax.caparao.data.local

import android.content.Context
import android.content.SharedPreferences

/** Small persisted settings in the `settings` SharedPreferences file. File name and keys must never change. */
class SettingsStore(private val prefs: SharedPreferences) {
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    companion object {
        private const val FILE = "settings"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"

        fun open(context: Context): SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }
}
