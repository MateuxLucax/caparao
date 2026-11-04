package io.github.mateuxlucax.caparao.data.scale

import io.github.mateuxlucax.caparao.model.ScaleState
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

/** Small, loopback-only control surface. This class only exists in the debug variant. */
class ScriptedScaleHttpServer(private val source: ScriptedScaleSource) {
    private val socket = ServerSocket(PORT, 8, InetAddress.getByName("127.0.0.1"))

    init {
        Thread({
            while (!socket.isClosed) {
                try {
                    socket.accept().use(::handle)
                } catch (_: Exception) {
                    if (!socket.isClosed) continue
                }
            }
        }, "scripted-scale-http").apply { isDaemon = true; start() }
    }

    private fun handle(client: Socket) {
        try {
            client.soTimeout = 5_000
            val input = client.getInputStream()
            val request = readLine(input).split(' ', limit = 3)
            if (request.size < 2) return respond(client, 400, error("malformed request"))
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input)
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0) headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
            }
            val length = headers["content-length"]?.toIntOrNull() ?: 0
            if (length > MAX_BODY_BYTES) return respond(client, 413, error("request body is too large"))
            val bytes = ByteArray(length)
            var received = 0
            while (received < length) {
                val count = input.read(bytes, received, length - received)
                if (count < 0) break
                received += count
            }
            val body = String(bytes, 0, received, StandardCharsets.UTF_8)
            val (status, result) = route(request[0], request[1], headers["content-type"].orEmpty(), body)
            respond(client, status, result)
        } catch (failure: Throwable) {
            runCatching { respond(client, 400, error(failure.message ?: "invalid request")) }
        }
    }

    private fun route(method: String, path: String, contentType: String, body: String): Pair<Int, JSONObject> = try {
        when {
            method == "GET" && path == "/state" -> 200 to state()
            method != "POST" -> 405 to error("use GET or POST")
            path == "/scenario" -> {
                val json = if (contentType.contains("csv", ignoreCase = true) || !body.trimStart().startsWith("{")) ScriptedScenarioParser.fromCsv(body) else JSONObject(body)
                source.loadScenario(json.toString())
                source.resume()
                200 to state().put("loaded", json.optString("name", "scenario"))
            }
            path == "/reading" -> {
                val data = JSONObject(body)
                source.reading(
                    atMs = data.optLong("at_ms", source.nowMs),
                    weightGrams = data.optionalDouble("weight_g"),
                    flowGramsPerSecond = data.optionalDouble("flow_gps"),
                    overload = data.optBoolean("overload", false),
                )
                200 to state()
            }
            path == "/event" -> {
                val data = JSONObject(body)
                val name = data.getString("event")
                val payload = JSONObject(data.toString())
                payload.remove("event")
                val type = when (name) {
                    "failure" -> "connection_failed"
                    "scale_drop" -> "drop"
                    else -> name
                }
                if (type == "connection_failed") payload.put("error", data.optString("error", "NOT_FOUND"))
                if (type == "overload") payload.put("grams", data.optDouble("weight_g", 1300.0))
                source.inject(type, payload)
                200 to state()
            }
            path == "/clock" -> {
                val data = JSONObject(body)
                data.optionalDouble("speed")?.let(source::changeSpeed)
                val action = data.optString("action", "advance")
                require(action in setOf("pause", "resume", "advance")) { "action must be pause, resume, or advance" }
                when (action) {
                    "pause" -> source.pause()
                    "resume" -> source.resume()
                    "advance" -> if (data.has("at_ms")) source.advanceTo(data.getLong("at_ms")) else source.advance(data.optLong("ms", 0L))
                    else -> Unit
                }
                200 to state()
            }
            else -> 404 to error("unknown endpoint")
        }
    } catch (failure: IllegalArgumentException) {
        400 to error(failure.message ?: "invalid request")
    } catch (failure: Exception) {
        400 to error(failure.message ?: "invalid request")
    }

    private fun state() = JSONObject().apply {
        put("connection", when (val value = source.state.value) {
            ScaleState.Idle -> "idle"
            ScaleState.Searching -> "searching"
            ScaleState.Connecting -> "connecting"
            is ScaleState.Connected -> "connected"
            is ScaleState.Failed -> "failed:${value.error.name.lowercase()}"
        })
        source.reading.value?.let { reading ->
            put("weight_g", reading.weightGrams)
            put("overload", reading.overload)
        }
        put("time_ms", source.nowMs)
        put("position", source.position)
    }

    private fun respond(client: Socket, status: Int, data: JSONObject) {
        val phrase = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            413 -> "Payload Too Large"
            else -> "Internal Server Error"
        }
        val bytes = data.toString().toByteArray(StandardCharsets.UTF_8)
        val output = client.getOutputStream()
        output.write("HTTP/1.1 $status $phrase\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.write(bytes)
        output.flush()
    }

    private fun readLine(input: java.io.InputStream): String {
        val line = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value < 0 || value == '\n'.code) break
            if (value != '\r'.code) line.write(value)
        }
        return String(line.toByteArray(), StandardCharsets.UTF_8)
    }

    private fun error(message: String) = JSONObject().put("error", message)

    private fun JSONObject.optionalDouble(name: String): Double? = if (has(name) && !isNull(name)) getDouble(name) else null

    companion object {
        const val PORT = 8765
        private const val MAX_BODY_BYTES = 1_000_000
        private const val SAMPLE_PERIOD_MS = 100L
    }
}
