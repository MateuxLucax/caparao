package io.github.mateuxlucax.caparao.data.diagnostics

import io.github.mateuxlucax.caparao.data.export.ExportedFile
import io.github.mateuxlucax.caparao.model.BrewMode
import androidx.core.content.FileProvider
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** App-owned events that are safe to include in a local diagnostic session. */
sealed interface DiagnosticEvent {
    val note: String

    data object AppForeground : DiagnosticEvent { override val note = "app_foreground" }
    data object AppBackground : DiagnosticEvent { override val note = "app_background" }
    data object ScaleStarted : DiagnosticEvent { override val note = "scale_started" }
    data object ScanStarted : DiagnosticEvent { override val note = "scan_started" }
    data object ScaleNotFound : DiagnosticEvent { override val note = "scale_not_found" }
    data object ScaleConnecting : DiagnosticEvent { override val note = "scale_connecting" }
    data object ScaleConnected : DiagnosticEvent { override val note = "scale_connected" }
    data object ScaleConnectionFailed : DiagnosticEvent { override val note = "scale_connection_failed" }
    data object ScaleLinkLost : DiagnosticEvent { override val note = "scale_link_lost" }
    data object ScaleStopped : DiagnosticEvent { override val note = "scale_stopped" }
    data object BrewScreenOpened : DiagnosticEvent { override val note = "brew_screen_opened" }
    data object BrewScreenClosed : DiagnosticEvent { override val note = "brew_screen_closed" }
    data object BrewArmed : DiagnosticEvent { override val note = "brew_armed" }
    data object BrewStopRequested : DiagnosticEvent { override val note = "brew_stop_requested" }
    /** A filter lift past the last pour ends the brew, without Done (#277). */
    data object BrewLiftEnded : DiagnosticEvent { override val note = "brew_lift_end" }
    data object BrewInterrupted : DiagnosticEvent { override val note = "brew_interrupted" }
    data object BrewResultReady : DiagnosticEvent { override val note = "brew_result_ready" }
    data object SessionStarted : DiagnosticEvent { override val note = "session_started" }

    data class BrewPhaseChanged(val mode: DiagnosticBrewMode, val phase: DiagnosticBrewPhase) : DiagnosticEvent {
        override val note = "brew_${mode.note}_${phase.note}"
    }
}

enum class DiagnosticBrewMode(val note: String) {
    ESPRESSO("espresso"),
    FILTER("filter"),
    FRENCH_PRESS("french_press"),
}

/** Detector phases are copied into the data layer so free-form recipe or coffee text can never enter a log line. */
enum class DiagnosticBrewPhase(val note: String) {
    WAITING_FOR_CUP("waiting_for_cup"),
    TARING("taring"),
    READY("ready"),
    BREWING("brewing"),
    DONE("done"),
}

enum class DiagnosticIncompleteReason(val note: String) {
    SCALE_DISCONNECTED("scale_disconnected"),
    SESSION_SIZE_LIMIT("session_size_limit"),
    WRITER_OVERRUN("writer_overrun"),
    STORAGE_ERROR("storage_error"),
    PROCESS_INTERRUPTED("process_interrupted"),
}

enum class DiagnosticRecorderError { STORAGE }

sealed interface DiagnosticRecorderState {
    data object Idle : DiagnosticRecorderState
    data object Starting : DiagnosticRecorderState
    data class Recording(val startedAtEpochMillis: Long) : DiagnosticRecorderState
    data class Finalizing(val reason: DiagnosticIncompleteReason?) : DiagnosticRecorderState
    data class Ready(
        val file: File,
        val complete: Boolean,
        val reason: DiagnosticIncompleteReason? = null,
    ) : DiagnosticRecorderState
    data class Failed(val reason: DiagnosticRecorderError) : DiagnosticRecorderState
}

/** Limits apply per installation. Starting a session reserves room for its full size cap. */
data class DiagnosticRecorderLimits(
    val maxSessions: Int = MAX_SESSIONS,
    val maxStoredBytes: Long = MAX_STORED_BYTES,
    val maxSessionBytes: Long = MAX_SESSION_BYTES,
    val queueCapacity: Int = WRITER_QUEUE_CAPACITY,
    val maxNotificationBytes: Int = MAX_NOTIFICATION_BYTES,
    val footerReserveBytes: Int = FOOTER_RESERVE_BYTES,
) {
    init {
        require(maxSessions > 0)
        require(maxSessionBytes > 0 && maxStoredBytes >= maxSessionBytes)
        require(queueCapacity > 0 && maxNotificationBytes > 0)
        require(footerReserveBytes > 0 && footerReserveBytes < maxSessionBytes)
    }

    companion object {
        const val MAX_SESSIONS = 10
        const val MAX_STORED_BYTES = 32L * 1024 * 1024
        const val MAX_SESSION_BYTES = 8L * 1024 * 1024
        const val WRITER_QUEUE_CAPACITY = 128
        const val MAX_NOTIFICATION_BYTES = 512
        const val FOOTER_RESERVE_BYTES = 512
    }
}

