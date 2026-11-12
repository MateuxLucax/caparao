package dev.mateux.caparao.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dev.mateux.caparao.watch.WatchAlert
import dev.mateux.caparao.watch.WatchPaths

/** A heads-up's title and text, as string resources. */
internal data class HeadsUp(val title: Int, val text: Int)

/** The heads-up for [alert], or null when it has none. Only the brew's final moments get one, and each once per brew. */
internal fun headsUpFor(alert: WatchAlert): HeadsUp? = when (alert) {
    WatchAlert.ALL_DONE -> HeadsUp(R.string.finish_filter_title, R.string.finish_filter_text)
    WatchAlert.FRENCH_PRESS_DEADLINE -> HeadsUp(R.string.finish_press_title, R.string.finish_press_text)
    else -> null
}

/**
 * Vibrates for the guide moments the phone sends (#103). A listener service, so the wrist feels
 * them with the screen off or another app open, not only while the watch app is visible.
 */
class AlertListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchPaths.ALERT) return
        val alert = WatchAlert.decode(event.data) ?: return
        // Vibrator, not VibratorManager: the watch's minSdk is 30.
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createWaveform(alert.pattern, -1))
        headsUpFor(alert)?.let(::notifyFinish)
    }

    /**
     * A heads-up the wrist can read without opening the app, naming what to do next: the filter's last pour is done
     * (drawdown follows), or the French press steep is over (remove the press, then press and serve).
     */
    private fun notifyFinish(heads: HeadsUp) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.finish_channel), NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(this, 0, Intent(this, WatchActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(heads.title))
            .setContentText(getString(heads.text))
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
