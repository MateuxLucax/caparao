package io.github.mateuxlucax.caparao.data.diagnostics

import android.content.Context
import io.github.mateuxlucax.caparao.data.export.ExportedFile
import kotlinx.coroutines.flow.StateFlow

/**
 * The opt-in capture session Settings starts, stops, refreshes and shares. Kept apart from [Diagnostics] so the event
 * seam carries no session lifecycle; [DiagnosticRecorder] is its only implementation until #336 removes it.
 */
interface DiagnosticCapture {
    val state: StateFlow<DiagnosticRecorderState>

    /** Start one session; false when one is already recording or storage cannot create the file. */
    suspend fun start(): Boolean

    suspend fun stop(): DiagnosticRecorderState

    /** Re-read the retained sessions, including recovery after process death. */
    suspend fun refresh()

    /** A share handle for the newest retained session, or null when there is none. */
    fun shareLatest(context: Context): ExportedFile?
}
