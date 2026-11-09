package io.github.mateuxlucax.caparao.sync

import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticErrorContext
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticEvent
import io.github.mateuxlucax.caparao.data.diagnostics.Diagnostics
import io.github.mateuxlucax.caparao.data.diagnostics.NoDiagnostics
import io.github.mateuxlucax.caparao.data.exporter.ExporterClient
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.model.SetupException
import io.github.mateuxlucax.caparao.model.SyncError
import kotlinx.coroutines.CancellationException

/** How an upload job ended; the worker turns it into a WorkManager result. */
enum class UploadOutcome { SUCCESS, RETRY, FAILURE }

/**
 * Uploads one brew for the worker and records the outcome in [settings]: the brew is marked synced only after a 2xx
 * (#91); errors are kept for the Brews tab and retried only when that can help.
 */
class UploadRunner(
    private val repository: BrewRepository,
    private val settings: SettingsStore,
    private val exporter: ExporterClient,
    /** Whether Android 17 local network protection would block connecting to a URL. */
    private val localNetworkBlocked: suspend (String) -> Boolean = { false },
    /** Upload outcomes go to the capture without the server's response body, which may carry the host (#330). */
    private val diagnostics: Diagnostics = NoDiagnostics,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(brewId: String): UploadOutcome {
        // Removed meanwhile: nothing to do until a server is saved again (UploadScheduler.resendUnsynced).
        if (!settings.serverConfigured) return UploadOutcome.FAILURE
        // Discarded or deleted after it was enqueued.
        val brew = repository.get(brewId) ?: return UploadOutcome.SUCCESS
        if (brew.syncedAt != null) return UploadOutcome.SUCCESS
        val url = settings.serverUrl
        val error = try {
            if (localNetworkBlocked(url)) throw SetupException(SyncError.Kind.LOCAL_NETWORK_PERMISSION)
            exporter.upload(url, settings.token, brew, repository.samples(brewId))
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The event carries the stable kind; the exception is reported with the response body scrubbed away.
            diagnostics.error(e, DiagnosticErrorContext.UPLOAD)
            SyncErrors.of(e) { localNetworkBlocked(url) }
        }
        if (error == null) {
            val at = now()
            repository.markSynced(brewId, at)
            settings.lastSyncMs = at
            if (clearsLastError(brewId)) settings.setLastSyncError(null, null)
            diagnostics.event(DiagnosticEvent.UploadSucceeded)
            return UploadOutcome.SUCCESS
        }
        settings.setLastSyncError(error, brewId)
        diagnostics.event(DiagnosticEvent.UploadFailed(error.kind))
        if (error.kind.retry) {
            diagnostics.event(DiagnosticEvent.UploadRetried)
            return UploadOutcome.RETRY
        }
        return UploadOutcome.FAILURE
    }

    /**
     * A success shows the server works, which clears any error except a rejected body of another brew: that one stays
     * until that brew goes through or the server is saved again, so the brew is not left unsent without a word.
     */
    private fun clearsLastError(brewId: String): Boolean {
        val last = settings.lastSyncError ?: return false
        return last.kind != SyncError.Kind.REJECTED || settings.lastSyncErrorBrewId == brewId
    }
}
