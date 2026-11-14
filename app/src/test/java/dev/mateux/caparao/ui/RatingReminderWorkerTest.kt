package dev.mateux.caparao.ui

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.ProgressUpdater
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.futures.SettableFuture
import androidx.work.impl.utils.taskexecutor.SerialExecutor
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import dev.mateux.caparao.R
import dev.mateux.caparao.data.local.BrewDatabase
import dev.mateux.caparao.data.local.BrewRepository
import dev.mateux.caparao.data.local.SeedBuiltInRecipes
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The reminder's eligibility recheck on the JVM (Robolectric): the worker runs against a real in-memory database and
 * posts nothing for a brew that was rated or discarded since the reminder was scheduled, nor without Android's
 * Notifications permission (#465).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RatingReminderWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, BrewDatabase::class.java)
        .addCallback(SeedBuiltInRecipes)
        .allowMainThreadQueries()
        .build()
    private val brews = BrewRepository(db.brews())
    private val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))

    @Before
    fun grantNotificationsPermission() {
        shadowOf(context.applicationContext as android.app.Application)
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun closeDatabase() = db.close()

    @Test
    fun unratedBrewWithPermissionPostsTheReminder() = runBlocking {
        brews.save(brew("fresh", 1_000), emptyList())

        val result = worker("fresh").doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        val posted = notifications.allNotifications.single()
        assertEquals(context.getString(R.string.rating_reminder_title), posted.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun ratedBrewPostsNothing() = runBlocking {
        val saved = brew("rated", 1_000)
        brews.save(saved, emptyList())
        brews.saveRating(saved.copy(rating = 4), ratedAt = 2_000)

        val result = worker("rated").doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test
    fun discardedBrewPostsNothing() = runBlocking {
        brews.save(brew("discarded", 1_000), emptyList())
        brews.delete("discarded")

        val result = worker("discarded").doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test
    fun missingNotificationsPermissionPostsNothing() = runBlocking {
        brews.save(brew("denied", 1_000), emptyList())
        shadowOf(context.applicationContext as android.app.Application)
            .denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        val result = worker("denied").doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test
    fun aWorkerWithoutABrewIdFails() = runBlocking {
        val result = RatingReminderWorker(
            context,
            workerParameters(null),
            unratedBrews = { brews.notRatedYet().first() },
        ).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun eligibilityNeedsAnUnratedBrewAndThePermission() {
        val unrated = brew("unrated", 1_000)
        // A rated brew and a discarded one are both gone from notRatedYet(): the list names them by omission.
        assertNull(ratingReminderEligible("rated", listOf(unrated), notificationsAllowed = true))
        assertNull(ratingReminderEligible("discarded", listOf(unrated), notificationsAllowed = true))
        assertNull(ratingReminderEligible("unrated", listOf(unrated), notificationsAllowed = false))
        assertEquals(unrated, ratingReminderEligible("unrated", listOf(unrated), notificationsAllowed = true))
    }

    /** The reminder work with the brew lookup bound to the test database, as in the app. */
    private fun worker(brewId: String) = RatingReminderWorker(
        context,
        workerParameters(brewId),
        unratedBrews = { brews.notRatedYet().first() },
    )

    private fun workerParameters(brewId: String?) = WorkerParameters(
        UUID.randomUUID(),
        brewId?.let { Data.Builder().putString(RatingReminderScheduler.INPUT_BREW_ID, it).build() } ?: Data.EMPTY,
        emptyList(),
        WorkerParameters.RuntimeExtras(),
        0,
        0,
        Executors.newSingleThreadExecutor(),
        EmptyCoroutineContext,
        taskExecutor,
        workerFactory,
        progressUpdater,
        foregroundUpdater,
    )

    /** WorkManager's plumbing, unused by [RatingReminderWorker]; minimal fakes keep the constructor happy. */
    private val taskExecutor = object : TaskExecutor {
        override fun getMainThreadExecutor(): Executor = Executors.newSingleThreadExecutor()
        override fun getSerialTaskExecutor(): SerialExecutor = object : SerialExecutor {
            override fun execute(command: Runnable) = command.run()
            override fun hasPendingTasks() = false
        }
        override fun getCoroutineScope(): CoroutineScope = CoroutineScope(EmptyCoroutineContext)
    }

    private val workerFactory = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? = null
    }

    private val progressUpdater = ProgressUpdater { _, _, _ -> SettableFuture.create<Void>() }

    private val foregroundUpdater = ForegroundUpdater { _, _, _ -> SettableFuture.create<Void>() }

    private fun brew(id: String, startedAt: Long) = Brew(
        id = id, mode = BrewMode.FILTER, startedAt = startedAt, durationMs = 60_000, doseGrams = 15.0,
        yieldGrams = 250.0, timeToFirstDropMs = null, flowAvgGramsPerSecond = 1.0, flowPeakGramsPerSecond = 2.0,
        interrupted = false,
    )
}
