package io.github.mateuxlucax.caparao

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.mateuxlucax.caparao.sync.UploadOutcome
import io.github.mateuxlucax.caparao.sync.WorkManagerUploadQueue

/**
 * Uploads one brew (see [WorkManagerUploadQueue]); the logic is in sync/UploadRunner.
 *
 * Do not move or rename this class: WorkManager stores its fully qualified name with every enqueued job and creates it
 * by reflection, so jobs already waiting on users' phones would fail.
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
