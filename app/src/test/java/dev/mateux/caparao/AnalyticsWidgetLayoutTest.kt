package dev.mateux.caparao

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.mateux.caparao.data.local.SettingsStore
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.ui.analytics.AnalyticsViewModel
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnalyticsWidgetLayoutTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-11-16T15:00:00Z")
    private val brew = Brew("widget", BrewMode.FILTER, now.minusSeconds(7200).toEpochMilli(), 180_000, 18.0, 300.0, null, 1.5, 2.0, false)

    @Test
    fun fourByTwoShowsSummaryWithoutCurve() {
        val root = AnalyticsWidgetProvider().views(context, 1, listOf(brew), summarize(listOf(brew)), expanded = false).apply(context, null)
        root.measure(spec(250), spec(176))
        root.layout(0, 0, 250, 176)

        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.analytics_widget_content).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.analytics_widget_curve).visibility)
        assertTrue(root.findViewById<TextView>(R.id.analytics_widget_caffeine).text.contains("mg"))
        assertTrue(root.findViewById<TextView>(R.id.analytics_widget_week).text.contains("Last week"))
    }

    @Test
    fun fourByThreeAddsTodaysCaffeineCurve() {
        val root = AnalyticsWidgetProvider().views(context, 1, listOf(brew), summarize(listOf(brew)), expanded = true).apply(context, null)
        root.measure(spec(250), spec(264))
        root.layout(0, 0, 250, 264)

        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.analytics_widget_curve).visibility)
        assertTrue(root.findViewById<android.widget.ImageView>(R.id.analytics_widget_curve).drawable != null)
    }

    @Test
    fun emptyStateHidesSummaryAndCurve() {
        val root = AnalyticsWidgetProvider().views(context, 1, emptyList(), summarize(emptyList()), expanded = true).apply(context, null)
        root.measure(spec(250), spec(264))
        root.layout(0, 0, 250, 264)

        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.analytics_widget_empty).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.analytics_widget_content).visibility)
        assertEquals(context.getString(R.string.analytics_widget_empty), root.findViewById<TextView>(R.id.analytics_widget_empty).text)
    }

    private fun summarize(brews: List<Brew>) = AnalyticsViewModel.summarize(
        brews, emptyList(), now, ZoneId.of("UTC"), SettingsStore.DEFAULT_BEDTIME_MINUTES,
    )

    private fun spec(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
}
