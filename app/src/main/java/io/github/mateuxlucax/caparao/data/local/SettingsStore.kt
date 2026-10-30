package io.github.mateuxlucax.caparao.data.local

import android.content.Context
import android.content.SharedPreferences
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.SyncError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** What the Brews tab shows about uploads: whether a server is set, the last upload and the last error. */
data class SyncStatus(
    val serverConfigured: Boolean,
    /** Epoch ms of the last brew the server confirmed; 0 if none yet. */
    val lastSyncMs: Long,
    val lastError: SyncError?,
)

/**
 * Small persisted settings in the `settings` SharedPreferences file. File name and keys must never change. The token
 * is a secret: never log it or put it in an error.
 */
class SettingsStore(private val prefs: SharedPreferences) {
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    /**
     * The recipe chosen last time in [mode], preselected on the next brew (docs/adr/0018-recipes.md): null when none was
     * ever chosen, [FREE_BREW] when the last brew had no recipe.
     */
    fun lastRecipeId(mode: BrewMode): String? = prefs.getString(KEY_LAST_RECIPE + mode.name.lowercase(), null)

    fun setLastRecipeId(mode: BrewMode, id: String) = prefs.edit().putString(KEY_LAST_RECIPE + mode.name.lowercase(), id).apply()

    /** The dose of the last free brew (no recipe) in [mode], to start the next one from. */
    fun lastDose(mode: BrewMode): Double? =
        prefs.getFloat(KEY_LAST_DOSE + mode.name.lowercase(), -1f).takeIf { it > 0 }?.toDouble()

    fun setLastDose(mode: BrewMode, grams: Double) =
        prefs.edit().putFloat(KEY_LAST_DOSE + mode.name.lowercase(), grams.toFloat()).apply()

    /** The X of the 1:X ratio a free brew (no recipe) in [mode] aims for; a recipe carries its own. */
    fun targetRatio(mode: BrewMode): Double =
        prefs.getFloat(KEY_TARGET_RATIO + mode.name.lowercase(), -1f).takeIf { it > 0 }?.toDouble() ?: defaultTargetRatio(mode)

    fun setTargetRatio(mode: BrewMode, ratio: Double) =
        prefs.edit().putFloat(KEY_TARGET_RATIO + mode.name.lowercase(), ratio.toFloat()).apply()

    /** Guided brew alerts (docs/adr/0019-guided-brew.md): vibration on by default, sound off. */
    var guideVibration: Boolean
        get() = prefs.getBoolean(KEY_GUIDE_VIBRATION, true)
        set(value) = prefs.edit().putBoolean(KEY_GUIDE_VIBRATION, value).apply()

    /** Where the guide vibrates when [guideVibration] is on: the phone, a paired watch, or both (the default). */
    var guideVibrationOn: VibrationTarget
        get() = prefs.getString(KEY_GUIDE_VIBRATION_ON, null)?.let { name -> VibrationTarget.entries.find { it.name == name } }
            ?: VibrationTarget.BOTH
        set(value) = prefs.edit().putString(KEY_GUIDE_VIBRATION_ON, value.name).apply()

    var guideSound: Boolean
        get() = prefs.getBoolean(KEY_GUIDE_SOUND, false)
        set(value) = prefs.edit().putBoolean(KEY_GUIDE_SOUND, value).apply()

    /** Caparaó's own colors instead of the system's Material You (docs/adr/0024-brand-color-option.md); off by default. */
    var brandColors: Boolean
        get() = prefs.getBoolean(KEY_BRAND_COLORS, false)
        set(value) = prefs.edit().putBoolean(KEY_BRAND_COLORS, value).apply()

    /** [brandColors] now and after every change, so the theme switches at once. */
    val brandColorsChanges: Flow<Boolean>
        get() = prefs.changes(setOf(KEY_BRAND_COLORS)).map { brandColors }.distinctUntilChanged()

    /** The upload server's base URL, empty when none is set. */
    val serverUrl: String get() = prefs.getString(KEY_SERVER_URL, "").orEmpty()

