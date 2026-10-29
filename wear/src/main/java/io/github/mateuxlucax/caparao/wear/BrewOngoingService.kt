package io.github.mateuxlucax.caparao.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import io.github.mateuxlucax.caparao.watch.WatchPaths
import io.github.mateuxlucax.caparao.watch.WatchSnapshot
import io.github.mateuxlucax.caparao.watch.WatchState

/**
 * An Ongoing Activity while a brew is ready or running: the watch face shows its icon and raising the wrist comes back
 * to [WatchActivity] (docs/adr/0023-watch-companion.md). It keeps listening to the phone so it ends with the brew, or
 * when the phone goes quiet.
 */
class BrewOngoingService : Service() {
    private val messages by lazy { Wearable.getMessageClient(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var receivedAtMs = 0L

    private val listener = MessageClient.OnMessageReceivedListener { event ->
        if (event.path != WatchPaths.SNAPSHOT) return@OnMessageReceivedListener
        val snapshot = WatchSnapshot.decode(event.data) ?: return@OnMessageReceivedListener
        receivedAtMs = SystemClock.elapsedRealtime()
        if (!isBrewing(snapshot.state)) stopSelf()
    }

    private val staleCheck = object : Runnable {
        override fun run() {
            if (SystemClock.elapsedRealtime() - receivedAtMs > STALE_AFTER_MS) stopSelf() else handler.postDelayed(this, STALE_AFTER_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        receivedAtMs = SystemClock.elapsedRealtime()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.brew_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, WatchActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.brew_ongoing))
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
        OngoingActivity.Builder(this, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_launcher_monochrome)
            .setTouchIntent(open)
            .setStatus(Status.Builder().addTemplate(getString(R.string.brew_ongoing)).build())
            .build()
            .apply(this)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        messages.addListener(listener)
        handler.postDelayed(staleCheck, STALE_AFTER_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(staleCheck)
        messages.removeListener(listener)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "brew"
        private const val NOTIFICATION_ID = 1
        private const val STALE_AFTER_MS = 10_000L

        fun isBrewing(state: WatchState) = state == WatchState.READY || state == WatchState.BREWING

        /** From the visible [WatchActivity] only: a foreground service cannot start from the background. */
        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, BrewOngoingService::class.java)) }
        }
    }
}
