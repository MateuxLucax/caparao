package io.github.mateuxlucax.caparao.wear

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
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
    }
}
