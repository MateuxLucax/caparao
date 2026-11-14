package dev.mateux.caparao.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.mateux.caparao.CaparaoApplication
import dev.mateux.caparao.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** One pending local rating reminder at a time; the newest saved brew replaces the older one. */
class RatingReminderScheduler(context: Context) {
    private val applicationContext = context.applicationContext
    private val workManager by lazy { WorkManager.getInstance(applicationContext) }

    fun update(brewId: String, enabled: Boolean, delayMinutes: Int) {
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = OneTimeWorkRequestBuilder<RatingReminderWorker>()
            .setInitialDelay(delayMinutes.coerceIn(5, 15).toLong(), TimeUnit.MINUTES)
            .setInputData(Data.Builder().putString(INPUT_BREW_ID, brewId).build())
            .addTag(WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val WORK_NAME = "rating-reminder"
        const val WORK_TAG = "rating-reminder"
        const val INPUT_BREW_ID = "rating_reminder_brew_id"
        const val CHANNEL_ID = "rating-reminders"
        const val NOTIFICATION_ID = 465
    }
}

/** Rechecks the phone's source of truth immediately before showing the notification. */
class RatingReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val brewId = inputData.getString(RatingReminderScheduler.INPUT_BREW_ID) ?: return Result.failure()
        val container = (applicationContext as CaparaoApplication).container
        val brew = container.brews.notRatedYet().first().firstOrNull { it.id == brewId } ?: return Result.success()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val context = applicationContext
        createChannel(context)
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val time = DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(brew.startedAt))
        val details = listOfNotNull(brew.coffee, brew.recipeName, time).joinToString(" · ")
        val rateIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_RATE_BREW
            putExtra(MainActivity.EXTRA_RATING_BREW_ID, brew.id)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val ratePendingIntent = android.app.PendingIntent.getActivity(
            context, RatingReminderScheduler.NOTIFICATION_ID, rateIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val laterIntent = Intent(context, RatingReminderActionReceiver::class.java)
        val laterPendingIntent = android.app.PendingIntent.getBroadcast(
            context, RatingReminderScheduler.NOTIFICATION_ID, laterIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, RatingReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_coffee)
            .setContentTitle(context.getString(R.string.rating_reminder_title))
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setContentIntent(ratePendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setDefaults(0)
            .addAction(0, context.getString(R.string.rating_reminder_rate), ratePendingIntent)
            .addAction(0, context.getString(R.string.rating_reminder_later), laterPendingIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(RatingReminderScheduler.NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the check above and notify.
        }
        return Result.success()
    }

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                RatingReminderScheduler.CHANNEL_ID,
                context.getString(R.string.rating_reminder_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.rating_reminder_channel_description)
                setSound(null, null)
                enableVibration(false)
            },
        )
    }
}

/** The notification's Later action dismisses this single reminder. */
class RatingReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        NotificationManagerCompat.from(context).cancel(RatingReminderScheduler.NOTIFICATION_ID)
    }
}
