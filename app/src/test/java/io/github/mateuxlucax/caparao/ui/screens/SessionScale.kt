package io.github.mateuxlucax.caparao.ui.screens

import io.github.mateuxlucax.caparao.data.scale.FrameDecoder
import io.github.mateuxlucax.caparao.data.scale.ScaleMessage
import io.github.mateuxlucax.caparao.data.scale.ScaleSource
import io.github.mateuxlucax.caparao.data.scale.TimerAction
import io.github.mateuxlucax.caparao.model.ScaleReading
import io.github.mateuxlucax.caparao.model.ScaleState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

/**
 * A recorded session played frame by frame on the test's command instead of in real time: [playUntil] moves [nowMs]
 * to each frame's own time, so a screen reached at a given point of the session always looks the same. Tare is applied
 * here, like [io.github.mateuxlucax.caparao.data.scale.ReplayScaleSource] does.
 */
class SessionScale(lines: List<String>) : ScaleSource {
    private val frames: List<Pair<Long, ScaleReading>> = FrameDecoder().let { decoder ->
        lines.drop(1).map(::JSONObject).filter { it.getString("dir") == "rx" }.flatMap { line ->
            val bytes = line.getString("hex").split(' ').map { it.toInt(16).toByte() }.toByteArray()
            decoder.feed(bytes).filterIsInstance<ScaleMessage.Weight>().map { line.getLong("t_ms") to it.reading }
        }
    }

    override val state = MutableStateFlow<ScaleState>(ScaleState.Idle)
    override val reading = MutableStateFlow<ScaleReading?>(null)
    override val readings = MutableSharedFlow<ScaleReading>(extraBufferCapacity = 64)
    override val log = MutableStateFlow(listOf("playing a recorded session"))

    /** The session's clock, for the brew detector. */
    var nowMs = 0L
        private set
    private var next = 0
    private var tareOffset = 0.0
    private var lastRawWeight = 0.0

    /** Delivers every frame up to [atMs], calling [settle] after each so the app takes it in before the next. */
    fun playUntil(atMs: Long, settle: () -> Unit) {
        while (next < frames.size && frames[next].first <= atMs) {
            val (t, frame) = frames[next++]
            nowMs = t
            lastRawWeight = frame.weightGrams
            val tared = frame.copy(weightGrams = Math.round((lastRawWeight - tareOffset) * 10) / 10.0)
            reading.value = tared
            check(readings.tryEmit(tared))
            settle()
        }
        nowMs = atMs
    }

    override fun start() {
        state.value = ScaleState.Connected(battery = null)
    }

    override fun stop() = Unit

    override fun tare() {
        tareOffset = lastRawWeight
        reading.update { it?.copy(weightGrams = 0.0) }
    }

    override fun timer(action: TimerAction) = Unit
}
