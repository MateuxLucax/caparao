package io.github.mateuxlucax.caparao.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.mateuxlucax.caparao.CaparaoApplication

/**
 * Uploads one brew (see [WorkManagerUploadQueue]); the logic is in sync/UploadRunner.
 *
 * Its name is public: WorkManager stores the fully qualified name with every enqueued job and creates it by
 * reflection. It moved here in the compatibility reset (#396); the old jobs are cancelled once and rescheduled.
 * From the first Play upload on, do not move or rename it.
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val brewId = WorkManagerUploadQueue.brewId(inputData) ?: return Result.failure()
        val container = (applicationContext as CaparaoApplication).container
        return when (container.uploadRunner.run(brewId)) {
            UploadOutcome.SUCCESS -> Result.success()
            UploadOutcome.RETRY -> Result.retry()
            UploadOutcome.FAILURE -> Result.failure()
        }
    }
}
