package dev.mateux.caparao.data.diagnostics

import dev.mateux.caparao.data.scale.ReplayScaleSource
import dev.mateux.caparao.data.scale.FrameDecoder
import dev.mateux.caparao.data.scale.recordDecodedNotification
import dev.mateux.caparao.model.ScaleReading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureWriterTest {
    private val zeroWeight = bytes("A5 5A 01 01 00 09 00 00 00 00 00 00 00 00 00 00 00")
    private val thirtyGrams = bytes("A5 5A 01 01 00 09 00 00 01 2C 00 00 00 00 00 00 00")

    @Test
    fun jsonlReplaysRawChunksAndKeepsDuplicateReadingsAndPrebrewBaseline() = runTest {
        val directory = tempDir()
        var clock = 1_000_000_000L
        val recorder = recorder(directory, { clock }, this)
        assertTrue(recorder.start())

        val mutable = zeroWeight.copyOf()
        notifyWeight(recorder, mutable)
        mutable.fill(0)
        clock += 100_000_000L
        recorder.event(DiagnosticEvent.BrewScreenOpened)
        clock += 100_000_000L
        notifyWeight(recorder, thirtyGrams)
        clock += 100_000_000L
        notifyWeight(recorder, thirtyGrams)
        recorder.event(DiagnosticEvent.BrewPhaseChanged(DiagnosticBrewMode.ESPRESSO, DiagnosticBrewPhase.READY))

        val result = recorder.stop() as CaptureWriterState.Ready
        assertTrue(result.complete)
        val lines = result.file.readLines()
        val rows = lines.drop(1).map(::JSONObject)
        assertEquals(CaptureWriter.FORMAT_NAME, JSONObject(lines.first()).getString("format"))
        assertEquals("0.0-test", JSONObject(lines.first()).getString("app_version"))
        assertEquals("synthetic", JSONObject(lines.first()).getString("phone_model"))
        assertEquals("test", JSONObject(lines.first()).getString("android_version"))
        assertEquals(listOf("event", "rx", "event", "rx", "rx", "event", "event"), rows.map { it.getString("dir") })
        assertEquals("session_started", rows[0].getString("note"))
        assertEquals("A5 5A 01 01 00 09 00 00 00 00 00 00 00 00 00 00 00", rows[1].getString("hex"))
        assertEquals("notification", rows[1].getString("note"))
        assertEquals(listOf(thirtyGrams, thirtyGrams).map(::hex), rows.filter { it.getString("dir") == "rx" }.drop(1).map { it.getString("hex") })
        assertTrue(rows.first().getLong("t_ms") <= rows[1].getLong("t_ms"))
        assertEquals(listOf(0L, 0L, 100L, 200L, 300L, 300L, 300L), rows.map { it.getLong("t_ms") })

        recorder.clear()
        val replay = ReplayScaleSource(this, lines, playbackSpeed = 100.0, repeat = false)
        val readings = async(UnconfinedTestDispatcher(testScheduler)) { replay.readings.take(3).toList() }
        replay.start()
        advanceUntilIdle()
        assertEquals(
            listOf(ScaleReading(0.0, 0.0, 0, false), ScaleReading(30.0, 0.0, 0, false), ScaleReading(30.0, 0.0, 0, false)),
            readings.await(),
        )
        directory.deleteRecursively()
    }

    @Test
    fun commandsEventsAndNotificationsShareOneMonotonicClockAndOrder() = runTest {
        val directory = tempDir()
        var clock = 4_000_000_000L
        val recorder = recorder(directory, { clock }, this)
        assertTrue(recorder.start())
        clock += 5_000_000L
        recorder.event(DiagnosticEvent.ScanStarted)
        clock += 5_000_000L
        notifyWeight(recorder, zeroWeight)
        clock += 5_000_000L
        recorder.command(bytes("A5 5A 03 0D 00 00 64 D1"))
        clock += 5_000_000L
        recorder.event(DiagnosticEvent.AppForeground)

        val file = (recorder.stop() as CaptureWriterState.Ready).file
        val rows = file.readLines().drop(1).map(::JSONObject)
        assertEquals(listOf("session_started", "scan_started", "notification", "command", "app_foreground", "session_end"), rows.map { it.getString("note") })
        assertEquals(listOf(0L, 5L, 10L, 15L, 20L, 20L), rows.map { it.getLong("t_ms") })
        assertEquals(listOf(0L, 1L, 2L, 3L, 4L, 5L), rows.map { it.getLong("seq") })
        assertTrue(rows.zipWithNext().all { (left, right) -> left.getLong("t_ms") <= right.getLong("t_ms") })
        recorder.clear()
        directory.deleteRecursively()
    }

    @Test
    fun writerOverrunProducesAnExplicitIncompleteFooter() = runTest {
        val directory = tempDir()
        val recorder = recorder(directory, { 1_000_000_000L }, this, limits = limits(queueCapacity = 2))
        assertTrue(recorder.start()) // session_started uses one queue slot before the writer is scheduled
        notifyWeight(recorder, zeroWeight)
        notifyWeight(recorder, zeroWeight) // fills the bounded queue and stops capture

        val result = recorder.stop() as CaptureWriterState.Ready
        assertFalse(result.complete)
        assertEquals(CaptureIncompleteReason.WRITER_OVERRUN, result.reason)
        val footer = JSONObject(result.file.readLines().last())
        assertEquals("session_incomplete", footer.getString("note"))
        assertEquals("writer_overrun", footer.getString("reason"))
        recorder.clear()
        directory.deleteRecursively()
    }

    @Test
    fun heldLineFlushOverrunKeepsTheOriginalIncompleteReason() = runTest {
        val directory = tempDir()
        val recorder = recorder(directory, { 1_000_000_000L }, this, limits = limits(queueCapacity = 2))
        assertTrue(recorder.start())
        recorder.notification(zeroWeight.copyOfRange(0, 5), DiagnosticFrameClassification(emptyList(), true))
        recorder.event(DiagnosticEvent.AppForeground)
        recorder.event(DiagnosticEvent.ScaleLinkLost)
        recorder.disconnect()
        runCurrent()

        val result = recorder.state.value as CaptureWriterState.Ready
        assertFalse(result.complete)
        assertEquals(CaptureIncompleteReason.SCALE_DISCONNECTED, result.reason)
        assertEquals("scale_disconnected", JSONObject(result.file.readLines().last()).getString("reason"))
        recorder.clear()
        directory.deleteRecursively()
    }

    @Test
    fun sessionDurationLimitFinishesAnIncompleteCapture() = runTest {
        val directory = tempDir()
        val recorder = recorder(directory, { 1_000_000_000L }, this, limits = CaptureWriterLimits(maxDurationMillis = 100))
        assertTrue(recorder.start())
        advanceTimeBy(100)
        runCurrent()

        val ready = recorder.state.value as CaptureWriterState.Ready
        assertFalse(ready.complete)
        assertEquals(CaptureIncompleteReason.SESSION_DURATION_LIMIT, ready.reason)
        recorder.clear()
        directory.deleteRecursively()
    }

    @Test
    fun gzipFetchConverterOutputReplaysWithOriginalChunkBoundaries() = runTest {
        val directory = tempDir()
        val recorder = recorder(directory, { 1_000_000_000L }, this)
        assertTrue(recorder.start())
        val split = zeroWeight.size / 2
        val firstChunk = zeroWeight.copyOfRange(0, split)
        val secondChunk = zeroWeight.copyOfRange(split, zeroWeight.size)
        recorder.notification(firstChunk, DiagnosticFrameClassification(emptyList(), true))
        recorder.notification(secondChunk, DiagnosticFrameClassification(listOf(DiagnosticFrameType.WEIGHT), false))
        recorder.notification(thirtyGrams, DiagnosticFrameClassification(listOf(DiagnosticFrameType.WEIGHT), false))
        recorder.notification(thirtyGrams, DiagnosticFrameClassification(listOf(DiagnosticFrameType.WEIGHT), false))
        recorder.event(DiagnosticEvent.BrewResultReady)

        val captured = (recorder.stop() as CaptureWriterState.Ready).file
        val compressed = File(directory, "capture.jsonl.gz")
        GZIPOutputStream(FileOutputStream(compressed)).use { output -> FileInputStream(captured).use { it.copyTo(output) } }
        val converted = File(directory, "converted.jsonl")
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "scripts/capture/fetch-capture.py").isFile }
        val converter = """
            import importlib.util, pathlib, sys
            spec = importlib.util.spec_from_file_location('fetch_capture', sys.argv[1])
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            pathlib.Path(sys.argv[3]).write_text(module.convert_capture(pathlib.Path(sys.argv[2]).read_bytes()))
        """.trimIndent()
        val process = ProcessBuilder("python3", "-c", converter, File(root, "scripts/capture/fetch-capture.py").path, compressed.path, converted.path)
            .redirectErrorStream(true)
            .start()
        val processOutput = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(processOutput, 0, process.waitFor())
        val lines = converted.readLines()
        val rx = lines.drop(1).map(::JSONObject).filter { it.optString("dir") == "rx" }
        assertEquals(listOf(firstChunk, secondChunk, thirtyGrams, thirtyGrams).map(::hex), rx.map { it.getString("hex") })

        recorder.clear()
        val replay = ReplayScaleSource(this, lines, playbackSpeed = 100.0, repeat = false)
        val readings = async(UnconfinedTestDispatcher(testScheduler)) { replay.readings.take(3).toList() }
        replay.start()
        advanceUntilIdle()
        assertEquals(
            listOf(ScaleReading(0.0, 0.0, 0, false), ScaleReading(30.0, 0.0, 0, false), ScaleReading(30.0, 0.0, 0, false)),
            readings.await(),
        )
        directory.deleteRecursively()
    }

    @Test
    fun fragmentedFramesKeepOriginalChunksAndUnsupportedRepliesAreDroppedWithoutStoppingCapture() = runTest {
        val directory = tempDir()
        val recorder = recorder(directory, { 1_000_000_000L }, this)
        assertTrue(recorder.start())
        val split = zeroWeight.size / 2
        recorder.notification(zeroWeight.copyOfRange(0, split), DiagnosticFrameClassification(emptyList(), true))
        recorder.notification(zeroWeight.copyOfRange(split, zeroWeight.size), DiagnosticFrameClassification(listOf(DiagnosticFrameType.WEIGHT), false))
        val ready = recorder.stop() as CaptureWriterState.Ready
        val rx = ready.file.readLines().drop(1).map(::JSONObject).filter { it.optString("dir") == "rx" }
        assertEquals(listOf(hex(zeroWeight.copyOfRange(0, split)), hex(zeroWeight.copyOfRange(split, zeroWeight.size))), rx.map { it.getString("hex") })

        val unsupported = listOf(
            DiagnosticFrameType.MODEL to bytes("A5 5A 02 13 00 03 41 42 43 00 00"),
            DiagnosticFrameType.UNKNOWN to bytes("A5 5A 77 77 00 03 DE AD BE 00 00"),
        )
        unsupported.forEachIndexed { index, (type, reply) ->
            val filtered = recorder(File(directory, "unsupported-$index"), { 1_000_000_000L }, this)
            assertTrue(filtered.start())
            filtered.notification(reply, DiagnosticFrameClassification(listOf(type), false))
            notifyWeight(filtered, zeroWeight)
            filtered.event(DiagnosticEvent.BrewResultReady)
            val result = filtered.stop() as CaptureWriterState.Ready
            assertTrue(result.complete)
            assertFalse(result.file.readText().contains(hex(reply)))
            assertTrue(result.file.readText().contains(hex(zeroWeight)))
            filtered.clear()
        }
        recorder.clear()
        directory.deleteRecursively()
    }

    @Test
    fun mixedModelAndWeightChunksKeepOnlyTheSupportedFrameBytes() = runTest {
        val directory = tempDir()
        val recorder = recorder(directory, { 1_000_000_000L }, this)
        val decoder = FrameDecoder()
        val model = bytes("A5 5A 02 13 00 03 41 42 43 00 00")
        val split = 7
        assertTrue(recorder.start())

        recordDecodedNotification(decoder, recorder, model + zeroWeight.copyOfRange(0, split))
        recordDecodedNotification(decoder, recorder, zeroWeight.copyOfRange(split, zeroWeight.size))
        recorder.event(DiagnosticEvent.BrewResultReady)

        val result = recorder.stop() as CaptureWriterState.Ready
        val rows = result.file.readLines().drop(1).map(::JSONObject)
        val rx = rows.filter { it.optString("dir") == "rx" }
        assertEquals(listOf(zeroWeight.copyOfRange(0, split), zeroWeight.copyOfRange(split, zeroWeight.size)).map(::hex), rx.map { it.getString("hex") })
        assertFalse(result.file.readText().contains(hex(model)))
        assertTrue(rows.zipWithNext().all { (left, right) -> left.getLong("seq") < right.getLong("seq") })
        assertTrue(rows.zipWithNext().all { (left, right) -> left.getLong("t_ms") <= right.getLong("t_ms") })
        recorder.clear()
        directory.deleteRecursively()
    }

    @Test
    fun aSupportedFrameSplitInsideItsHeaderKeepsTheBufferedPrefix() = runTest {
        val directory = tempDir()
        val recorder = recorder(directory, { 1_000_000_000L }, this)
        val decoder = FrameDecoder()
        val model = bytes("A5 5A 02 13 00 03 41 42 43 00 00")
        assertTrue(recorder.start())

        recordDecodedNotification(decoder, recorder, zeroWeight.copyOfRange(0, 1))
        recordDecodedNotification(decoder, recorder, zeroWeight.copyOfRange(1, zeroWeight.size) + model)
        recorder.event(DiagnosticEvent.BrewResultReady)

        val result = recorder.stop() as CaptureWriterState.Ready
        val rows = result.file.readLines().drop(1).map(::JSONObject)
        val rx = rows.filter { it.optString("dir") == "rx" }
        assertEquals(
            listOf(zeroWeight.copyOfRange(0, 1), zeroWeight.copyOfRange(1, zeroWeight.size)).map(::hex),
            rx.map { it.getString("hex") },
        )
        assertFalse(result.file.readText().contains(hex(model)))
        assertTrue(rows.zipWithNext().all { (left, right) -> left.getLong("seq") < right.getLong("seq") })
        assertTrue(rows.zipWithNext().all { (left, right) -> left.getLong("t_ms") <= right.getLong("t_ms") })
        recorder.clear()
        directory.deleteRecursively()
    }

    @Test
    fun unsupportedSplitFrameDropDoesNotReorderHeldRecords() = runTest {
        val directory = tempDir()
        val recorder = recorder(directory, { 1_000_000_000L }, this)
        val decoder = FrameDecoder()
        val model = bytes("A5 5A 02 13 00 03 41 42 43 00 00")
        assertTrue(recorder.start())

        recordDecodedNotification(decoder, recorder, model.copyOfRange(0, 2))
        recorder.event(DiagnosticEvent.AppForeground)
        recordDecodedNotification(decoder, recorder, model.copyOfRange(2, model.size))
        recorder.command(bytes("A5 5A 03 0D 00 00 64 D1"))
        recorder.event(DiagnosticEvent.BrewResultReady)

        val result = recorder.stop() as CaptureWriterState.Ready
        val rows = result.file.readLines().drop(1).map(::JSONObject)
        assertEquals(listOf("session_started", "app_foreground", "command", "brew_result_ready", "session_end"), rows.map { it.getString("note") })
        assertTrue(rows.zipWithNext().all { (left, right) -> left.getLong("seq") < right.getLong("seq") })
        assertFalse(result.file.readText().contains(hex(model)))
        recorder.clear()
        directory.deleteRecursively()
    }

    @Test
    fun refreshTruncatesATornFinalLineBeforeSendingTheRecoveredFile() = runTest {
        val directory = tempDir()
        val file = File(directory, "capture-interrupted.jsonl")
        val header = JSONObject()
            .put("format", CaptureWriter.FORMAT_NAME)
            .put("format_version", CaptureWriter.FORMAT_VERSION)
            .put("app_version", "test")
            .put("phone_model", "synthetic")
            .put("android_version", "test")
            .put("started_at", "2026-01-01T00:00:00Z")
        val row = JSONObject().put("t_ms", 0).put("seq", 0).put("dir", "event").put("hex", "").put("note", "session_started")
        file.writeText("${header}\n${row}\n{\"t_ms\":1,\"seq\":1,\"dir\":\"rx\",\"hex\":\"A5")
        var sent = false
        var allLinesValid = false
        val recovered = CaptureWriter(
            directory,
            this,
            ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            send = { uploaded, _, _ ->
                sent = uploaded == file
                allLinesValid = uploaded.readLines().all { runCatching { JSONObject(it) }.isSuccess }
                false
            },
        )

        recovered.refresh()
        runCurrent()

        assertTrue(sent)
        assertTrue(allLinesValid)
        val footer = JSONObject(file.readLines().last())
        assertEquals("session_incomplete", footer.getString("note"))
        assertEquals("process_interrupted", footer.getString("reason"))
        assertEquals(1L, footer.getLong("seq"))
        recovered.clear()
        directory.deleteRecursively()
    }

    @Test
    fun refreshDoesNotSendAFileItCannotRepair() = runTest {
        val directory = tempDir()
        val file = File(directory, "capture-broken.jsonl")
        file.writeText("{\"t_ms\":1,\"seq\":0,\"dir\":\"rx\"")
        var sent = false
        val recorder = CaptureWriter(
            directory,
            this,
            ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            send = { _, _, _ ->
                sent = true
                true
            },
        )

        recorder.refresh()
        advanceUntilIdle()

        assertFalse(sent)
        assertEquals(CaptureWriterState.Failed(CaptureWriterError.STORAGE), recorder.state.value)
        assertTrue(file.exists())
        directory.deleteRecursively()
    }

    @Test
    fun sessionSizeLimitKeepsTheFileBoundedAndMarksItIncomplete() = runTest {
        val directory = tempDir()
        val recorder = recorder(
            directory,
            { 1_000_000_000L },
            this,
            limits = limits(maxSessionBytes = 1_024, footerReserveBytes = 384),
        )
        assertTrue(recorder.start())
        notifyWeight(recorder, ByteArray(220) { it.toByte() })

        val result = recorder.stop() as CaptureWriterState.Ready
        assertFalse(result.complete)
        assertEquals(CaptureIncompleteReason.SESSION_SIZE_LIMIT, result.reason)
        assertTrue(result.file.length() <= 1_024L)
        val footer = JSONObject(result.file.readLines().last())
        assertEquals("session_size_limit", footer.getString("reason"))
        recorder.clear()
        directory.deleteRecursively()
    }

    @Test
    fun storageErrorsFailStartAndMarkWriterFailuresIncomplete() = runTest {
        val root = tempDir()
        val notDirectory = File(root, "file").apply { writeText("occupied") }
        val cannotStart = recorder(notDirectory.resolve("diagnostics"), { 0L }, this)
        assertFalse(cannotStart.start())
        assertEquals(CaptureWriterState.Failed(CaptureWriterError.STORAGE), cannotStart.state.value)

        var appendFails = false
        val output: (File, Boolean) -> OutputStream = { file, append ->
            if (append && appendFails) throw IOException("private exception text")
            java.io.FileOutputStream(file, append)
        }
        val brokenWriter = CaptureWriter(
            File(root, "writer"), this, monotonicNanos = { 0L },
            ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            outputStream = output,
        )
        assertTrue(brokenWriter.start())
        appendFails = true
        brokenWriter.event(DiagnosticEvent.AppForeground)
        val result = brokenWriter.stop() as CaptureWriterState.Ready
        assertFalse(result.complete)
        assertEquals(CaptureIncompleteReason.STORAGE_ERROR, result.reason)
        assertFalse(result.file.readText().contains("private exception text"))
        brokenWriter.clear()
        root.deleteRecursively()
    }

    @Test
    fun anUnsentCaptureRetriesWithoutBlockingAnotherCapture() = runTest {
        val directory = tempDir()
        var wall = 1_700_000_000_000L
        var monotonic = 1_000_000_000L
        val recorder = CaptureWriter(
            directory, this, monotonicNanos = { monotonic }, wallClockMillis = { wall },
            ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            limits = limits(maxSessionBytes = 4_096),
        )
        assertTrue(recorder.start())
        notifyWeight(recorder, zeroWeight)
        val pending = (recorder.stop() as CaptureWriterState.Ready).file
        wall += 1_000L
        monotonic += 1_000_000_000L
        assertTrue(recorder.start())
        val current = (recorder.stop() as CaptureWriterState.Ready).file
        val files = directory.listFiles().orEmpty().filter { it.extension == "jsonl" }
        assertEquals(2, files.size)
        assertTrue(pending.exists())
        assertTrue(current.exists())
        assertTrue(files.all { it.length() <= 4_096L })
        assertTrue(files.sumOf(File::length) <= 8_192L)
        recorder.clear()
        directory.deleteRecursively()
    }

    @Test
    fun failedCaptureUploadRetriesInTheBackground() = runTest {
        val directory = tempDir()
        var attempts = 0
        val recorder = CaptureWriter(
            directory,
            this,
            ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            send = { _, _, _ -> ++attempts > 1 },
        )
        assertTrue(recorder.start())
        val ready = recorder.stop() as CaptureWriterState.Ready
        runCurrent()
        assertEquals(1, attempts)
        assertTrue(ready.file.exists())
        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals(2, attempts)
        assertFalse(ready.file.exists())
        assertEquals(0, recorder.history.value.pendingUploads)
        assertTrue((recorder.state.value as CaptureWriterState.Ready).sent)
        recorder.clear()
        directory.deleteRecursively()
    }

    @Test
    fun failedCaptureKeepsRetryingAfterTheFirstBackoffWindow() = runTest {
        val directory = tempDir()
        var attempts = 0
        val recorder = CaptureWriter(
            directory,
            this,
            ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            send = { _, _, _ -> attempts++; false },
        )
        assertTrue(recorder.start())
        recorder.stop()
        runCurrent()
        assertEquals(1, attempts)

        advanceTimeBy(16 * 60 * 1_000L)
        runCurrent()

        assertTrue("retry continues after the 7.5 minute window", attempts >= 6)
        recorder.clear()
        directory.deleteRecursively()
    }

    @Test
    fun everyBrewStartsAndEndsItsOwnAutomaticCapture() = runTest {
        val directory = tempDir()
        var enabled = true
        val recorder = CaptureWriter(
            directory,
            this,
            ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            optedIn = { enabled },
            captureEveryBrew = { enabled },
        )

        recorder.event(DiagnosticEvent.BrewPhaseChanged(DiagnosticBrewMode.FILTER, DiagnosticBrewPhase.BREWING))
        runCurrent()
        assertTrue(recorder.state.value is CaptureWriterState.Recording)
        recorder.event(DiagnosticEvent.BrewResultReady)
        runCurrent()
        val sent = recorder.state.value as CaptureWriterState.Ready
        assertTrue(sent.complete)
        assertTrue(sent.file.readText().contains("brew_filter_brewing"))
        assertTrue(sent.file.readText().contains("brew_result_ready"))
        recorder.clear()
        directory.deleteRecursively()
    }

    @Test
    fun theBrewingMarkerPrecedesTheFirstFrame() = runTest {
        val directory = tempDir()
        var enabled = true
        val recorder = CaptureWriter(
            directory,
            this,
            ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            optedIn = { enabled },
            captureEveryBrew = { enabled },
        )

        recorder.event(DiagnosticEvent.BrewPhaseChanged(DiagnosticBrewMode.FILTER, DiagnosticBrewPhase.BREWING))
        runCurrent() // The session is published with the marker already enqueued.
        recorder.notification(byteArrayOf(1, 2, 3), DiagnosticFrameClassification(false, false))
        recorder.event(DiagnosticEvent.BrewResultReady)
        runCurrent()
        val sent = recorder.state.value as CaptureWriterState.Ready
        val lines = sent.file.readLines().filter { it.isNotBlank() }
        val marker = lines.indexOfFirst { it.contains("brew_filter_brewing") }
        val frame = lines.indexOfFirst { it.contains("\"note\":\"notification\"") }
        assertTrue("brewing marker missing", marker != -1)
        assertTrue("notification frame missing", frame != -1)
        assertTrue("marker at $marker must precede the frame at $frame", marker < frame)
        recorder.clear()
        directory.deleteRecursively()
    }

    @Test
    fun restartingABrewEndsItsCaptureBeforeTheNextOneStarts() = runTest {
        val directory = tempDir()
        val sent = mutableListOf<String>()
        val recorder = CaptureWriter(
            directory,
            this,
            ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            captureEveryBrew = { true },
            send = { file, _, _ -> sent += file.readText(); true },
        )

        recorder.event(DiagnosticEvent.BrewPhaseChanged(DiagnosticBrewMode.FILTER, DiagnosticBrewPhase.BREWING))
        runCurrent()
        recorder.event(DiagnosticEvent.BrewInterrupted)
        runCurrent()
        recorder.event(DiagnosticEvent.BrewPhaseChanged(DiagnosticBrewMode.FILTER, DiagnosticBrewPhase.BREWING))
        runCurrent()
        recorder.event(DiagnosticEvent.BrewResultReady)
        runCurrent()

        assertEquals(2, sent.size)
        assertTrue(sent[0].contains("brew_interrupted"))
        assertTrue(sent[0].contains("\"note\":\"session_end\""))
        assertFalse(sent[0].contains("brew_result_ready"))
        assertTrue(sent[1].contains("brew_filter_brewing"))
        assertTrue(sent[1].contains("brew_result_ready"))
        recorder.clear()
        directory.deleteRecursively()
    }

    @Test
    fun fileSweepWaitsUntilANewSessionIsPublishedAsActive() = runTest {
        val directory = tempDir()
        val headerStarted = CompletableDeferred<File>()
        val allowHeaderToFinish = CompletableDeferred<Unit>()
        val holdNextHeader = AtomicBoolean(false)
        val sentFiles = CopyOnWriteArrayList<File>()
        var enabled = true
        val recorder = CaptureWriter(
            directory,
            this,
            ioDispatcher = Dispatchers.IO,
            limits = CaptureWriterLimits(maxDurationMillis = Long.MAX_VALUE),
            optedIn = { enabled },
            outputStream = { file, append ->
                val output = FileOutputStream(file, append)
                if (append || !holdNextHeader.compareAndSet(true, false)) output else object : OutputStream() {
                    override fun write(value: Int) = output.write(value)
                    override fun write(bytes: ByteArray, offset: Int, length: Int) = output.write(bytes, offset, length)
                    override fun flush() = output.flush()
                    override fun close() {
                        output.close()
                        runBlocking {
                            headerStarted.complete(file)
                            allowHeaderToFinish.await()
                        }
                    }
                }
            },
            send = { file, _, _ -> sentFiles += file; true },
        )
        holdNextHeader.set(true)
        val starting = async { recorder.start() }
        val inProgress = headerStarted.await()
        val refreshing = async { recorder.refresh() }
        runCurrent()

        allowHeaderToFinish.complete(Unit)
        assertTrue(starting.await())
        refreshing.await()
        runCurrent()
        assertTrue(recorder.state.value is CaptureWriterState.Recording)
        assertFalse("refresh left the active writer available for another start", recorder.start())
        assertFalse(sentFiles.contains(inProgress))
        assertTrue(inProgress.exists())
        assertTrue(sentFiles.isEmpty())
        // Disable consent so clearing the live session does not schedule its normal upload.
        enabled = false
        recorder.clear()
        directory.deleteRecursively()
    }

    @Test
    fun concurrentStopWritesOneFooterAndLeavesACompleteSession() = runTest {
        val directory = tempDir()
        val recorder = recorder(directory, { 1_000_000_000L }, this)
        assertTrue(recorder.start())
        notifyWeight(recorder, zeroWeight)
        val stops = List(8) { async { recorder.stop() } }
        runCurrent()
        val results = stops.map { it.await() as CaptureWriterState.Ready }
        assertTrue(results.all { it.complete })
        val lines = results.first().file.readLines()
        assertEquals(1, lines.drop(1).count { JSONObject(it).optString("note") == "session_end" })
        recorder.clear()
        directory.deleteRecursively()
    }

    @Test
    fun confirmedSendDeletesCaptureAndOptOutDeletesPendingCapture() = runTest {
        val directory = tempDir()
        var enabled = true
        val sent = mutableListOf<Triple<Boolean, String, Long>>()
        val recorder = CaptureWriter(
            directory, this, monotonicNanos = { 0L },
            ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            optedIn = { enabled },
            send = { file, complete, mode -> sent += Triple(complete, mode, file.length()); true },
        )
        assertTrue(recorder.start())
        recorder.event(DiagnosticEvent.BrewPhaseChanged(DiagnosticBrewMode.ESPRESSO, DiagnosticBrewPhase.READY))
        val ready = recorder.stop() as CaptureWriterState.Ready
        val bytes = ready.file.length()
        runCurrent()
        assertEquals(listOf(Triple(true, "espresso", bytes)), sent)
        assertFalse(ready.file.exists())
        assertTrue((recorder.state.value as CaptureWriterState.Ready).sent)

        val offline = CaptureWriter(
            File(directory, "offline"), this, monotonicNanos = { 0L },
            ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            optedIn = { enabled }, send = { _, _, _ -> false },
        )
        assertTrue(offline.start())
        val pending = (offline.stop() as CaptureWriterState.Ready).file
        runCurrent()
        assertTrue((offline.state.value as CaptureWriterState.Ready).pending)
        enabled = false
        offline.clear()
        assertFalse(pending.exists())

        var calls = 0
        val disabled = CaptureWriter(
            File(directory, "disabled"), this, optedIn = { false },
            send = { _, _, _ -> calls++; true },
        )
        assertFalse(disabled.start())
        assertEquals(0, calls)
        directory.deleteRecursively()
    }

    @Test
    fun brewCompletionStopsCaptureAutomatically() = runTest {
        val directory = tempDir()
        val recorder = recorder(directory, { 1_000_000_000L }, this)
        assertTrue(recorder.start())
        recorder.event(DiagnosticEvent.BrewResultReady)
        runCurrent()
        val ready = recorder.state.value as CaptureWriterState.Ready
        assertTrue(ready.complete)
        assertEquals("brew_result_ready", JSONObject(ready.file.readLines().dropLast(1).last()).getString("note"))
        recorder.clear()
        directory.deleteRecursively()
    }

    @Test
    fun liftEndStopsCaptureAutomatically() = runTest {
        val directory = tempDir()
        val recorder = recorder(directory, { 1_000_000_000L }, this)
        assertTrue(recorder.start())
        recorder.event(DiagnosticEvent.BrewLiftEnded)
        val ready = recorder.stop() as CaptureWriterState.Ready
        assertTrue(ready.complete)
        assertEquals("brew_lift_end", JSONObject(ready.file.readLines().dropLast(1).last()).getString("note"))
        recorder.clear()
        directory.deleteRecursively()
    }

    @Test
    fun retainedInterruptedSessionIsRecoverableAndMetadataContainsNoIdentifiers() = runTest {
        val directory = tempDir()
        val recorder = recorder(directory, { 1_000_000_000L }, this)
        assertTrue(recorder.start())
        recorder.event(DiagnosticEvent.ScaleConnected)
        notifyWeight(recorder, zeroWeight)
        recorder.recordDisconnect()
        val incomplete = recorder.stop() as CaptureWriterState.Ready
        assertFalse(incomplete.complete)

        val output = incomplete.file.readText()
        assertTrue(incomplete.file.name.startsWith("capture-"))
        assertFalse(output.contains("AA:BB:CC:DD:EE:FF"))
        assertFalse(output.contains("TIMEMORE_Dot"))
        assertFalse(output.contains("TES017"))
        assertFalse(output.contains("coffee.example.com"))
        assertFalse(output.contains("dev-token"))

        val recovered = CaptureWriter(directory, this, ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        recovered.refresh()
        val state = recovered.state.value as CaptureWriterState.Ready
        assertFalse(state.complete)
        assertEquals(CaptureIncompleteReason.SCALE_DISCONNECTED, state.reason)
        recorder.clear()
        recovered.clear()
        directory.deleteRecursively()
    }

    private fun recorder(
        directory: File,
        clock: () -> Long,
        scope: kotlinx.coroutines.CoroutineScope,
        limits: CaptureWriterLimits = limits(),
    ) = CaptureWriter(
        directory,
        scope,
        monotonicNanos = clock,
        wallClockMillis = { 1_700_000_000_000L },
        ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testSchedulerFor(scope)),
        limits = limits,
    )

    private fun limits(
        maxSessionBytes: Long = 8L * 1024 * 1024,
        queueCapacity: Int = 128,
        maxNotificationBytes: Int = 512,
        footerReserveBytes: Int = 512,
    ) = CaptureWriterLimits(maxSessionBytes, queueCapacity, maxNotificationBytes, footerReserveBytes)

    private fun tempDir(): File = Files.createTempDirectory("caparao-diagnostics-test-").toFile()

    private fun bytes(hex: String) = hex.split(' ').map { it.toInt(16).toByte() }.toByteArray()

    private fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private fun notifyWeight(recorder: CaptureWriter, bytes: ByteArray) = recorder.notification(
        bytes,
        DiagnosticFrameClassification(listOf(DiagnosticFrameType.WEIGHT), false),
    )

    /** All tests pass the active TestScope, whose scheduler is shared with the recorder's writer dispatcher. */
    private fun testSchedulerFor(scope: kotlinx.coroutines.CoroutineScope) =
        (scope.coroutineContext[kotlin.coroutines.ContinuationInterceptor] as? kotlinx.coroutines.test.TestDispatcher)?.scheduler
            ?: error("Test scope must use a TestDispatcher")
}
