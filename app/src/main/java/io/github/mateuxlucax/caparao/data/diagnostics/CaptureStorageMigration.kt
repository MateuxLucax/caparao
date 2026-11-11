package io.github.mateuxlucax.caparao.data.diagnostics

import android.content.SharedPreferences
import java.io.File

/** Deletes local-only sessions written before captures became uploadable. Call on the I/O dispatcher. */
internal fun removeLegacyDiagnosticCaptures(filesDir: File, migrations: SharedPreferences): Boolean {
    if (migrations.getBoolean(LEGACY_CAPTURES_REMOVED, false)) return true
    val legacy = File(filesDir, "diagnostics")
    if (legacy.exists() && !legacy.deleteRecursively()) return false
    return migrations.edit().putBoolean(LEGACY_CAPTURES_REMOVED, true).commit()
}

private const val LEGACY_CAPTURES_REMOVED = "legacy_diagnostic_captures_removed"
