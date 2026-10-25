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

    /** The dose used last time in [mode], to start the next brew from. */
    fun lastDose(mode: BrewMode): Double? =
        prefs.getFloat(KEY_LAST_DOSE + mode.name.lowercase(), -1f).takeIf { it > 0 }?.toDouble()

    fun setLastDose(mode: BrewMode, grams: Double) =
        prefs.edit().putFloat(KEY_LAST_DOSE + mode.name.lowercase(), grams.toFloat()).apply()

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
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TOKEN = "server_token"
        private const val KEY_LAST_SYNC = "last_sync_ms"
        private const val KEY_LAST_ERROR = "last_sync_error"
        private const val KEY_LAST_ERROR_DETAIL = "last_sync_error_detail"
        private const val KEY_LAST_ERROR_BREW = "last_sync_error_brew"

        private val SYNC_KEYS = setOf(KEY_SERVER_URL, KEY_TOKEN, KEY_LAST_SYNC, KEY_LAST_ERROR, KEY_LAST_ERROR_DETAIL)

        fun open(context: Context): SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }
}
