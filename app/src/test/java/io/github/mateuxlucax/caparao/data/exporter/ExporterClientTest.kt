package io.github.mateuxlucax.caparao.data.exporter

import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewSample
import io.github.mateuxlucax.caparao.model.SyncError.Kind
import io.github.mateuxlucax.caparao.sync.SyncErrors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.ServerSocket
import java.net.SocketTimeoutException

/** The client against a real HTTP server on 127.0.0.1: the responses and client behavior table of contract/README.md. */
class ExporterClientTest {
    private val server = LocalServer()
    private val client = ExporterClient(connectTimeoutMs = 1_000, readTimeoutMs = 300)

    @After
    fun tearDown() = server.close()

    private val brew = Brew(
        "0f8e1c2a-5b7d-4c3e-9a10-2f6d8b4e7c91", BrewMode.ESPRESSO, 1_792_931_400_000, 28_600, 18.0, 36.4, 6_200, 1.3, 2.1,
        interrupted = false,
    )

    private fun upload(url: String = server.url) = runBlocking {
        client.upload(url, "dev-token", brew, listOf(BrewSample(0, 0.0, 0.0), BrewSample(100, 0.1, 0.4)))
    }

    /** The exception [block] throws, mapped like the upload job maps it. */
    private fun failure(block: () -> Unit): Exception {
        try {
            block()
        } catch (e: Exception) {
            return e
        }
        fail("expected an exception")
        throw IllegalStateException()
    }

    @Test
    fun postsTheBrewWithTheTokenAsJson() {
        upload(server.url + "/")
        val request = server.requests.single()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/brews", request.path)
        assertEquals("Bearer dev-token", request.headers["authorization"])
        assertTrue(request.headers["content-type"]!!.startsWith("application/json"))
        assertEquals(brew.id, request.json.getString("id"))
        assertEquals(2, request.json.getJSONArray("samples").length())
    }

    @Test
    fun createdAndReplacedAreSuccess() {
        server.status(201)
        upload()
        server.status(200)
        upload()
        assertEquals(2, server.requests.size)
    }

    @Test
    fun serverErrorsAreRetried() {
        listOf(500, 502, 503).forEach { code ->
            server.status(code)
            val error = SyncErrors.of(failure { upload() })
            assertEquals(Kind.SERVER, error.kind)
            assertTrue(error.kind.retry)
        }
    }

    @Test
    fun timeoutIsRetried() {
        server.respond = { _, _ ->
            Thread.sleep(1_000)
            201
        }
        val e = failure { upload() }
        assertTrue(e.toString(), e is SocketTimeoutException)
        assertEquals(Kind.NETWORK, SyncErrors.of(e).kind)
        assertTrue(SyncErrors.of(e).kind.retry)
    }

    @Test
    fun unreachableIsRetried() {
        val closedPort = ServerSocket(0).use { it.localPort }
        val e = failure { upload("http://127.0.0.1:$closedPort") }
        assertTrue(e is IOException)
        assertEquals(Kind.NETWORK, SyncErrors.of(e).kind)
    }

    @Test
    fun wrongTokenIsNotRetried() {
        server.status(401)
        val error = SyncErrors.of(failure { upload() })
        assertEquals(Kind.UNAUTHORIZED, error.kind)
        assertFalse(error.kind.retry)
    }

    @Test
    fun other4xxAreNotRetried() {
        listOf(400, 404, 405, 408, 413, 429).forEach { code ->
            server.status(code)
            val error = SyncErrors.of(failure { upload() })
            assertEquals("$code", Kind.REJECTED, error.kind)
            assertFalse(error.kind.retry)
            assertTrue(error.detail!!.startsWith("HTTP $code"))
        }
    }

    @Test
    fun redirectIsAnErrorAndTheTokenIsNotSentOn() {
        LocalServer().use { elsewhere ->
            server.respond = { exchange, _ ->
                exchange.responseHeaders.add("Location", elsewhere.url + "/api/v1/brews")
                307
            }
            val error = SyncErrors.of(failure { upload() })
            assertEquals(Kind.REDIRECTED, error.kind)
            assertFalse(error.kind.retry)
            assertEquals(0, elsewhere.requests.size)
        }
    }

    @Test
    fun checkAsksForHealthThenTheListWithTheToken() {
        server.status(200)
        runBlocking { client.check(server.url, "dev-token") }
        assertEquals(listOf("GET /healthz", "GET /api/v1/brews"), server.requests.map { "${it.method} ${it.path}" })
        assertNull("no token to the open endpoint", server.requests[0].headers["authorization"])
        assertEquals("Bearer dev-token", server.requests[1].headers["authorization"])
    }

    @Test
    fun checkReportsAWrongToken() {
        server.respond = { _, request -> if (request.path == "/healthz") 200 else 401 }
        val error = SyncErrors.of(failure { runBlocking { client.check(server.url, "wrong") } })
        assertEquals(Kind.UNAUTHORIZED, error.kind)
    }

    @Test
    fun checkReportsAServerThatIsNotOne() {
        server.status(404)
        assertEquals(Kind.REJECTED, SyncErrors.of(failure { runBlocking { client.check(server.url, "dev-token") } }).kind)
        assertEquals("stops at the health check", 1, server.requests.size)
    }
}
