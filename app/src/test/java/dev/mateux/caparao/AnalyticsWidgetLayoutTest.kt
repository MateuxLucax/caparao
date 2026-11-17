package dev.mateux.caparao

import android.content.Context
import android.content.ComponentName
import android.content.res.Configuration
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.view.View.MeasureSpec
import androidx.test.core.app.ApplicationProvider
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.ui.analytics.CoffeeSummary
import dev.mateux.caparao.ui.analytics.AnalyticsViewModel
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnalyticsWidgetLayoutTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-11-16T15:00:00Z")
    private val brew = Brew("widget", BrewMode.FILTER, now.minusSeconds(7200).toEpochMilli(), 180_000, 18.0, 300.0, null, 1.5, 2.0, false)
    private val state by lazy { AnalyticsViewModel.summarize(listOf(brew), emptyList(), now, ZoneId.of("UTC")) }
    private val provider = AnalyticsWidgetProvider()

    @Test
    fun layoutAIsTheWideFourByTwoWireframe() {
        val size = SizeF(340f, 110f)
        val root = render(size)
        assertEquals(R.layout.widget_analytics_wide, provider.layoutForSize(size))
        assertNotNull(root.findViewById<TextView>(R.id.analytics_widget_caffeine))
        assertNotNull(root.findViewById<ImageView>(R.id.analytics_widget_sparkline))
        assertNotNull(root.findViewById<TextView>(R.id.analytics_widget_bedtime))
        assertNotNull(root.findViewById<TextView>(R.id.analytics_widget_week))
        assertTrue(root.findViewById<View>(android.R.id.background).hasOnClickListeners())
        val sparkline = (root.findViewById<ImageView>(R.id.analytics_widget_sparkline).drawable as BitmapDrawable).bitmap
        assertEquals(((size.width - 48f) * 1.2f / 2.2f * context.resources.displayMetrics.density).toInt(), sparkline.width)
        assertEquals((28f * context.resources.displayMetrics.density).toInt(), sparkline.height)
        assertHeaderAbsent(root)
    }

    @Test
    fun layoutBIsTheExpandedFourByThreeChartWireframe() {
        val size = SizeF(340f, 250f)
        val root = render(size)
        assertEquals(R.layout.widget_analytics_expanded, provider.layoutForSize(size))
        val chip = root.findViewById<TextView>(R.id.analytics_widget_bedtime_chip)
        assertNotNull(chip)
        assertEquals(1, chip.maxLines)
        assertEquals(1, root.findViewById<TextView>(R.id.analytics_widget_caffeine_caption).maxLines)
        assertEquals(1, root.findViewById<TextView>(R.id.analytics_widget_week_label).maxLines)
        assertEquals(1, root.findViewById<TextView>(R.id.analytics_widget_delta).maxLines)
        val widthPx = (size.width * context.resources.displayMetrics.density).toInt()
        val heightPx = (size.height * context.resources.displayMetrics.density).toInt()
        root.measure(MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightPx, MeasureSpec.EXACTLY))
        root.layout(0, 0, widthPx, heightPx)
        val heroGroup = root.findViewById<View>(R.id.analytics_widget_hero_group)
        assertTrue(heroGroup.right + 8f * context.resources.displayMetrics.density <= chip.left)
        val chart = root.findViewById<ImageView>(R.id.analytics_widget_curve)
        assertNotNull(chart)
        val bitmap = (chart.drawable as BitmapDrawable).bitmap
        assertEquals(chartWidthPx(size, contentWidthDp = size.width - 32f), bitmap.width)
        assertEquals(((size.height - 104f) * context.resources.displayMetrics.density).toInt(), bitmap.height)
        assertHeaderAbsent(root)
    }

    @Test
    fun layoutCIsTheSquareAndNarrowWireframe() {
        val size = SizeF(220f, 220f)
        val root = render(size)
        assertEquals(R.layout.widget_analytics_square, provider.layoutForSize(size))
        assertNotNull(root.findViewById<TextView>(R.id.analytics_widget_caffeine_caption))
        assertNotNull(root.findViewById<ImageView>(R.id.analytics_widget_sparkline))
        assertNotNull(root.findViewById<TextView>(R.id.analytics_widget_bedtime_label))
        assertNotNull(root.findViewById<TextView>(R.id.analytics_widget_week_label))
        assertEquals(View.GONE, root.findViewById<TextView>(R.id.analytics_widget_delta).visibility)
        val bitmap = provider.curveBitmap(context, state, size, 23 * 60, detailed = false)
        assertEquals(chartWidthPx(size, contentWidthDp = size.width - 32f), bitmap.width)
        assertEquals(((size.height - 156f) * context.resources.displayMetrics.density).toInt(), bitmap.height)
        assertEquals(R.layout.widget_analytics_square, provider.layoutForSize(SizeF(230f, 110f)))
        assertHeaderAbsent(root)
    }

    @Test
    fun sizeBucketsSelectWideExpandedAndSquareLayouts() {
        assertEquals(R.layout.widget_analytics_wide, provider.layoutForSize(SizeF(260f, 180f)))
        assertEquals(R.layout.widget_analytics_expanded, provider.layoutForSize(SizeF(250f, 250f)))
        assertEquals(R.layout.widget_analytics_square, provider.layoutForSize(SizeF(110f, 110f)))
        assertEquals(R.layout.widget_analytics_wide, provider.layoutForSize(SizeF(260f, 180f)))
        assertEquals(R.layout.widget_analytics_wide, provider.layoutForSize(SizeF(260f, 179f)))
    }

    @Test
    fun layoutDShowsTheEmptyStateActionAtEverySize() {
        for (size in listOf(SizeF(340f, 110f), SizeF(340f, 165f), SizeF(220f, 220f))) {
            val root = provider.viewsForSize(context, 1, emptyList(), AnalyticsViewModel.summarize(emptyList(), emptyList(), now), size).apply(context, null)
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.analytics_widget_empty).visibility)
            assertEquals(View.GONE, root.findViewById<View>(R.id.analytics_widget_content).visibility)
            assertNotNull(root.findViewById<ImageView>(R.id.analytics_widget_empty_icon))
            assertEquals(context.getString(R.string.analytics_widget_empty), root.findViewById<TextView>(R.id.analytics_widget_empty_text).text)
            assertEquals(context.getString(R.string.analytics_widget_start_brew), root.findViewById<TextView>(R.id.analytics_widget_start_brew).text)
            assertTrue(root.findViewById<View>(R.id.analytics_widget_start_brew).hasOnClickListeners())
            assertHeaderAbsent(root)
        }
    }

    @Test
    fun bedtimeOverTargetUsesErrorColorAndAnAccessibleExplanation() {
        val highState = state.copy(caffeine = state.caffeine.copy(bedtimeMg = 51))
        val root = provider.viewsForSize(context, 1, listOf(brew), highState, SizeF(340f, 110f)).apply(context, null)
        val bedtime = root.findViewById<TextView>(R.id.analytics_widget_bedtime)
        assertTrue(bedtime.currentTextColor != context.getColor(R.color.widget_primary_text))
        assertEquals(context.getString(R.string.analytics_widget_bedtime_high_description, 51), bedtime.contentDescription)
    }

    @Test
    fun pickerUsesTheFourByTwoSizeAndLocalizedReceiverLabels() {
        val parser = context.resources.getXml(R.xml.analytics_widget_info)
        while (parser.next() != org.xmlpull.v1.XmlPullParser.START_TAG) {}
        val namespace = "http://schemas.android.com/apk/res/android"
        assertEquals(4, parser.getAttributeIntValue(namespace, "targetCellWidth", 0))
        assertEquals(2, parser.getAttributeIntValue(namespace, "targetCellHeight", 0))
        assertTrue(parser.getAttributeValue(namespace, "minWidth").contains("180"))
        assertTrue(250f > context.resources.getDimension(R.dimen.analytics_widget_min_height) / context.resources.displayMetrics.density)
        assertEquals(R.layout.widget_analytics_preview, parser.getAttributeResourceValue(namespace, "previewLayout", 0))
        assertNotNull(parser.getAttributeResourceValue(namespace, "previewImage", 0).takeIf { it != 0 })
        parser.close()
        assertEquals(R.string.widget_last_brew_title, context.packageManager.getReceiverInfo(ComponentName(context, BrewWidgetProvider::class.java), 0).labelRes)
        assertEquals(R.string.analytics_widget_name, context.packageManager.getReceiverInfo(ComponentName(context, AnalyticsWidgetProvider::class.java), 0).labelRes)

        val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("pt-BR")) }
        val portuguese = context.createConfigurationContext(configuration)
        assertEquals("Caffeine now and this week", context.getString(R.string.analytics_widget_description))
        assertEquals("Cafeína agora e nesta semana", portuguese.getString(R.string.analytics_widget_description))
        assertEquals("Analytics", context.getString(R.string.analytics_widget_name))
        assertEquals("Análises", portuguese.getString(R.string.analytics_widget_name))
    }

    @Test
    fun ptBrUsesTheSameCaffeineNowCaptionInThePlacedWidgetAndPreview() {
        val portuguese = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("pt-BR")) },
        )
        val root = provider.viewsForSize(portuguese, 1, listOf(brew), state, SizeF(340f, 110f)).apply(portuguese, null)
        assertEquals(portuguese.getString(R.string.analytics_widget_now_caption), root.findViewById<TextView>(R.id.analytics_widget_caffeine_caption).text)
        assertEquals("cafeína agora", portuguese.getString(R.string.analytics_widget_now_caption))
        assertEquals("Esta semana", portuguese.getString(R.string.analytics_widget_week_title))
        val expanded = provider.viewsForSize(portuguese, 1, listOf(brew), state, SizeF(340f, 250f)).apply(portuguese, null)
        assertEquals("Esta semana", expanded.findViewById<TextView>(R.id.analytics_widget_week_label).text)
        assertTrue(expanded.findViewById<TextView>(R.id.analytics_widget_bedtime_chip).text.toString().startsWith("Ao dormir"))
        assertTrue(AnalyticsWidgetProvider.CONFIGURATION_ACTIONS.contains(Intent.ACTION_LOCALE_CHANGED))
        assertTrue(AnalyticsWidgetProvider.CONFIGURATION_ACTIONS.contains(Intent.ACTION_CONFIGURATION_CHANGED))
    }

    @Test
    fun deltaUsesThisWeekMinusLastWeekAndArrowMatchesItsSign() {
        val sample = state.copy(thisWeek = CoffeeSummary(grams = 38.0), lastWeek = CoffeeSummary(grams = 266.0))
        val root = provider.viewsForSize(context, 1, listOf(brew), sample, SizeF(340f, 110f)).apply(context, null)
        assertEquals("38 g", root.findViewById<TextView>(R.id.analytics_widget_week).text)
        assertEquals("↓ 228 g", root.findViewById<TextView>(R.id.analytics_widget_delta).text)

        val previous = sample.copy(thisWeek = CoffeeSummary(grams = 266.0), lastWeek = CoffeeSummary(grams = 38.0))
        val reversed = provider.viewsForSize(context, 1, listOf(brew), previous, SizeF(340f, 110f)).apply(context, null)
        assertEquals("↑ 228 g", reversed.findViewById<TextView>(R.id.analytics_widget_delta).text)
    }

    @Test
    fun nightModeUsesReadableThemeColorsForWidgetText() {
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }
        val night = context.createConfigurationContext(configuration)
        val root = provider.viewsForSize(night, 1, listOf(brew), state, SizeF(340f, 250f)).apply(night, null)
        assertEquals(night.getColor(R.color.widget_hero_text), root.findViewById<TextView>(R.id.analytics_widget_caffeine).currentTextColor)
        assertEquals(night.getColor(R.color.widget_secondary_text), root.findViewById<TextView>(R.id.analytics_widget_caffeine_caption).currentTextColor)
        assertEquals(night.getColor(R.color.widget_secondary_text), root.findViewById<TextView>(R.id.analytics_widget_week_label).currentTextColor)
        assertEquals(night.getColor(R.color.widget_secondary_text), root.findViewById<TextView>(R.id.analytics_widget_delta).currentTextColor)
        assertEquals(night.getColor(R.color.widget_primary_text), root.findViewById<TextView>(R.id.analytics_widget_week).currentTextColor)
        assertNotEquals(night.getColor(R.color.widget_primary_text), root.findViewById<TextView>(R.id.analytics_widget_caffeine).currentTextColor)
    }

    @Test
    fun chartLineColorAdaptsToDayAndNightContexts() {
        val size = SizeF(340f, 250f)
        val dayBitmap = provider.curveBitmap(context, state, size, 23 * 60, detailed = true)
        val nightContext = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            },
        )
        val nightBitmap = provider.curveBitmap(nightContext, state, size, 23 * 60, detailed = true)
        val dayColor = AnalyticsWidgetProvider.chartLineColor(context)
        val nightColor = AnalyticsWidgetProvider.chartLineColor(nightContext)
        assertNotEquals(dayColor, nightColor)
        assertTrue(dayBitmap.pixels().isNotEmpty())
        assertTrue(nightBitmap.pixels().isNotEmpty())
    }

    private fun render(size: SizeF): View = provider
        .viewsForSize(context, 1, listOf(brew), state, size)
        .apply(context, null)

    private fun chartWidthPx(size: SizeF, contentWidthDp: Float): Int {
        val density = context.resources.displayMetrics.density
        val widthDp = if (provider.layoutForSize(size) == R.layout.widget_analytics_wide) (size.width - 48f) * 1.2f / 2.2f else contentWidthDp
        return (widthDp * density).toInt()
    }

    private fun assertHeaderAbsent(root: View) {
        fun visit(view: View) {
            if (view is TextView) assertTrue(view.text.toString() !in setOf("Analytics", "Análises"))
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(root)
    }

    private fun android.graphics.Bitmap.pixels(): Set<Int> = buildSet {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        addAll(pixels.toList())
    }
}
