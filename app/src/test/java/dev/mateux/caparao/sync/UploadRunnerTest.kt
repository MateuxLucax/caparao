package dev.mateux.caparao.sync

import dev.mateux.caparao.data.diagnostics.DiagnosticEvent
import dev.mateux.caparao.data.diagnostics.FakeDiagnostics
import dev.mateux.caparao.data.exporter.ExporterClient
import dev.mateux.caparao.data.exporter.LocalServer
import dev.mateux.caparao.data.local.BrewRepository
import dev.mateux.caparao.data.local.SettingsStore
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.BrewSample
import dev.mateux.caparao.model.SyncError
import dev.mateux.caparao.model.SyncError.Kind
import dev.mateux.caparao.ui.FakeBrewDao
import dev.mateux.caparao.ui.FakePreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
    private val diagnostics = FakeDiagnostics()
    private val runner = UploadRunner(repository, settings, ExporterClient(1_000, 1_000), { blocked }, diagnostics, now = { 42_000L })

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
        assertEquals(listOf(DiagnosticEvent.UploadSucceeded), diagnostics.events)
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
        assertEquals(listOf(DiagnosticEvent.UploadFailed(Kind.SERVER), DiagnosticEvent.UploadRetried), diagnostics.events)
    }

    @Test
    fun wrongTokenFailsWithoutRetry() {
        configure()
        save("b1")
        server.status(401)
        assertEquals(UploadOutcome.FAILURE, run("b1"))
        assertEquals(SyncError(Kind.UNAUTHORIZED), settings.lastSyncError)
        assertEquals(listOf(DiagnosticEvent.UploadFailed(Kind.UNAUTHORIZED)), diagnostics.events)
    }

    @Test
    fun blockedLocalNetworkFailsWithoutARequest() {
        configure()
        save("b1")
        blocked = true
        assertEquals(UploadOutcome.FAILURE, run("b1"))
        assertEquals(Kind.LOCAL_NETWORK_PERMISSION, settings.lastSyncError?.kind)
        assertEquals(0, server.requests.size)
        assertEquals(listOf(DiagnosticEvent.UploadFailed(Kind.LOCAL_NETWORK_PERMISSION)), diagnostics.events)
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

    @Test
    fun notesEditedDuringAnUploadStayUnsyncedAndGoOutOnTheRetry() = runBlocking {
        configure()
        save("b1")
        val uploadStarted = CompletableDeferred<Unit>()
        val releaseUpload = CompletableDeferred<Unit>()
        val sentNotes = mutableListOf<String?>()
        var attempts = 0
        // Suspends the first upload so the test can edit the brew while the request is in flight, like the real server.
        val exporter = object : ExporterClient() {
            override suspend fun upload(serverUrl: String, token: String, brew: Brew, samples: List<BrewSample>) {
                sentNotes += brew.notes
                if (++attempts == 1) {
                    uploadStarted.complete(Unit)
                    releaseUpload.await()
                }
            }
        }
        val runner = UploadRunner(repository, settings, exporter, { blocked }, now = { 42_000L })

        val firstAttempt = async(Dispatchers.Default) { runner.run("b1") }
        uploadStarted.await()
        repository.saveRating(repository.get("b1")!!.copy(notes = "Sweet"), ratedAt = 41_000)
        releaseUpload.complete(Unit)

        assertEquals(UploadOutcome.RETRY, firstAttempt.await())
        assertNull(syncedAt("b1"))
        assertEquals(UploadOutcome.SUCCESS, runner.run("b1"))
        assertEquals(listOf(null, "Sweet"), sentNotes)
        assertEquals(42_000L, syncedAt("b1"))
    }
}
