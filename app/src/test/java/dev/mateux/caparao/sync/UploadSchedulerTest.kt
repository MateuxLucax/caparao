package dev.mateux.caparao.sync

import dev.mateux.caparao.data.local.SettingsStore
import dev.mateux.caparao.ui.FakePreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadSchedulerTest {
    private val settings = SettingsStore(FakePreferences())
    private val queue = FakeUploadQueue()
    private val scheduler = UploadScheduler(settings, queue) { listOf("b1", "b2") }

    @Test
    fun noJobIsEnqueuedWithoutAServer() = runBlocking {
        scheduler.brewSaved("b1")
        scheduler.resendUnsynced()
        assertEquals(emptyList<String>(), queue.calls)
    }

    @Test
    fun aUrlWithoutATokenIsNotAServer() = runBlocking {
        settings.saveServer("https://coffee.example.com", " ")
        scheduler.brewSaved("b1")
        scheduler.resendUnsynced()
        assertEquals(emptyList<String>(), queue.calls)
    }

    @Test
    fun aSavedBrewKeepsAnExistingJob() {
        settings.saveServer("https://coffee.example.com", "dev-token")
        scheduler.brewSaved("b1")
        assertEquals(listOf("keep b1"), queue.calls)
    }

    @Test
    fun resendRestartsEveryUnsyncedBrew() = runBlocking {
        settings.saveServer("https://coffee.example.com", "dev-token")
        scheduler.resendUnsynced()
        assertEquals(listOf("replace b1", "replace b2"), queue.calls)
    }

    @Test
    fun openingTheAppKeepsJobsAndAddsMissingOnes() = runBlocking {
        scheduler.ensurePending()
        assertEquals(emptyList<String>(), queue.calls)
        settings.saveServer("https://coffee.example.com", "dev-token")
        scheduler.ensurePending()
        assertEquals(listOf("keep b1", "keep b2"), queue.calls)
    }

    @Test
    fun deletingAndRemovingCancel() {
        scheduler.brewDeleted("b1")
        scheduler.serverRemoved()
        assertEquals(listOf("cancel b1", "cancel all"), queue.calls)
    }

    @Test
    fun theMovedWorkerCancelsOldJobsAndReschedulesUnsyncedBrewsOnce() = runBlocking {
        settings.saveServer("https://coffee.example.com", "dev-token")
        var migrated = false
        assertEquals(true, scheduler.rescheduleJobsFromTheOldWorker(false) { migrated = true })
        assertTrue(migrated)
        assertEquals(listOf("cancel all", "replace b1", "replace b2"), queue.calls)

        migrated = false
        assertEquals(false, scheduler.rescheduleJobsFromTheOldWorker(true) { migrated = true })
        assertFalse(migrated)
        assertEquals(listOf("cancel all", "replace b1", "replace b2"), queue.calls)
    }

    @Test
    fun theMovedWorkerIsMarkedMigratedOnlyAfterTheJobsAreScheduled() {
        settings.saveServer("https://coffee.example.com", "dev-token")
        val failing = UploadScheduler(
            settings,
            object : UploadQueue {
                override fun enqueue(brewId: String, replace: Boolean) = throw IllegalStateException("no WorkManager")
                override fun cancel(brewId: String) = Unit
                override fun cancelAll() = Unit
            },
        ) { listOf("b1") }
        var migrated = false
        assertThrows(IllegalStateException::class.java) {
            runBlocking { failing.rescheduleJobsFromTheOldWorker(false) { migrated = true } }
        }
        assertFalse(migrated)
    }
}
