package io.github.mateuxlucax.caparao.data.exporter

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.util.Collections

/** A request as [LocalServer] received it; header names lowercase. */
class ReceivedRequest(val method: String, val path: String, val headers: Map<String, String>, val body: String) {
    val json: JSONObject get() = JSONObject(body)
}

/**
 * An HTTP server on 127.0.0.1 for client tests. [respond] answers each request (default `201`); it may set headers,
 * sleep or pick any status.
 */
class LocalServer(var respond: (HttpExchange, ReceivedRequest) -> Int = { _, _ -> 201 }) : AutoCloseable {
    val requests: MutableList<ReceivedRequest> = Collections.synchronizedList(mutableListOf())

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val request = ReceivedRequest(
                exchange.requestMethod,
                exchange.requestURI.path,
                exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.first() },
                exchange.requestBody.readBytes().decodeToString(),
            )
            requests += request
            try {
                val code = respond(exchange, request)
                val reply = """{"status": $code}""".toByteArray()
                exchange.sendResponseHeaders(code, reply.size.toLong())
                exchange.responseBody.write(reply)
            } catch (_: Exception) {
                // The client gave up (timeout test); nothing to answer.
            } finally {
                exchange.close()
            }
        }
        start()
    }

    val url = "http://127.0.0.1:${server.address.port}"

    /** Answers every request with [code]. */
    fun status(code: Int) {
        respond = { _, _ -> code }
    }

    override fun close() = server.stop(0)
}
