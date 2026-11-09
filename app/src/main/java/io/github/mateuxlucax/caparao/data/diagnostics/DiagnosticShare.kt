package io.github.mateuxlucax.caparao.data.diagnostics

import android.content.Context
import androidx.core.content.FileProvider
import io.github.mateuxlucax.caparao.data.export.ExportedFile

/** Hands the newest retained diagnostic session to the share sheet through the app's FileProvider (#328). */
class DiagnosticShare(private val context: Context, private val recorder: DiagnosticRecorder) {
    private val authority = "${context.packageName}.fileprovider"

    fun latest(): ExportedFile? =
        recorder.shareLatest(authority) { file -> FileProvider.getUriForFile(context, authority, file) }
}
