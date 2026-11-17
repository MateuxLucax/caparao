package dev.mateux.caparao

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.widget.TextView
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mateux.caparao.data.local.BrewDatabase
import dev.mateux.caparao.data.local.BrewRepository
import dev.mateux.caparao.data.local.BrewEntity
import dev.mateux.caparao.ui.widget.BrewWidgetRefresh
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/** A broadcast that hangs on its data read must still finish before Android's background ANR window (#326). */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BrewWidgetProviderTest {
    @Test
    fun configurationRefreshReloadsSavedBrewIntoPopulatedLocalizedViews() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val localizedContext = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("pt-BR")) },
        )
        val db = Room.inMemoryDatabaseBuilder(context, BrewDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.brews().insert(brewEntity())
            var root: android.view.View? = null
            BrewWidgetProvider().refreshBounded(
                read = { BrewRepository(db.brews()).latest() },
                publish = { latest -> root = BrewWidgetProvider().views(localizedContext, 42, latest).apply(localizedContext, null) },
                finish = {},
            )
            val populated = requireNotNull(root)
            assertEquals("Fazenda Ambiental", populated.findViewById<TextView>(R.id.widget_coffee).text)
            assertEquals(View.VISIBLE, populated.findViewById<View>(R.id.widget_meta).visibility)
            assertEquals("15 g → 250 g", (populated.findViewById<View>(R.id.widget_meta) as android.widget.TextView).text)
        } finally {
            db.close()
        }
    }

    @Test
    fun finishRunsWithinTheTimeoutWhenTheReadNeverReturns() = runTest {
        var finished = false
        var published = false

        BrewWidgetProvider().refreshBounded<String>(
            read = { awaitCancellation() },
            publish = { published = true },
            finish = { finished = true },
        )

        assertTrue("the broadcast must finish", finished)
        assertFalse("the previous widget content stays", published)
        assertTrue(
            "finished after ${currentTime}ms, expected at most ${BrewWidgetRefresh.TIMEOUT_MS}ms",
            currentTime <= BrewWidgetRefresh.TIMEOUT_MS,
        )
    }

    private fun brewEntity() = BrewEntity(
        id = "locale-widget-brew", mode = "FILTER", startedAt = 1L, durationMs = 30_000L,
        doseGrams = 15.0, yieldGrams = 250.0, timeToFirstDropMs = null,
        flowAvg = 1.0, flowPeak = 2.0, interrupted = false, syncedAt = null,
        recipeName = null, coffee = "Fazenda Ambiental",
    )
}
