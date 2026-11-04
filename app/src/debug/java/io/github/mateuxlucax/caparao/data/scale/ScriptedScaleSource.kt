package io.github.mateuxlucax.caparao.data.scale

import android.os.SystemClock
import io.github.mateuxlucax.caparao.model.ScaleBattery
import io.github.mateuxlucax.caparao.model.ScaleError
import io.github.mateuxlucax.caparao.model.ScaleReading
import io.github.mateuxlucax.caparao.model.ScaleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.roundToInt

/** Plays scenario events as valid Dot frames. JVM tests can advance its clock without sleeping. */
class ScriptedScaleSource(
    private val scope: CoroutineScope,
    json: String,
    speed: Double = 1.0,
    startPaused: Boolean = false,
    private val autoRun: Boolean = true,
    private val monotonicMs: () -> Long = SystemClock::elapsedRealtime,
) : ScaleSource {
    init {
        require(speed.isFinite() && speed > 0.0)
    }

    private var events = parseEvents(JSONObject(json))
    private val decoder = FrameDecoder()
    private val _state = MutableStateFlow<ScaleState>(ScaleState.Idle)
    override val state: StateFlow<ScaleState> = _state.asStateFlow()
    private val _reading = MutableStateFlow<ScaleReading?>(null)
    override val reading: StateFlow<ScaleReading?> = _reading.asStateFlow()
    private val _readings = MutableSharedFlow<ScaleReading>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val readings: SharedFlow<ScaleReading> = _readings.asSharedFlow()
    private val _log = MutableStateFlow(listOf("playing a scripted scale scenario"))
    override val log: StateFlow<List<String>> = _log.asStateFlow()

    private var job: Job? = null
    private var nextEvent = 0
    private var paused = startPaused
    private var playbackSpeed = speed
    private var tareOffset = 0.0
    private var lastRawWeight = 0.0
    private var lastFrameAtMs: Long? = null
    private var clockMs = 0L
    private var startedAtMs = 0L
    private var started = false
    private var timerRunning = false
    private var timerElapsedMs = 0L
    private var timerStartedAtMs = 0L

    /** Scenario time for the detector; follows the virtual clock in tests and device time during playback. */
    val nowMs: Long
        get() = if (autoRun && started && !paused) clockMs + ((monotonicMs() - startedAtMs) * playbackSpeed).toLong() else clockMs

    val position: JSONObject
        get() = JSONObject().put("event", nextEvent).put("total", events.size).put("paused", paused)

    override fun start() {
        if (started) return
        _state.value = ScaleState.Searching
        started = true
        startedAtMs = monotonicMs()
        if (nextEvent == 0 && clockMs == 0L) advanceTo(0L)
        startPlayback()
    }

    override fun stop() {
        clockMs = nowMs
        job?.cancel()
        job = null
        started = false
        _reading.value = null
        _state.value = ScaleState.Idle
    }

    override fun tare() {
        tareOffset = lastRawWeight
        _reading.value = _reading.value?.copy(weightGrams = 0.0)
    }

    override fun timer(action: TimerAction) {
        when (action) {
            TimerAction.START -> if (!timerRunning) {
                timerRunning = true
                timerStartedAtMs = nowMs
            }
            TimerAction.PAUSE -> if (timerRunning) {
                timerElapsedMs += nowMs - timerStartedAtMs
                timerRunning = false
            }
            TimerAction.RESET -> {
                timerElapsedMs = 0
                timerRunning = false
            }
        }
    }

    /** Apply all scheduled events through [targetMs], without waiting for wall time. */
    @Synchronized
    fun advanceTo(targetMs: Long) {
        require(targetMs >= clockMs) { "Scenario time cannot move backwards" }
        while (nextEvent < events.size && events[nextEvent].atMs <= targetMs) {
            val event = events[nextEvent++]
            clockMs = event.atMs
            apply(event)
        }
        clockMs = targetMs
        startedAtMs = monotonicMs()
    }

    /** Advance to the next event, or by [deltaMs] when supplied. */
    @Synchronized
    fun advance(deltaMs: Long? = null) {
        val target = deltaMs?.let { nowMs + it } ?: events.getOrNull(nextEvent)?.atMs ?: nowMs
        advanceTo(target)
    }

    @Synchronized
    fun pause() {
        clockMs = nowMs
        startedAtMs = monotonicMs()
        paused = true
    }

    @Synchronized
    fun resume() {
        paused = false
        startedAtMs = monotonicMs()
        if (started && job?.isActive != true) startPlayback()
    }

    @Synchronized
    fun changeSpeed(value: Double) {
        require(value.isFinite() && value > 0.0) { "speed must be greater than zero" }
        clockMs = nowMs
        playbackSpeed = value
        startedAtMs = monotonicMs()
    }

    @Synchronized
    fun loadScenario(json: String) {
        events = parseEvents(JSONObject(json))
        nextEvent = 0
        clockMs = 0L
        startedAtMs = monotonicMs()
        decoder.reset()
        tareOffset = 0.0
        lastRawWeight = 0.0
        lastFrameAtMs = null
        timerElapsedMs = 0L
        timerRunning = false
        _reading.value = null
        _state.value = ScaleState.Searching
        paused = true
        job?.cancel()
        job = null
        if (started) startPlayback()
    }

    @Synchronized
    fun reading(atMs: Long, weightGrams: Double?, flowGramsPerSecond: Double?, overload: Boolean = false) {
        require((weightGrams == null) xor (flowGramsPerSecond == null)) { "provide weight_g or flow_gps, not both" }
        require(atMs >= clockMs) { "reading time cannot move backwards" }
        val grams = weightGrams ?: (lastRawWeight + requireNotNull(flowGramsPerSecond) * ((atMs - clockMs) / 1000.0))
        clockMs = atMs
        weight(grams, timerSeconds(), overload)
    }

    /** Injects a named exceptional event, such as `drop`, `overload`, or `connection_failed`. */
    @Synchronized
    fun inject(type: String, data: JSONObject = JSONObject()) {
        if (type == "tare") tare() else apply(ScenarioEvent(clockMs, type, data))
    }

    private fun startPlayback() {
        if (!autoRun || job?.isActive == true) return
        job = scope.launch {
            while (nextEvent < events.size) {
                while (paused) delay(25)
                val event = events[nextEvent]
                while (true) {
                    while (paused) delay(25)
                    val remaining = event.atMs - nowMs
                    if (remaining <= 0) break
                    delay((remaining / playbackSpeed).toLong().coerceIn(1L, 25L))
                }
                clockMs = event.atMs
                startedAtMs = monotonicMs()
                apply(event)
                nextEvent++
            }
        }
    }

    private fun apply(event: ScenarioEvent) {
        when (event.type) {
            "searching" -> _state.value = ScaleState.Searching
            "connecting" -> _state.value = ScaleState.Connecting
            "connected" -> {
                _state.value = ScaleState.Connected(null)
                event.data.optJSONObject("battery")?.let { battery ->
                    feed(batteryFrame(DotProtocol.TYPE_READ, battery.optInt("bars", 3), battery.optInt("percent", 80)))
                }
            }
            "connection_failed", "not_found" -> _state.value = ScaleState.Failed(
                if (event.type == "not_found") ScaleError.NOT_FOUND else ScaleError.valueOf(event.data.optString("error", "CONNECTION_LOST")),
                event.data.optBoolean("retrying", false),
            )
            "drop" -> _state.value = ScaleState.Failed(ScaleError.CONNECTION_LOST, event.data.optBoolean("retrying", true))
            "reconnect" -> _state.value = ScaleState.Connected(null)
            "battery" -> feed(batteryFrame(if (event.data.optString("delivery", "read") == "push") DotProtocol.TYPE_NOTIFY else DotProtocol.TYPE_READ,
                event.data.optInt("bars", 3), event.data.optInt("percent", 80)))
            "negative_boot" -> weight(-2.0, 0, false)
            "weight" -> weight(event.data.optDouble("grams", event.data.optDouble("weight_g")), event.data.optInt("timer", timerSeconds()), event.data.optBoolean("overload", false))
            "overload" -> weight(event.data.optDouble("grams", 1300.0), event.data.optInt("timer", 0), true)
            "tare" -> tare()
            "lift" -> weight(event.data.optDouble("grams", 0.0), event.data.optInt("timer", timerSeconds()), false)
            "first_drop" -> weight(event.data.optDouble("grams", lastRawWeight + 1.0), event.data.optInt("timer", timerSeconds()), false)
            "flow_stop" -> weight(event.data.optDouble("grams", lastRawWeight), event.data.optInt("timer", timerSeconds()), false)
            "noop" -> Unit
            else -> error("Unknown scripted scale event: ${event.type}")
        }
    }

    private fun weight(grams: Double, timer: Int, overload: Boolean) {
        val previousAtMs = lastFrameAtMs
        val flow = previousAtMs?.let { ((grams - lastRawWeight) * 1000.0 / (clockMs - it).coerceAtLeast(1L)).coerceIn(-99.9, 99.9) } ?: 0.0
        feed(weightFrame(grams, flow, timer, overload))
        lastFrameAtMs = clockMs
    }

    private fun feed(frame: ByteArray) {
        decoder.feed(frame).forEach { message ->
            when (message) {
                is ScaleMessage.Weight -> {
                    lastRawWeight = message.reading.weightGrams
                    val reading = message.reading.copy(weightGrams = ((lastRawWeight - tareOffset) * 10).roundToInt() / 10.0)
                    _reading.value = reading
                    _readings.tryEmit(reading)
                }
                is ScaleMessage.Battery -> _state.value = ScaleState.Connected(message.battery)
                is ScaleMessage.Ack, is ScaleMessage.Model, is ScaleMessage.Unknown -> Unit
            }
        }
    }

    private data class ScenarioEvent(val atMs: Long, val type: String, val data: JSONObject)

    private fun parseEvents(root: JSONObject): List<ScenarioEvent> {
        val dose = root.optDouble("dose_g", 18.0)
        val target = dose * root.optDouble("ratio", 15.0)
        val raw = root.getJSONArray("events")
        val result = mutableListOf<ScenarioEvent>()
        for (index in 0 until raw.length()) {
            val item = raw.getJSONObject(index)
            val at = item.optLong("at_ms", result.lastOrNull()?.atMs ?: 0L)
            if (item.getString("type") == "hold") {
                val until = item.getLong("until_ms")
                require(until >= at) { "hold until_ms cannot be before at_ms" }
                val every = item.optLong("every_ms", 100).coerceAtLeast(1L)
                val grams = relative(item, "grams", dose, target)
                var time = at
                while (time <= until) {
                    result += ScenarioEvent(time, "weight", JSONObject().put("grams", grams))
                    time += every
                }
                continue
            }
            if (item.getString("type") != "ramp") {
                val data = JSONObject(item.toString())
                if (item.getString("type") == "weight") {
                    val hasWeight = item.has("grams") || item.has("weight_g")
                    require(!(hasWeight && item.has("flow_gps"))) { "a scenario row cannot contain both weight_g and flow_gps" }
                    when {
                        item.has("flow_gps") -> {
                            val lastWeight = result.lastOrNull { it.type == "weight" }?.data?.optDouble("grams") ?: 0.0
                            val lastAtMs = result.lastOrNull { it.type == "weight" }?.atMs ?: at
                            data.put("grams", lastWeight + item.getDouble("flow_gps") * (at - lastAtMs).coerceAtLeast(0L) / 1000.0)
                        }
                        item.has("relative_to") -> data.put("grams", relative(item, "grams", dose, target))
                        item.has("weight_g") -> data.put("grams", item.getDouble("weight_g"))
                    }
                    data.remove("relative_to")
                    data.remove("offset_g")
                    data.remove("flow_gps")
                    data.remove("weight_g")
                }
                result += ScenarioEvent(at, item.getString("type"), data)
                continue
            }
            require(!(item.has("flow_gps") && (item.has("from_g") || item.has("to_g")))) {
                "a scenario ramp cannot contain both weight and flow"
            }
            val from = relative(item, "from_g", dose, target)
            val to = relative(item, "to_g", dose, target)
            val step = item.optDouble("step_g", 1.0).let { if (to >= from) kotlin.math.abs(it).coerceAtLeast(0.1) else -kotlin.math.abs(it).coerceAtLeast(0.1) }
            val interval = item.optLong("every_ms", 100)
            var value = from
            var tick = 0
            while (value <= to + 0.0001) {
                result += ScenarioEvent(at + tick * interval, "weight", JSONObject().put("grams", value))
                value += step
                tick++
            }
            if (result.last().let { kotlin.math.abs(it.data.optDouble("grams") - to) } > 0.0001) {
                result += ScenarioEvent(at + tick * interval, "weight", JSONObject().put("grams", to))
            }
        }
        return result.sortedBy { it.atMs }
    }

    private fun relative(item: JSONObject, key: String, dose: Double, target: Double): Double {
        val relative = when (item.optString("relative_to")) {
            "dose" -> dose
            "target" -> target
            else -> null
        }
        return (relative?.let { item.optDouble(key, 0.0) * it } ?: item.optDouble(key, 0.0)) + item.optDouble("offset_g", 0.0)
    }

    private fun timerSeconds(): Int = ((timerElapsedMs + if (timerRunning) nowMs - timerStartedAtMs else 0L) / 1000).toInt()

    private fun weightFrame(grams: Double, flow: Double, timer: Int, overload: Boolean): ByteArray {
        val decigrams = (grams * 10).roundToInt()
        val payload = byteArrayOf(
            (decigrams shr 24).toByte(), (decigrams shr 16).toByte(), (decigrams shr 8).toByte(), decigrams.toByte(),
            ((flow * 10).roundToInt() shr 8).toByte(), (flow * 10).roundToInt().toByte(),
            (timer shr 8).toByte(), timer.toByte(), if (overload) 1 else 0,
        )
        return frame(DotProtocol.TYPE_NOTIFY, DotProtocol.CMD_WEIGHT, payload)
    }

    private fun batteryFrame(type: Int, bars: Int, percent: Int) = frame(type, DotProtocol.CMD_BATTERY, byteArrayOf(bars.toByte(), percent.toByte()))

    private fun frame(type: Int, command: Int, payload: ByteArray): ByteArray {
        val body = DotProtocol.HEADER_SIZE + payload.size
        val output = ByteArray(body + DotProtocol.CRC_SIZE)
        output[0] = DotProtocol.HEADER_1
        output[1] = DotProtocol.HEADER_2
        output[2] = type.toByte()
        output[3] = command.toByte()
        output[5] = payload.size.toByte()
        payload.copyInto(output, DotProtocol.HEADER_SIZE)
        val crc = DotProtocol.crc16(output, 0, body)
        output[body] = (crc shr 8).toByte()
        output[body + 1] = crc.toByte()
        return output
    }
}
