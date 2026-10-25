package io.github.mateuxlucax.caparao.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import java.util.concurrent.TimeUnit

/** Where upload jobs go: WorkManager in the app ([WorkManagerUploadQueue]), a list in tests. */
interface UploadQueue {
    /** One job per brew; [replace] restarts a job that failed or waits in backoff, otherwise an existing one is kept. */
    fun enqueue(brewId: String, replace: Boolean)

    fun cancel(brewId: String)

    fun cancelAll()
}

/**
 * Decides when brews are uploaded (docs/adr/0016-upload-jobs.md): after each save, and all unsynced ones again when a
 * server is saved. Nothing is enqueued without a server (docs/adr/0003-offline-first.md).
 */
class UploadScheduler(
    private val settings: SettingsStore,
    private val queue: UploadQueue,
    /** Ids of the brews not confirmed by the server (BrewRepository.unsyncedIds). */
    private val unsyncedIds: suspend () -> List<String>,
) {
    /** A brew was saved; call it after the insert finished, so the job always finds the brew. */
    fun brewSaved(brewId: String) {
        if (settings.serverConfigured) queue.enqueue(brewId, replace = false)
    }

    fun brewDeleted(brewId: String) = queue.cancel(brewId)

    /** A server was saved or changed, or a problem was fixed: every unsynced brew is sent (again). */
    suspend fun resendUnsynced() {
        if (!settings.serverConfigured) return
        unsyncedIds().forEach { queue.enqueue(it, replace = true) }
    }

    fun serverRemoved() = queue.cancelAll()
}

/**
 * [UploadQueue] on WorkManager: unique work `upload-<brew id>`, tagged [TAG], network constraint, exponential backoff.
 * [worker] is the app's UploadWorker, passed in because its class name is persisted by WorkManager and stays in the
 * root package.
 */
class WorkManagerUploadQueue(context: Context, private val worker: Class<out ListenableWorker>) : UploadQueue {
    private val context = context.applicationContext

    override fun enqueue(brewId: String, replace: Boolean) {
        val request = OneTimeWorkRequest.Builder(worker)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setInputData(Data.Builder().putString(KEY_BREW_ID, brewId).build())
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(brewId),
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancel(brewId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(brewId))
    }

    override fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    companion object {
        // Work names, the tag and the input key are stored by WorkManager on users' phones: never change them.
        const val TAG = "upload"
        private const val KEY_BREW_ID = "brew_id"
        private const val BACKOFF_SECONDS = 30L

        fun workName(brewId: String) = "upload-$brewId"

        /** The brew a job uploads, from its input. */
        fun brewId(input: Data): String? = input.getString(KEY_BREW_ID)
    }
}
