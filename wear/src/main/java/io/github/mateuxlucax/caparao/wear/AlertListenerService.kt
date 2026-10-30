package io.github.mateuxlucax.caparao.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import io.github.mateuxlucax.caparao.watch.WatchAlert
import io.github.mateuxlucax.caparao.watch.WatchPaths

/**
 * Vibrates for the guide moments the phone sends (docs/adr/0019-guided-brew.md). A listener service, so the wrist feels
 * them with the screen off or another app open, not only while the watch app is visible.
 */
class AlertListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchPaths.ALERT) return
        val alert = WatchAlert.decode(event.data) ?: return
        val vibrator = (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator ?: return
        vibrator.vibrate(VibrationEffect.createWaveform(alert.pattern, -1))
        if (alert == WatchAlert.ALL_DONE) notifyFinish()
    }

    /**
     * The recipe's last step is done: for a filter that is the last pour (drawdown follows), for a French press the
     * final wait, so it is time to finish. A heads-up the wrist can read without opening the app.
     */
    private fun notifyFinish() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.finish_channel), NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(this, 0, Intent(this, WatchActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.finish_title))
            .setContentText(getString(R.string.finish_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(TIMEOUT_MS)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val CHANNEL = "finish"
        const val NOTIFICATION_ID = 2
        const val TIMEOUT_MS = 60_000L
    }
}
