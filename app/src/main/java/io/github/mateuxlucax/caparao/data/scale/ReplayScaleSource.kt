package io.github.mateuxlucax.caparao.data.scale

import io.github.mateuxlucax.caparao.model.ScaleReading
import io.github.mateuxlucax.caparao.model.ScaleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Plays a session recorded from a real Dot (the probe's JSONL, src/test/resources/sessions) as if the scale were
 * connected: the notifications go through the same [FrameDecoder] at their recorded pace, looping. Used on the
 * emulator, which has no Bluetooth, for screenshots and end-to-end tests (docs/adr/0010-testing-strategy.md).
 *
 * Tare and the timer are applied here, since there is no scale to obey them.
 */
class ReplayScaleSource(
    private val scope: CoroutineScope,
    lines: List<String>,
    /** Where to start in the recording, e.g. to land on a moment with weight on the scale. */
    private val startAtMs: Long = 0,
) : ScaleSource {
    private val notifications: List<Pair<Long, ByteArray>> = lines.drop(1).map(::JSONObject)
        .filter { it.getString("dir") == "rx" }
        .map { it.getLong("t_ms") to hex(it.getString("hex")) }

    private val _state = MutableStateFlow<ScaleState>(ScaleState.Idle)
    override val state: StateFlow<ScaleState> = _state.asStateFlow()

    private val _reading = MutableStateFlow<ScaleReading?>(null)
    override val reading: StateFlow<ScaleReading?> = _reading.asStateFlow()

    // Readings come at 10 Hz and collectors are quick; the buffer covers a slow frame without blocking the decoder.
    private val _readings = MutableSharedFlow<ScaleReading>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val readings: SharedFlow<ScaleReading> = _readings.asSharedFlow()

    private fun publish(reading: ScaleReading) {
        _reading.value = reading
        _readings.tryEmit(reading)
    }

    private val _log = MutableStateFlow(listOf("replaying a recorded session"))
    override val log: StateFlow<List<String>> = _log.asStateFlow()

    private var job: Job? = null
    private var tareOffset = 0.0
    private var lastRawWeight = 0.0
    private var timerRunning = false
    private var timerMs = 0L

    override fun start() {
        if (job?.isActive == true) return
        _state.value = ScaleState.Connecting
        job = scope.launch {
            val decoder = FrameDecoder()
            var from = notifications.indexOfFirst { it.first >= startAtMs }.coerceAtLeast(0)
            _state.value = ScaleState.Connected(battery = null)
            while (true) {
                for (i in from until notifications.size) {
                    val gap = if (i == from) 0 else notifications[i].first - notifications[i - 1].first
                    delay(gap)
                    if (timerRunning) timerMs += gap
                    decoder.feed(notifications[i].second).forEach(::handle)
                }
                from = 0
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        _reading.value = null
        _state.value = ScaleState.Idle
    }

    override fun tare() {
        tareOffset = lastRawWeight
        _reading.update { it?.copy(weightGrams = 0.0) }
    }

    override fun timer(action: TimerAction) {
        when (action) {
            TimerAction.START -> timerRunning = true
            TimerAction.PAUSE -> timerRunning = false
            TimerAction.RESET -> {
                timerRunning = false
                timerMs = 0
            }
        }
    }

    private fun handle(message: ScaleMessage) {
        when (message) {
            is ScaleMessage.Weight -> {
                lastRawWeight = message.reading.weightGrams
                publish(
                    message.reading.copy(
                        weightGrams = Math.round((lastRawWeight - tareOffset) * 10) / 10.0,
                        timerSeconds = (timerMs / 1000).toInt(),
                    ),
                )
            }
            is ScaleMessage.Battery -> _state.value = ScaleState.Connected(message.battery)
            is ScaleMessage.Ack, is ScaleMessage.Model, is ScaleMessage.Unknown -> Unit
        }
    }

    private companion object {
        fun hex(text: String) = text.split(' ').map { it.toInt(16).toByte() }.toByteArray()
    }
}
