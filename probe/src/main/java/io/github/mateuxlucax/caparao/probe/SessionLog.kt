package io.github.mateuxlucax.caparao.probe

import android.content.Context
import android.os.Build
import android.os.SystemClock
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One JSONL file per recorded session in `getExternalFilesDir("sessions")`, so `adb pull` can reach it without root.
 *
 * The repository will be public and these files become test fixtures: callers must never pass a MAC address or the
 * device name, here or in `note`. Every line is flushed immediately so a crash or a dead battery keeps the data.
 */
class SessionLog(private val context: Context) {
    private var writer: BufferedWriter? = null
    private var startedAt = 0L

    val active: Boolean get() = writer != null

    /** Opens `<yyyyMMdd-HHmmss>-<label>.jsonl` and writes the header line. Returns the file name. */
    fun start(label: String): String {
        stop()
        val dir = context.getExternalFilesDir("sessions") ?: error("no external files dir")
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault()).format(Instant.now())
        val slug = label.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "session" }
        val file = File(dir, "$stamp-$slug.jsonl")
        writer = file.bufferedWriter()
        startedAt = SystemClock.elapsedRealtime()
        writeLine(
            JSONObject()
                .put("label", label)
                .put("started_at", Instant.now().toString())
                .put("app", "probe")
                .put("phone", Build.MODEL)
                .put("android_sdk", Build.VERSION.SDK_INT),
        )
        return file.name
    }

    /** `dir` is "rx", "tx" or "event"; `hex` is empty for events. */
    fun write(dir: String, hex: String, note: String) {
        if (writer == null) return
        writeLine(
            JSONObject()
                .put("t_ms", SystemClock.elapsedRealtime() - startedAt)
                .put("dir", dir)
                .put("hex", hex)
                .put("note", note),
        )
    }

    fun stop() {
        writer?.close()
        writer = null
    }

    private fun writeLine(json: JSONObject) {
        writer?.apply {
            write(json.toString())
            newLine()
            flush()
        }
    }
}