/**
 * Stores opt-in raw BLE sessions as bounded JSONL. Calls made from BLE and brew handling only copy small values and
 * use [Channel.trySend]; file I/O runs on [ioDispatcher] and cannot hold up the detector or GATT event collector.
 */
class DiagnosticRecorder(
    private val directory: File,
    private val scope: CoroutineScope,
    private val monotonicNanos: () -> Long = System::nanoTime,
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val limits: DiagnosticRecorderLimits = DiagnosticRecorderLimits(),
    private val outputStream: (File, Boolean) -> OutputStream = { file, append -> FileOutputStream(file, append) },
) {
    private val lock = Any()
    private val lifecycleLock = kotlinx.coroutines.sync.Mutex()
    private val _state = MutableStateFlow<DiagnosticRecorderState>(DiagnosticRecorderState.Idle)
    val state: StateFlow<DiagnosticRecorderState> = _state.asStateFlow()

    private var active: Session? = null
    private var finishing: Session? = null

    /** Start one new recording. Returns false when already recording or when storage cannot create a file. */
    suspend fun start(): Boolean = lifecycleLock.withLock {
        synchronized(lock) {
            if (active != null) return@withLock false
            _state.value = DiagnosticRecorderState.Starting
        }
        finishingSession()?.let { it.finished.await() }

        val wallTime = wallClockMillis()
        val startNanos = monotonicNanos()
        val prepared = try {
            withContext(ioDispatcher) {
                prepareDirectory()
                pruneForNewSession()
                val file = newFile(wallTime)
                val header = header(wallTime)
                outputStream(file, false).buffered().use { stream ->
                    stream.write(header)
                    stream.flush()
                }
                file to header.size.toLong()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            synchronized(lock) { _state.value = DiagnosticRecorderState.Failed(DiagnosticRecorderError.STORAGE) }
            return@withLock false
        }

        val (file, headerBytes) = prepared
        val session = Session(file, startNanos, headerBytes, Channel(limits.queueCapacity))
        synchronized(lock) {
            active = session
            _state.value = DiagnosticRecorderState.Recording(wallTime)
            enqueueLocked(session, Payload.Event(DiagnosticEvent.SessionStarted.note))
        }
        scope.launch(ioDispatcher) { writeSession(session) }
        true
    }

    /** Close the active file and wait for its bounded queue to drain; safe when called concurrently. */
    suspend fun stop(): DiagnosticRecorderState {
        val session = synchronized(lock) {
            val current = active
            if (current != null) {
                active = null
                finishing = current
                current.endingAtMs = elapsedMsLocked(current)
                current.channel.close()
                _state.value = DiagnosticRecorderState.Finalizing(current.incompleteReason)
                current
            } else {
                finishing
            }
        }
        session?.finished?.await()
        return state.value
    }

    /** Store one original GATT notification chunk, before the scale decoder can consume or transform it. */
    fun recordNotification(bytes: ByteArray) {
        synchronized(lock) {
            val session = active ?: return
            if (bytes.size > limits.maxNotificationBytes) {
                markIncompleteLocked(session, DiagnosticIncompleteReason.SESSION_SIZE_LIMIT)
                return
            }
            enqueueLocked(session, Payload.Raw("rx", bytes.copyOf(), "notification"))
        }
    }

    /** Store a successful outgoing write. The caller passes a snapshot captured before handing bytes to Android BLE. */
    fun recordCommand(bytes: ByteArray) {
        synchronized(lock) {
            val session = active ?: return
            if (bytes.size > limits.maxNotificationBytes) {
                markIncompleteLocked(session, DiagnosticIncompleteReason.SESSION_SIZE_LIMIT)
                return
            }
            enqueueLocked(session, Payload.Raw("tx", bytes.copyOf(), "command"))
        }
    }

    fun recordEvent(event: DiagnosticEvent) {
        synchronized(lock) {
            val session = active ?: return
            enqueueLocked(session, Payload.Event(event.note))
        }
    }

    /** Logs the link drop and closes the session as incomplete. A deliberate app stop does not call this. */
    fun recordDisconnect() {
        recordEvent(DiagnosticEvent.ScaleLinkLost)
        finishAfterDisconnect()
    }

    /** Finish after the scale state has been published so an active brew can record its interrupted phase first. */
    fun finishAfterDisconnect() {
        synchronized(lock) {
            val session = active ?: return
            markIncompleteLocked(session, DiagnosticIncompleteReason.SCALE_DISCONNECTED)
        }
    }

    /** Re-read the retained session list when Settings opens, including recovery after process death. */
    suspend fun refresh() = withContext(ioDispatcher) {
        val latest = listSessionFiles().maxByOrNull(File::lastModified)
        val recovered = latest?.let(::readSessionStatus)
        synchronized(lock) {
            // A settings screen can request refresh just as recording starts. Do not overwrite the newer state.
            val current = _state.value
            val settled = current == DiagnosticRecorderState.Idle || current is DiagnosticRecorderState.Ready || current is DiagnosticRecorderState.Failed
            if (active == null && finishing == null && settled) {
                _state.value = recovered ?: DiagnosticRecorderState.Idle
            }
        }
    }

    /** A local FileProvider share handle for the newest retained session. */
    fun shareLatest(context: Context): ExportedFile? = synchronized(lock) {
        val file = (state.value as? DiagnosticRecorderState.Ready)?.file ?: return@synchronized null
        if (!file.isFile) return@synchronized null
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        ExportedFile(uri, "application/x-ndjson")
    }

    private fun enqueueLocked(session: Session, payload: Payload) {
        val line = PendingLine(session.nextSequence++, elapsedMsLocked(session), payload)
        if (session.channel.trySend(line).isFailure) {
            markIncompleteLocked(session, DiagnosticIncompleteReason.WRITER_OVERRUN)
        }
    }

    /** Caller holds [lock]. */
    private fun markIncompleteLocked(session: Session, reason: DiagnosticIncompleteReason) {
        if (session.incompleteReason == null) session.incompleteReason = reason
        if (active === session) {
            active = null
            finishing = session
            session.endingAtMs = elapsedMsLocked(session)
            session.channel.close()
        }
        _state.value = DiagnosticRecorderState.Finalizing(session.incompleteReason)
    }

    private suspend fun writeSession(session: Session) {
        var bytesWritten = session.headerBytes
        var writeFailed = false
        try {
            outputStream(session.file, true).buffered().use { stream ->
                var unflushed = 0
                for (line in session.channel) {
                    val encoded = encode(line)
                    if (bytesWritten + encoded.size + limits.footerReserveBytes > limits.maxSessionBytes) {
                        synchronized(lock) { markIncompleteLocked(session, DiagnosticIncompleteReason.SESSION_SIZE_LIMIT) }
                        break
                    }
                    stream.write(encoded)
                    bytesWritten += encoded.size
                    if (++unflushed >= FLUSH_EVERY_LINES) {
                        stream.flush()
                        unflushed = 0
                    }
                }

                val reason = synchronized(lock) { session.incompleteReason }
                val footer = footer(session, reason)
                if (bytesWritten + footer.size <= limits.maxSessionBytes) {
                    stream.write(footer)
                    bytesWritten += footer.size
                } else {
                    writeFailed = true
                }
                stream.flush()
            }
        } catch (_: Exception) {
            writeFailed = true
        }

        val reason = synchronized(lock) {
            if (writeFailed && session.incompleteReason == null) session.incompleteReason = DiagnosticIncompleteReason.STORAGE_ERROR
            session.incompleteReason
        }
        val result = if (session.file.isFile) {
            DiagnosticRecorderState.Ready(session.file, complete = reason == null && !writeFailed, reason = reason)
        } else {
            DiagnosticRecorderState.Failed(DiagnosticRecorderError.STORAGE)
        }
        synchronized(lock) {
            if (active === session) active = null
            if (finishing === session) finishing = null
            _state.value = result
        }
        session.finished.complete(Unit)
    }

    private fun prepareDirectory() {
        if (!directory.exists() && !directory.mkdirs()) throw IOException("diagnostic directory")
        if (!directory.isDirectory) throw IOException("diagnostic directory")
    }

    /** Remove the oldest completed sessions until a full new session can fit under both retention bounds. */
    private fun pruneForNewSession() {
        val files = listSessionFiles().sortedBy(File::lastModified).toMutableList()
        var total = files.sumOf(File::length)
        while (files.size >= limits.maxSessions || total + limits.maxSessionBytes > limits.maxStoredBytes) {
            val oldest = files.firstOrNull() ?: break
            val oldestBytes = oldest.length()
            if (!oldest.delete()) throw IOException("diagnostic retention")
            files.removeAt(0)
            total -= oldestBytes
        }
    }

    private fun listSessionFiles(): List<File> = directory.listFiles()
        .orEmpty()
        .filter { it.isFile && it.name.startsWith(FILE_PREFIX) && it.name.endsWith(FILE_SUFFIX) }

    private fun newFile(wallTime: Long): File {
        val stamp = FILE_STAMP.format(Instant.ofEpochMilli(wallTime))
        var suffix = 0
        while (true) {
            val tail = if (suffix == 0) "" else "-$suffix"
            val file = File(directory, "$FILE_PREFIX$stamp$tail$FILE_SUFFIX")
            if (file.createNewFile()) return file
            suffix++
        }
    }

    private fun header(wallTime: Long): ByteArray = jsonLine(
        JSONObject()
            .put("format", FORMAT_NAME)
            .put("format_version", FORMAT_VERSION)
            .put("app", APP_NAME)
            .put("started_at", Instant.ofEpochMilli(wallTime).toString()),
    )

    private fun encode(line: PendingLine): ByteArray {
        val json = JSONObject()
            .put("t_ms", line.elapsedMs)
            .put("seq", line.sequence)
            .put("dir", line.payload.dir)
            .put("hex", line.payload.hex)
            .put("note", line.payload.note)
        return jsonLine(json)
    }

    private fun footer(session: Session, reason: DiagnosticIncompleteReason?): ByteArray {
        val at = synchronized(lock) { session.endingAtMs ?: elapsedMsLocked(session) }
        val json = JSONObject()
            .put("t_ms", at)
            .put("seq", synchronized(lock) { session.nextSequence })
            .put("dir", "event")
            .put("hex", "")
            .put("note", if (reason == null) "session_end" else "session_incomplete")
            .put("complete", reason == null)
        reason?.let { json.put("reason", it.note) }
        return jsonLine(json)
    }

    private fun jsonLine(json: JSONObject): ByteArray = (json.toString() + "\n").toByteArray(Charsets.UTF_8)

    private fun elapsedMsLocked(session: Session): Long {
        val elapsed = ((monotonicNanos() - session.startedAtNanos).coerceAtLeast(0) / NANOS_PER_MILLISECOND)
        session.lastElapsedMs = maxOf(session.lastElapsedMs, elapsed)
        return session.lastElapsedMs
    }

    private fun readSessionStatus(file: File): DiagnosticRecorderState.Ready {
        val finalLine = runCatching { file.useLines { it.lastOrNull() } }.getOrNull()
        val footer = runCatching { finalLine?.let(::JSONObject) }.getOrNull()
        val complete = footer?.optString("note") == "session_end" && footer.optBoolean("complete", false)
        val reason = if (complete) null else {
            val encoded = footer?.optString("reason").orEmpty()
            DiagnosticIncompleteReason.entries.firstOrNull { it.note == encoded } ?: DiagnosticIncompleteReason.PROCESS_INTERRUPTED
        }
        return DiagnosticRecorderState.Ready(file, complete, reason)
    }

    private fun finishingSession(): Session? = synchronized(lock) { finishing }

    private class Session(
        val file: File,
        val startedAtNanos: Long,
        val headerBytes: Long,
        val channel: Channel<PendingLine>,
    ) {
        val finished = CompletableDeferred<Unit>()
        var nextSequence = 0L
        var lastElapsedMs = 0L
        var endingAtMs: Long? = null
        var incompleteReason: DiagnosticIncompleteReason? = null
    }

    private sealed interface Payload {
        val dir: String
        val note: String
        val hex: String

        data class Event(override val note: String) : Payload {
            override val dir = "event"
            override val hex = ""
        }

        data class Raw(val direction: String, val bytes: ByteArray, override val note: String) : Payload {
            override val dir = direction
            override val hex: String get() = bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        }
    }

    private data class PendingLine(val sequence: Long, val elapsedMs: Long, val payload: Payload)

    companion object {
        const val FORMAT_NAME = "caparao-diagnostic-jsonl"
        const val FORMAT_VERSION = 1
        private const val APP_NAME = "Caparaó"
        private const val FILE_PREFIX = "diagnostic-"
        private const val FILE_SUFFIX = ".jsonl"
        private const val FLUSH_EVERY_LINES = 8
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS'Z'").withZone(ZoneOffset.UTC)
    }
}

/** Map every allowed app mode to a fixed token; user-entered recipe text never enters this event. */
fun DiagnosticRecorder.recordBrewPhase(mode: BrewMode, phase: DiagnosticBrewPhase) {
    val diagnosticMode = when (mode) {
        BrewMode.ESPRESSO -> DiagnosticBrewMode.ESPRESSO
        BrewMode.FILTER -> DiagnosticBrewMode.FILTER
        BrewMode.FRENCH_PRESS -> DiagnosticBrewMode.FRENCH_PRESS
    }
    recordEvent(DiagnosticEvent.BrewPhaseChanged(diagnosticMode, phase))
}
