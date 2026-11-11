package io.github.mateuxlucax.caparao.data.scale

import io.github.mateuxlucax.caparao.model.ScaleError
import io.github.mateuxlucax.caparao.model.ScaleReading
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticErrorContext
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticEvent
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticFrameClassification
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticFrameType
import io.github.mateuxlucax.caparao.data.diagnostics.Diagnostics
import io.github.mateuxlucax.caparao.data.diagnostics.NoDiagnostics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Keeps a Timemore Dot connected while started: scan, open (3 attempts), the handshake (grams, standard mode), readings
 * and battery, and reconnecting with backoff (1 s doubling up to 10 s) when anything fails.
 *
 * It scans every time instead of remembering an address: the Dot answers a scan in under 100 ms (recorded), and a
 * replaced or reset scale then needs no extra step. The Bluetooth work itself is behind [BleTransport], so this
 * logic is tested on the JVM with virtual time.
 */
class ScaleClient(
    private val transport: BleTransport,
    private val scope: CoroutineScope,
    private val diagnostics: Diagnostics = NoDiagnostics,
    private val clock: () -> Long = System::currentTimeMillis,
) : ScaleSource {
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

    private val _log = MutableStateFlow<List<String>>(emptyList())
    override val log: StateFlow<List<String>> = _log.asStateFlow()

    private val decoder = FrameDecoder()
    private var job: Job? = null

    override fun start() {
        if (job?.isActive == true) return
        diagnostics.event(DiagnosticEvent.ScaleStarted)
        job = scope.launch { run() }
    }

    override fun stop() {
        diagnostics.event(DiagnosticEvent.ScaleStopped)
        job?.cancel()
        job = null
        transport.close()
        _reading.value = null
        _state.value = ScaleState.Idle
        note("stopped")
    }

    override fun tare() = send(DotProtocol.tare(), "tare")

    override fun timer(action: TimerAction) = when (action) {
        TimerAction.START -> send(DotProtocol.timerStart(), "timer start")
        TimerAction.PAUSE -> send(DotProtocol.timerPause(), "timer pause")
        TimerAction.RESET -> send(DotProtocol.timerReset(), "timer reset")
    }

    /** Commands go out only while connected; a failed write surfaces as a lost link, so it is not reported here. */
    private fun send(frame: ByteArray, what: String) {
        if (_state.value !is ScaleState.Connected) return
        scope.launch {
            note(what)
            runCatching { write(frame) }
        }
    }

    private suspend fun run() {
        var backoff = FIRST_BACKOFF_MS
        while (true) {
            val blocked = when (transport.readiness()) {
                BleReadiness.READY -> null
                BleReadiness.BLUETOOTH_OFF -> ScaleError.BLUETOOTH_OFF
                BleReadiness.PERMISSION_MISSING -> ScaleError.PERMISSION_MISSING
            }
            if (blocked != null) {
                // Needs the user; the screen calls start() again once it is fixed.
                diagnostics.event(DiagnosticEvent.ScaleConnectionFailed)
                fail(blocked, retrying = false)
                return
            }
            if (session()) backoff = FIRST_BACKOFF_MS
            note("retrying in ${backoff / 1000} s")
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    /** One attempt and, when it connects, the whole connected period. Returns whether it got connected. */
    private suspend fun session(): Boolean {
        _state.value = ScaleState.Searching
        note("searching")
        diagnostics.event(DiagnosticEvent.ScanStarted)
        val address = transport.scan(SCAN_TIMEOUT_MS)
        if (address == null) {
            diagnostics.event(DiagnosticEvent.ScaleNotFound)
            fail(ScaleError.NOT_FOUND, retrying = true)
            return false
        }
        _state.value = ScaleState.Connecting
        note("found, connecting")
        diagnostics.event(DiagnosticEvent.ScaleConnecting)
        try {
            open(address)
        } catch (e: BleException) {
            transport.close()
            val error = if (e.reason == BleException.Reason.PAIRING_REFUSED) ScaleError.PAIRED_ELSEWHERE else ScaleError.NOT_FOUND
            diagnostics.event(DiagnosticEvent.ScaleConnectionFailed)
            diagnostics.error(e, DiagnosticErrorContext.SCALE)
            note("open failed: ${e.message}")
            fail(error, retrying = true)
            return false
        }
        decoder.reset()
        diagnostics.event(DiagnosticEvent.ScaleConnected)
        try {
            connected()
        } catch (e: BleException) {
            diagnostics.event(DiagnosticEvent.ScaleConnectionFailed)
            diagnostics.error(e, DiagnosticErrorContext.SCALE)
            note("write failed: ${e.message}")
        }
        transport.close()
        _reading.value = null
        fail(ScaleError.CONNECTION_LOST, retrying = true)
        diagnostics.disconnect()
        return true
    }

    private suspend fun open(address: String) {
        var attempt = 1
        while (true) {
            try {
                transport.open(address)
                return
            } catch (e: BleException) {
                if (e.reason == BleException.Reason.PAIRING_REFUSED || attempt == OPEN_ATTEMPTS) throw e
                note("attempt $attempt failed: ${e.message}")
                transport.close()
                delay(OPEN_RETRY_MS)
                attempt++
            }
        }
    }

    /** Handshake, then readings and battery until the link drops. */
    private suspend fun connected() = coroutineScope {
        val linkLost = CompletableDeferred<Unit>()
        // Subscribed before the first write, so no answer is missed.
        launch(start = CoroutineStart.UNDISPATCHED) {
            transport.events.collect { event ->
                when (event) {
                    is BleEvent.Notification -> {
                        val original = event.bytes.copyOf()
                        val messages = decoder.feed(original)
                        diagnostics.notification(
                            original,
                            DiagnosticFrameClassification(messages.map(ScaleMessage::diagnosticFrameType), decoder.hasPendingBytes),
                        )
                        messages.forEach(::handle)
                    }
                    // The event and recorder share a fixed monotonic clock; no address or exception text is included.
                    BleEvent.LinkLost -> {
                        diagnostics.event(DiagnosticEvent.ScaleLinkLost)
                        linkLost.complete(Unit)
                    }
                }
            }
        }
        delay(HANDSHAKE_DELAY_MS)
        write(DotProtocol.unitGrams())
        delay(MODE_DELAY_MS)
        write(DotProtocol.standardMode())
        write(DotProtocol.readBattery())
        _state.value = ScaleState.Connected(battery = null)
        note("connected")
        // The Dot's own battery push is not reliable, so ask for it.
        launch {
            while (true) {
                delay(BATTERY_POLL_MS)
                write(DotProtocol.readBattery())
            }
        }
        linkLost.await()
        note("link lost")
        coroutineContext.cancelChildren()
    }

    private fun handle(message: ScaleMessage) {
        when (message) {
            is ScaleMessage.Weight -> publish(message.reading)
            is ScaleMessage.Battery -> _state.update { if (it is ScaleState.Connected) ScaleState.Connected(message.battery) else it }
            is ScaleMessage.Ack, is ScaleMessage.Model, is ScaleMessage.Unknown -> Unit
        }
    }

    private fun fail(error: ScaleError, retrying: Boolean) {
        _state.value = ScaleState.Failed(error, retrying)
        note("failed: $error")
    }

    /** Snapshot before handing the bytes to BLE; record them only after the platform write succeeds. */
    private suspend fun write(frame: ByteArray) {
        val snapshot = frame.copyOf()
        transport.write(snapshot.copyOf())
        diagnostics.command(snapshot)
    }

    private fun note(text: String) {
        _log.update { (it + "${clock()} $text").takeLast(LOG_LINES) }
    }

    internal companion object {
        const val SCAN_TIMEOUT_MS = 10_000L
        const val OPEN_ATTEMPTS = 3
        const val OPEN_RETRY_MS = 500L
        const val HANDSHAKE_DELAY_MS = 500L
        const val MODE_DELAY_MS = 200L
        const val BATTERY_POLL_MS = 60_000L
        const val FIRST_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 10_000L
        const val LOG_LINES = 200
    }
}

private fun ScaleMessage.diagnosticFrameType() = when (this) {
    is ScaleMessage.Weight -> DiagnosticFrameType.WEIGHT
    is ScaleMessage.Battery -> DiagnosticFrameType.BATTERY
    is ScaleMessage.Ack -> DiagnosticFrameType.ACK
    is ScaleMessage.Model -> DiagnosticFrameType.MODEL
    is ScaleMessage.Unknown -> DiagnosticFrameType.UNKNOWN
}
