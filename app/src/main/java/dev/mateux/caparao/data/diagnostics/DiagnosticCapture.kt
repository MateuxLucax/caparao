package dev.mateux.caparao.data.diagnostics

import kotlinx.coroutines.flow.StateFlow

data class CaptureHistory(val pendingUploads: Int = 0, val lastSentAtEpochMillis: Long = 0L)

/**
 * The opt-in capture session Settings starts, stops and refreshes. Kept apart from [Diagnostics] so the event
 * seam carries no session lifecycle; [CaptureWriter] owns these file operations.
 */
interface DiagnosticCapture {
    val state: StateFlow<CaptureWriterState>
    val history: StateFlow<CaptureHistory>

    /** Start one session; false when one is already recording or storage cannot create the file. */
    suspend fun start(): Boolean

    suspend fun stop(): CaptureWriterState

    /** Re-read the retained sessions, including recovery after process death. */
    suspend fun refresh()

    /** Remove retained captures after consent is withdrawn. */
    suspend fun clear()
}