    val token: String get() = prefs.getString(KEY_TOKEN, "").orEmpty()

    /** True when uploads are on: a URL and a token are saved. */
    val serverConfigured: Boolean get() = serverUrl.isNotBlank() && token.isNotBlank()

    /** Saves the server (trimmed) and forgets the last error, which was about the previous one. */
    fun saveServer(url: String, token: String) = prefs.edit()
        .putString(KEY_SERVER_URL, url.trim())
        .putString(KEY_TOKEN, token.trim())
        .putError(null, null)
        .apply()

    /** Turns uploads off; brews already sent stay marked synced. */
    fun removeServer() = prefs.edit().remove(KEY_SERVER_URL).remove(KEY_TOKEN).putError(null, null).apply()

    var lastSyncMs: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    /** The last upload error, null once cleared (sync/UploadRunner decides when). */
    val lastSyncError: SyncError?
        get() {
            val name = prefs.getString(KEY_LAST_ERROR, null) ?: return null
            val kind = SyncError.Kind.entries.find { it.name == name } ?: return null
            return SyncError(kind, prefs.getString(KEY_LAST_ERROR_DETAIL, null))
        }

    /** The brew whose upload set [lastSyncError]. */
    val lastSyncErrorBrewId: String? get() = prefs.getString(KEY_LAST_ERROR_BREW, null)

    fun setLastSyncError(error: SyncError?, brewId: String?) = prefs.edit().putError(error, brewId).apply()

    fun currentSyncStatus() = SyncStatus(serverConfigured, lastSyncMs, lastSyncError)

    /** [currentSyncStatus] now and after every change to it. */
    val syncStatus: Flow<SyncStatus>
        get() = prefs.changes(SYNC_KEYS).map { currentSyncStatus() }.distinctUntilChanged()

    private fun SharedPreferences.Editor.putError(error: SyncError?, brewId: String?) = this
        .putString(KEY_LAST_ERROR, error?.kind?.name)
        .putString(KEY_LAST_ERROR_DETAIL, error?.detail)
        .putString(KEY_LAST_ERROR_BREW, error?.let { brewId })

    companion object {
        private const val FILE = "settings"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_LAST_DOSE = "last_dose_"
        private const val KEY_LAST_RECIPE = "last_recipe_"
        private const val KEY_GUIDE_VIBRATION = "guide_vibration"
        private const val KEY_GUIDE_VIBRATION_ON = "guide_vibration_on"
        private const val KEY_GUIDE_SOUND = "guide_sound"
        private const val KEY_BRAND_COLORS = "brand_colors"
        private const val KEY_TARGET_RATIO = "target_ratio_"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TOKEN = "server_token"
        private const val KEY_LAST_SYNC = "last_sync_ms"
        private const val KEY_LAST_ERROR = "last_sync_error"
        private const val KEY_LAST_ERROR_DETAIL = "last_sync_error_detail"
        private const val KEY_LAST_ERROR_BREW = "last_sync_error_brew"

        private val SYNC_KEYS = setOf(KEY_SERVER_URL, KEY_TOKEN, KEY_LAST_SYNC, KEY_LAST_ERROR, KEY_LAST_ERROR_DETAIL)

        /** [lastRecipeId] after a brew without a recipe. */
        const val FREE_BREW = ""

        /** 1:2 for espresso, 1:16 for filter: common starting points at the bar. */
        fun defaultTargetRatio(mode: BrewMode): Double = when (mode) {
            BrewMode.ESPRESSO -> 2.0
            BrewMode.FILTER -> 16.0
        }

        fun open(context: Context): SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }
}

/** Where the guided brew vibrates (docs/adr/0023-watch-companion.md). */
enum class VibrationTarget(val phone: Boolean, val watch: Boolean) {
    PHONE(phone = true, watch = false),
    WATCH(phone = false, watch = true),
    BOTH(phone = true, watch = true),
}
