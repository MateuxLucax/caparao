package io.github.mateuxlucax.caparao.data.diagnostics

import io.github.mateuxlucax.caparao.model.BrewMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
import java.io.RandomAccessFile
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class DiagnosticIncompleteReason(val note: String) {
    SCALE_DISCONNECTED("scale_disconnected"),
    SESSION_SIZE_LIMIT("session_size_limit"),
    SESSION_DURATION_LIMIT("session_duration_limit"),
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
        val sent: Boolean = false,
        val pending: Boolean = false,
    ) : DiagnosticRecorderState
    data class Failed(val reason: DiagnosticRecorderError) : DiagnosticRecorderState
}

/** Limits apply per installation. Starting a session reserves room for its full size cap. */
data class DiagnosticRecorderLimits(
    val maxSessionBytes: Long = MAX_SESSION_BYTES,
    val queueCapacity: Int = WRITER_QUEUE_CAPACITY,
    val maxNotificationBytes: Int = MAX_NOTIFICATION_BYTES,
    val footerReserveBytes: Int = FOOTER_RESERVE_BYTES,
    val maxDurationMillis: Long = MAX_DURATION_MILLIS,
) {
    init {
        require(maxSessionBytes > 0)
        require(queueCapacity > 0 && maxNotificationBytes > 0)
        require(footerReserveBytes > 0 && footerReserveBytes < maxSessionBytes)
        require(maxDurationMillis > 0)
    }

    companion object {
        const val MAX_SESSION_BYTES = 8L * 1024 * 1024
        const val WRITER_QUEUE_CAPACITY = 128
        const val MAX_NOTIFICATION_BYTES = 512
        const val FOOTER_RESERVE_BYTES = 512
        const val MAX_DURATION_MILLIS = 15 * 60 * 1000L
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
    private val optedIn: () -> Boolean = { true },
    private val send: suspend (File, Boolean, String) -> Boolean = { _, _, _ -> false },
    private val appVersion: String = "0.0-test",
    private val phoneModel: String = "synthetic",
    private val androidVersion: String = "test",
) : Diagnostics, DiagnosticCapture {
    private val lock = Any()
    private val lifecycleLock = kotlinx.coroutines.sync.Mutex()
    private val _state = MutableStateFlow<DiagnosticRecorderState>(DiagnosticRecorderState.Idle)
    override val state: StateFlow<DiagnosticRecorderState> = _state.asStateFlow()

    private var active: Session? = null
    private var finishing: Session? = null

    /** Start one new recording. Returns false when already recording or when storage cannot create a file. */
    override suspend fun start(): Boolean = lifecycleLock.withLock {
        if (!optedIn()) return@withLock false
        synchronized(lock) {
            if (active != null) return@withLock false
            _state.value = DiagnosticRecorderState.Starting
        }
        finishingSession()?.let { it.finished.await() }

        val (pending, validPendingFile) = withContext(ioDispatcher) {
            expireOldSessions()
            val latest = listSessionFiles().maxByOrNull(File::lastModified)
            latest to (latest == null || repairInterruptedFile(latest))
        }
        if (!validPendingFile) {
            synchronized(lock) { _state.value = DiagnosticRecorderState.Failed(DiagnosticRecorderError.STORAGE) }
            return@withLock false
        }
        if (pending != null) {
            val status = withContext(ioDispatcher) {
                readSessionStatus(pending).also { upload(pending, it.complete, readSessionMode(pending)) }
            }
            if (pending.exists()) {
                synchronized(lock) { _state.value = status.copy(pending = true) }
                return@withLock false
            }
        }

        val wallTime = wallClockMillis()
        val startNanos = monotonicNanos()
        val prepared = try {
            withContext(ioDispatcher) {
                prepareDirectory()
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
            enqueueLocked(session, Payload.Event(DiagnosticEvent.SessionStarted.note()))
        }
        scope.launch(ioDispatcher) { writeSession(session) }
        session.durationTimer = scope.launch {
            kotlinx.coroutines.delay(limits.maxDurationMillis)
            synchronized(lock) { if (active === session) markIncompleteLocked(session, DiagnosticIncompleteReason.SESSION_DURATION_LIMIT) }
        }
        true
    }

    /** Close the active file and wait for its bounded queue to drain; safe when called concurrently. */
    override suspend fun stop(): DiagnosticRecorderState {
        val session = synchronized(lock) {
            val current = active
            if (current != null) {
                if (current.pendingNotifications.isNotEmpty()) {
                    discardPendingNotificationsLocked(current)
                }
                finishLocked(current, current.incompleteReason)
                current
            } else {
                finishing
            }
        }
        session?.finished?.await()
        return state.value
    }

    /** Store one original GATT notification chunk, before the scale decoder can consume or transform it. */
    override fun notification(bytes: ByteArray, classification: DiagnosticFrameClassification) {
        synchronized(lock) {
            val session = active ?: return
            if (classification.discardPendingBytes) {
                discardPendingNotificationsLocked(session)
                session.droppingPartialFrame = false
            }
            if (bytes.isEmpty()) return
            if (bytes.size > limits.maxNotificationBytes) {
                markIncompleteLocked(session, DiagnosticIncompleteReason.SESSION_SIZE_LIMIT)
                return
            }
            if (session.droppingPartialFrame) {
                if (classification.completedFrames.isNotEmpty()) session.droppingPartialFrame = false
                if (classification.hasPartialFrame) session.droppingPartialFrame = true
                return
            }
            val line = PendingLine(session.nextSequence++, elapsedMsLocked(session), Payload.Raw("rx", bytes.copyOf(), "notification"))
            if (classification.completedFrames.isEmpty()) {
                if (classification.hasPartialFrame) {
                    session.pendingNotifications += line
                    session.heldLines += line
                    if (session.pendingNotifications.sumOf { (it.payload as Payload.Raw).bytes.size } > limits.maxNotificationBytes) {
                        discardPendingNotificationsLocked(session)
                        if (!flushHeldLocked(session)) finishLocked(session, session.incompleteReason)
                        session.droppingPartialFrame = true
                    }
                }
                return
            }
            session.pendingNotifications += line
            session.heldLines += line
            val allowed = classification.completedFrames.all { it == DiagnosticFrameType.WEIGHT || it == DiagnosticFrameType.BATTERY || it == DiagnosticFrameType.ACK }
            if (allowed && !classification.hasPartialFrame) session.pendingNotifications.clear()
            else if (!allowed) discardPendingNotificationsLocked(session)
            if (classification.hasPartialFrame) session.droppingPartialFrame = !allowed
            if (session.pendingNotifications.sumOf { (it.payload as Payload.Raw).bytes.size } > limits.maxNotificationBytes) {
                discardPendingNotificationsLocked(session)
                session.droppingPartialFrame = classification.hasPartialFrame
            }
            if (!classification.hasPartialFrame && !flushHeldLocked(session)) finishLocked(session, session.incompleteReason)
        }
    }

    /** Store a successful outgoing write. The caller passes a snapshot captured before handing bytes to Android BLE. */
    override fun command(bytes: ByteArray) {
        synchronized(lock) {
            val session = active ?: return
            if (bytes.size > limits.maxNotificationBytes) {
                markIncompleteLocked(session, DiagnosticIncompleteReason.SESSION_SIZE_LIMIT)
                return
            }
            enqueueLocked(session, Payload.Raw("tx", bytes.copyOf(), "command"))
        }
    }

    override fun event(event: DiagnosticEvent) {
        synchronized(lock) {
            val session = active ?: return
            val mode = (event as? DiagnosticEvent.BrewPhaseChanged)?.mode?.note
            if (mode != null) session.mode = mode
            enqueueLocked(session, Payload.Event(event.note(), mode))
            if (event == DiagnosticEvent.BrewResultReady || event == DiagnosticEvent.BrewLiftEnded) finishLocked(session, null)
        }
    }

    /** Logs the link drop and closes the session as incomplete. A deliberate app stop does not call this. */
    fun recordDisconnect() {
        event(DiagnosticEvent.ScaleLinkLost)
        disconnect()
    }

    override fun disconnect() {
        synchronized(lock) {
            val session = active ?: return
            markIncompleteLocked(session, DiagnosticIncompleteReason.SCALE_DISCONNECTED)
        }
    }

    /** The local JSONL has no exception entry; the Sentry implementation reports handled errors instead (#333). */
    override fun error(throwable: Throwable, context: DiagnosticErrorContext) = Unit

    /** Re-read the retained session list when Settings opens, including recovery after process death. */
    override suspend fun refresh() = withContext(ioDispatcher) {
        if (synchronized(lock) { active != null || finishing != null }) return@withContext
        if (!optedIn()) {
            listSessionFiles().forEach(File::delete)
            synchronized(lock) { if (active == null) _state.value = DiagnosticRecorderState.Idle }
            return@withContext
        }
        expireOldSessions()
        val latest = listSessionFiles().maxByOrNull(File::lastModified)
        val recovered = latest?.takeIf(::repairInterruptedFile)?.let(::readSessionStatus)
        synchronized(lock) {
            // A settings screen can request refresh just as recording starts. Do not overwrite the newer state.
            val current = _state.value
            val settled = current == DiagnosticRecorderState.Idle || current is DiagnosticRecorderState.Ready || current is DiagnosticRecorderState.Failed
            if (active == null && finishing == null && settled) {
                _state.value = recovered ?: if (latest == null) DiagnosticRecorderState.Idle else DiagnosticRecorderState.Failed(DiagnosticRecorderError.STORAGE)
            }
        }
        if (recovered != null) upload(recovered.file, recovered.complete, readSessionMode(recovered.file))
    }

    /** Remove unsent files immediately when consent is withdrawn. */
    override suspend fun clear() = withContext(ioDispatcher) {
        synchronized(lock) { active?.let { markIncompleteLocked(it, DiagnosticIncompleteReason.PROCESS_INTERRUPTED) } }
        finishingSession()?.finished?.await()
        listSessionFiles().forEach(File::delete)
        synchronized(lock) { if (active == null) _state.value = DiagnosticRecorderState.Idle }
    }

    private fun enqueueLocked(session: Session, payload: Payload) {
        enqueueAtLocked(session, elapsedMsLocked(session), payload)
    }

    private fun enqueueAtLocked(session: Session, atMs: Long, payload: Payload) {
        val line = PendingLine(session.nextSequence++, atMs, payload)
        if (session.pendingNotifications.isNotEmpty() || session.heldLines.isNotEmpty()) {
            session.heldLines += line
        } else {
            enqueueLineLocked(session, line)
        }
    }

    private fun enqueueLineLocked(session: Session, line: PendingLine) {
        if (session.channel.trySend(line).isFailure) {
            markIncompleteLocked(session, DiagnosticIncompleteReason.WRITER_OVERRUN)
        }
    }

    private fun discardPendingNotificationsLocked(session: Session) {
        val pending = session.pendingNotifications.toSet()
        session.heldLines.removeAll(pending)
        session.pendingNotifications.clear()
    }

    private fun flushHeldLocked(session: Session): Boolean {
        val held = session.heldLines.sortedBy(PendingLine::sequence)
        session.heldLines.clear()
        held.forEach { line ->
            if (session.channel.trySend(line).isFailure) {
                if (session.incompleteReason == null) session.incompleteReason = DiagnosticIncompleteReason.WRITER_OVERRUN
                return false
            }
        }
        return true
    }

    /** Caller holds [lock]. */
    private fun markIncompleteLocked(session: Session, reason: DiagnosticIncompleteReason) {
        if (session.incompleteReason == null) session.incompleteReason = reason
        finishLocked(session, session.incompleteReason)
    }

    private fun finishLocked(session: Session, reason: DiagnosticIncompleteReason?) {
        if (active !== session) return
        if (session.pendingNotifications.isNotEmpty()) {
            discardPendingNotificationsLocked(session)
        }
        flushHeldLocked(session)
        active = null
        session.durationTimer?.cancel()
        finishing = session
        if (session.incompleteReason == null && reason != null) session.incompleteReason = reason
        session.endingAtMs = elapsedMsLocked(session)
        session.channel.close()
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
        if (result is DiagnosticRecorderState.Ready && optedIn()) scope.launch(ioDispatcher) { upload(result.file, result.complete, session.mode) }
    }

    private suspend fun upload(file: File, complete: Boolean, mode: String = "unknown") {
        if (!optedIn()) { file.delete(); return }
        if (runCatching { send(file, complete, mode) }.getOrDefault(false)) {
            file.delete()
            synchronized(lock) {
                val current = _state.value as? DiagnosticRecorderState.Ready
                if (current?.file == file) _state.value = current.copy(sent = true, pending = false)
            }
        } else {
            synchronized(lock) {
                val current = _state.value as? DiagnosticRecorderState.Ready
                if (current?.file == file) _state.value = current.copy(pending = true)
            }
        }
    }

    private fun prepareDirectory() {
        if (!directory.exists() && !directory.mkdirs()) throw IOException("diagnostic directory")
        if (!directory.isDirectory) throw IOException("diagnostic directory")
    }

    private fun listSessionFiles(): List<File> = directory.listFiles()
        .orEmpty()
        .filter { it.isFile && it.name.startsWith(FILE_PREFIX) && it.name.endsWith(FILE_SUFFIX) }

    private fun expireOldSessions() {
        val cutoff = wallClockMillis() - MAX_PENDING_AGE_MILLIS
        listSessionFiles().filter { it.lastModified() < cutoff }.forEach(File::delete)
    }

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
            .put("app_version", appVersion)
            .put("phone_model", phoneModel)
            .put("android_version", androidVersion)
            .put("started_at", Instant.ofEpochMilli(wallTime).toString()),
    )

    private fun encode(line: PendingLine): ByteArray {
        val json = JSONObject()
            .put("t_ms", line.elapsedMs)
            .put("seq", line.sequence)
            .put("dir", line.payload.dir)
            .put("hex", line.payload.hex)
            .put("note", line.payload.note)
        line.payload.mode?.let { json.put("mode", it) }
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

    private fun readSessionMode(file: File): String = runCatching {
        file.useLines { lines -> lines.mapNotNull { line -> JSONObject(line).optString("mode").takeIf(String::isNotBlank) }.lastOrNull() }
    }.getOrNull() ?: "unknown"

    private fun repairInterruptedFile(file: File): Boolean {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return false
        var offset = 0
        var validEnd = 0
        var last: JSONObject? = null
        while (offset < bytes.size) {
            val newline = (offset until bytes.size).firstOrNull { bytes[it] == '\n'.code.toByte() } ?: -1
            val end = if (newline == -1) bytes.size else newline
            val record = runCatching {
                JSONObject(String(bytes, offset, end - offset, Charsets.UTF_8).trimEnd('\r'))
            }.getOrNull() ?: break
            last = record
            validEnd = if (newline == -1) end else newline + 1
            if (newline == -1) break
            offset = newline + 1
        }
        val finalRecord = last ?: return false
        if (validEnd < bytes.size && !runCatching {
                RandomAccessFile(file, "rw").use { it.setLength(validEnd.toLong()) }
            }.isSuccess
        ) return false
        if (finalRecord.optString("note") in setOf("session_end", "session_incomplete")) return true
        return runCatching {
            FileOutputStream(file, true).buffered().use { stream ->
                if (validEnd == bytes.size && validEnd > 0 && bytes[validEnd - 1] != '\n'.code.toByte()) stream.write('\n'.code)
                val footer = JSONObject()
                    .put("t_ms", finalRecord.optLong("t_ms"))
                    .put("seq", finalRecord.optLong("seq") + 1)
                    .put("dir", "event")
                    .put("hex", "")
                    .put("note", "session_incomplete")
                    .put("complete", false)
                    .put("reason", DiagnosticIncompleteReason.PROCESS_INTERRUPTED.note)
                stream.write((footer.toString() + "\n").toByteArray(Charsets.UTF_8))
            }
            true
        }.getOrDefault(false)
    }

    private fun finishingSession(): Session? = synchronized(lock) { finishing }

    private class Session(
        val file: File,
        val startedAtNanos: Long,
        val headerBytes: Long,
        val channel: Channel<PendingLine>,
    ) {
        val finished = CompletableDeferred<Unit>()
        var durationTimer: Job? = null
        var nextSequence = 0L
        var lastElapsedMs = 0L
        var endingAtMs: Long? = null
        var incompleteReason: DiagnosticIncompleteReason? = null
        var mode = "unknown"
        val pendingNotifications = mutableListOf<PendingLine>()
        val heldLines = mutableListOf<PendingLine>()
        var droppingPartialFrame = false
    }

    private sealed interface Payload {
        val dir: String
        val note: String
        val hex: String
        val mode: String? get() = null

        data class Event(override val note: String, override val mode: String? = null) : Payload {
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
        const val FORMAT_VERSION = 2
        private const val FILE_PREFIX = "diagnostic-"
        private const val FILE_SUFFIX = ".jsonl"
        private const val FLUSH_EVERY_LINES = 8
        private const val MAX_PENDING_AGE_MILLIS = 7 * 24 * 60 * 60 * 1000L
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS'Z'").withZone(ZoneOffset.UTC)
    }
}

/** Map every allowed app mode to a fixed token; user-entered recipe text never enters this event. */
fun Diagnostics.recordBrewPhase(mode: BrewMode, phase: DiagnosticBrewPhase) {
    val diagnosticMode = when (mode) {
        BrewMode.ESPRESSO -> DiagnosticBrewMode.ESPRESSO
        BrewMode.FILTER -> DiagnosticBrewMode.FILTER
        BrewMode.FRENCH_PRESS -> DiagnosticBrewMode.FRENCH_PRESS
    }
    event(DiagnosticEvent.BrewPhaseChanged(diagnosticMode, phase))
}
