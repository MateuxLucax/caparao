package io.github.mateuxlucax.caparao.sync

import io.github.mateuxlucax.caparao.data.exporter.ExporterClient
import io.github.mateuxlucax.caparao.data.exporter.LocalServer
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewSample
import io.github.mateuxlucax.caparao.model.SyncError
import io.github.mateuxlucax.caparao.model.SyncError.Kind
import io.github.mateuxlucax.caparao.ui.FakeBrewDao
import io.github.mateuxlucax.caparao.ui.FakePreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UploadRunnerTest {
    private val server = LocalServer()
    private val dao = FakeBrewDao()
    private val repository = BrewRepository(dao)
    private val settings = SettingsStore(FakePreferences())
    private var blocked = false
    private val runner = UploadRunner(repository, settings, ExporterClient(1_000, 1_000), { blocked }, now = { 42_000L })

    @After
    fun tearDown() = server.close()

    private fun brew(id: String) = Brew(id, BrewMode.FILTER, 1_000, 60_000, 15.0, 250.0, null, 4.0, 6.0, interrupted = false)

    private fun save(id: String) = runBlocking { repository.save(brew(id), listOf(BrewSample(0, 0.0, 0.0))) }

    private fun run(id: String) = runBlocking { runner.run(id) }

    private fun syncedAt(id: String) = runBlocking { repository.get(id)?.syncedAt }

    private fun configure() = settings.saveServer(server.url, "dev-token")

    @Test
    fun successMarksSyncedAndRecordsTheTime() {
        configure()
        save("b1")
        assertEquals(UploadOutcome.SUCCESS, run("b1"))
        assertEquals(42_000L, syncedAt("b1"))
        assertEquals(42_000L, settings.lastSyncMs)
        assertEquals("/api/v1/brews", server.requests.single().path)
    }

    @Test
    fun withoutAServerNothingIsSent() {
        save("b1")
        assertEquals(UploadOutcome.FAILURE, run("b1"))
        assertEquals(0, server.requests.size)
        assertNull(syncedAt("b1"))
    }

    @Test
    fun aDeletedBrewIsDone() {
        configure()
        assertEquals(UploadOutcome.SUCCESS, run("gone"))
        assertEquals(0, server.requests.size)
    }

    @Test
    fun serverErrorRetriesAndIsShown() {
        configure()
        save("b1")
        server.status(503)
        assertEquals(UploadOutcome.RETRY, run("b1"))
        assertNull(syncedAt("b1"))
        assertEquals(Kind.SERVER, settings.lastSyncError?.kind)
    }

    @Test
    fun wrongTokenFailsWithoutRetry() {
        configure()
        save("b1")
        server.status(401)
        assertEquals(UploadOutcome.FAILURE, run("b1"))
        assertEquals(SyncError(Kind.UNAUTHORIZED), settings.lastSyncError)
    }

    @Test
    fun blockedLocalNetworkFailsWithoutARequest() {
        configure()
        save("b1")
        blocked = true
        assertEquals(UploadOutcome.FAILURE, run("b1"))
        assertEquals(Kind.LOCAL_NETWORK_PERMISSION, settings.lastSyncError?.kind)
        assertEquals(0, server.requests.size)
    }

    @Test
    fun aSuccessClearsAGeneralError() {
        configure()
        save("b1")
        save("b2")
        server.status(503)
        run("b1")
        server.status(201)
        run("b2")
        assertNull(settings.lastSyncError)
    }

    @Test
    fun aRejectedBrewStaysShownUntilItGoesThrough() {
        configure()
        save("b1")
        save("b2")
        server.status(400)
        assertEquals(UploadOutcome.FAILURE, run("b1"))
        server.status(201)
        run("b2")
        assertEquals("another brew's success keeps it", Kind.REJECTED, settings.lastSyncError?.kind)
        run("b1")
        assertNull(settings.lastSyncError)
    }

    @Test
    fun savingTheServerAgainClearsTheError() {
        configure()
        save("b1")
        server.status(401)
        run("b1")
        configure()
        assertNull(settings.lastSyncError)
    }
}
