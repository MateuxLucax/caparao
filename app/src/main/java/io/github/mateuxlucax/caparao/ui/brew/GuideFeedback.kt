package io.github.mateuxlucax.caparao.ui.brew

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.VibratorManager
import io.github.mateuxlucax.caparao.brew.StepEvent
import io.github.mateuxlucax.caparao.ui.watch.WatchMirror

/** A guide moment and how Settings lets it be felt and heard (docs/adr/0019-guided-brew.md). */
data class GuideAlert(val event: StepEvent, val vibrate: Boolean, val sound: Boolean)

/**
 * Plays [GuideAlert]s: short patterns on the phone's vibrator and tones from [ToneGenerator], so no audio files ship.
 * A step starting is a short buzz and a beep, near the target a light pulse, the target a double buzz and a beep, each
 * of the last 5 s a tick, the end a long buzz and a longer tone. The visual cue on screen is always there.
 */
class GuideFeedback(context: Context) {
    private val vibrator = (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    private val tones = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull()

    fun play(alert: GuideAlert) {
        // The vibration is the watch's too (WatchAlert), so both feel the same.
        val pattern = WatchMirror.alert(alert.event).pattern
        val tone = when (alert.event) {
            is StepEvent.Started -> ToneGenerator.TONE_PROP_BEEP to 120
            is StepEvent.NearTarget -> null
            is StepEvent.Reached -> ToneGenerator.TONE_PROP_BEEP2 to 200
            is StepEvent.Countdown -> ToneGenerator.TONE_PROP_PROMPT to 60
            StepEvent.AllDone -> ToneGenerator.TONE_PROP_ACK to 400
        }
        if (alert.vibrate) vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        if (alert.sound && tone != null) tones?.startTone(tone.first, tone.second)
    }

    fun release() {
        tones?.release()
    }
}
