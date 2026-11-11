package io.github.mateuxlucax.caparao.data.diagnostics

import kotlinx.coroutines.flow.StateFlow

/**
 * The opt-in capture session Settings starts, stops and refreshes. Kept apart from [Diagnostics] so the event
 * seam carries no session lifecycle; [DiagnosticRecorder] is its only implementation until #336 removes it.
 */
interface DiagnosticCapture {
    val state: StateFlow<DiagnosticRecorderState>

    /** Start one session; false when one is already recording or storage cannot create the file. */
    suspend fun start(): Boolean

    suspend fun stop(): DiagnosticRecorderState

    /** Re-read the retained sessions, including recovery after process death. */
    suspend fun refresh()

    /** Remove retained captures after consent is withdrawn. */
    suspend fun clear()
}
