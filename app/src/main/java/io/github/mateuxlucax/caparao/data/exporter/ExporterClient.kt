package io.github.mateuxlucax.caparao.data.exporter

import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Non-2xx response from the server; 3xx included, since redirects are never followed. */
class HttpStatusException(val code: Int, message: String) : Exception(message)

/**
 * Client of the upload contract (contract/README.md): `POST {serverUrl}/api/v1/brews` with a Bearer token. The JSON
 * wire format is built only in [body]. Throws [HttpStatusException] for a non-2xx answer and IOException when the
 * server can't be reached; sync/SyncErrors.kt maps both.
 */
class ExporterClient(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 60_000,
) {
    /** Sends one brew; returns on `201` (new) or `200` (replaced, docs/adr/0007-idempotent-upload.md). */
    suspend fun upload(serverUrl: String, token: String, brew: Brew, samples: List<BrewSample>) = withContext(Dispatchers.IO) {
        request("POST", serverUrl, "/api/v1/brews", token, body(brew, samples).toString().toByteArray())
    }

    /**
     * "Test connection": `GET /healthz` (reachable, no token), then `GET /api/v1/brews` with the token, which needs
     * auth and creates nothing. Throws like [upload].
     */
    suspend fun check(serverUrl: String, token: String) = withContext(Dispatchers.IO) {
        request("GET", serverUrl, "/healthz", token = null, body = null)
        request("GET", serverUrl, "/api/v1/brews", token, body = null)
    }

    private fun request(method: String, serverUrl: String, path: String, token: String?, body: ByteArray?) {
        val connection = URL(serverUrl.trim().trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            // A redirect is reported as an error (wrong URL, or http that should be https), and the token is never
            // sent on to another location.
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().take(200)
                throw HttpStatusException(code, "HTTP $code $detail".trim())
            }
            // Drained so the connection can be reused; the content is not needed.
            connection.inputStream.use { it.skip(Long.MAX_VALUE) }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /**
         * The request body of [brew] and its curve, exactly the contract's fields. Values the contract would reject
         * forever (a zero duration, negative or non-finite numbers) are clamped, since a `400` is never retried.
         */
        fun body(brew: Brew, samples: List<BrewSample>): JSONObject = JSONObject()
            .put("id", brew.id.lowercase())
            .put("mode", if (brew.mode == BrewMode.ESPRESSO) "espresso" else "filter")
            .put("started_at_ms", brew.startedAt)
            .put("ended_at_ms", brew.startedAt + brew.durationMs.coerceAtLeast(1))
            .put("dose_g", brew.doseGrams)
            .put("yield_g", nonNegative(brew.yieldGrams))
            // put(key, null) would drop the key; the contract wants it present and null for filter brews.
            .put(
                "time_to_first_drop_s",
                brew.timeToFirstDropMs?.takeIf { brew.mode == BrewMode.ESPRESSO }?.let { it.coerceAtLeast(0) / 1000.0 } ?: JSONObject.NULL,
            )
            .put("flow_avg_gps", nonNegative(brew.flowAvgGramsPerSecond))
            .put("flow_peak_gps", nonNegative(brew.flowPeakGramsPerSecond))
            .put("interrupted", brew.interrupted)
            .put("samples", JSONArray().apply {
                samples.take(MAX_SAMPLES).forEach {
                    put(
                        JSONObject()
                            .put("t_ms", it.atMs.coerceAtLeast(0))
                            .put("weight_g", finite(it.weightGrams))
                            .put("flow_gps", finite(it.flowGramsPerSecond))
                    )
                }
            })

        /** The contract's limit on samples per brew. */
        private const val MAX_SAMPLES = 20_000

        private fun finite(value: Double) = if (value.isFinite()) value else 0.0
        private fun nonNegative(value: Double) = finite(value).coerceAtLeast(0.0)
    }
}
