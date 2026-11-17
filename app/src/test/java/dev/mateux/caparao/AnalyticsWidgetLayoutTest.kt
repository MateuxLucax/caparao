package dev.mateux.caparao

import android.content.Context
import android.content.ComponentName
import android.content.res.Configuration
import android.content.Intent
import android.app.LocaleManager
import android.os.LocaleList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
        assertTrue(sparkline.width > 0)
        assertEquals(((size.width - 48f) * 1.2f / 2.2f * context.resources.displayMetrics.density).toInt(), sparkline.width)
        assertEquals((28f * context.resources.displayMetrics.density).toInt(), sparkline.height)
        assertHeaderAbsent(root)
    }

    @Test
    fun wideSparklineIncludesCaffeineRemainingFromYesterday() {
        val chartNow = Instant.parse("2026-11-17T15:00:00Z")
        val yesterday = brew.copy(id = "yesterday", startedAt = chartNow.minusSeconds(20 * 3600L).toEpochMilli())
        val oldBrewState = AnalyticsViewModel.summarize(listOf(yesterday), emptyList(), chartNow, ZoneId.of("UTC"))
        assertEquals(0, oldBrewState.caffeine.todayBrews)
        assertTrue(oldBrewState.caffeine.points.any { it.mg > 0.0 })

        val size = SizeF(396f, 254f)
        val root = provider.viewsForSize(context, 1, listOf(yesterday), oldBrewState, size).apply(context, null)
        val sparkline = root.findViewById<ImageView>(R.id.analytics_widget_sparkline)
        assertEquals(View.VISIBLE, sparkline.visibility)
        assertTrue((sparkline.drawable as BitmapDrawable).bitmap.width > 0)
        val bitmap = provider.curveBitmap(context, oldBrewState, size, 23 * 60, detailed = false, nowMinutes = 15 * 60)
        assertTrue(opaquePixels(bitmap) > 20)
    }

    @Test
    fun layoutBIsTheExpandedFourByThreeChartWireframe() {
        val size = SizeF(396f, 381f)
        val root = render(size)
        assertEquals(R.layout.widget_analytics_expanded, provider.layoutForSize(size))
        val bedtimeCaption = root.findViewById<TextView>(R.id.analytics_widget_bedtime_caption)
        val bedtimeValue = root.findViewById<TextView>(R.id.analytics_widget_bedtime_value)
        assertNotNull(bedtimeCaption)
        assertNotNull(bedtimeValue)
        assertEquals(1, bedtimeCaption.maxLines)
        assertEquals(1, bedtimeValue.maxLines)
        assertEquals(context.getColor(R.color.widget_secondary_text), bedtimeCaption.currentTextColor)
        assertEquals(1, root.findViewById<TextView>(R.id.analytics_widget_caffeine_caption).maxLines)
        assertEquals(1, root.findViewById<TextView>(R.id.analytics_widget_week_label).maxLines)
        assertEquals(1, root.findViewById<TextView>(R.id.analytics_widget_delta).maxLines)
        val widthPx = (size.width * context.resources.displayMetrics.density).toInt()
        val heightPx = (size.height * context.resources.displayMetrics.density).toInt()
        root.measure(MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightPx, MeasureSpec.EXACTLY))
        root.layout(0, 0, widthPx, heightPx)
        val hero = root.findViewById<TextView>(R.id.analytics_widget_caffeine)
        val heroCaption = root.findViewById<TextView>(R.id.analytics_widget_caffeine_caption)
        val density = context.resources.displayMetrics.density
        assertTrue(hero.bottom <= heroCaption.top + density)
        assertTrue(bedtimeCaption.bottom <= bedtimeValue.top + density)
        assertEquals(bedtimeCaption.right, bedtimeValue.right)
        assertTrue(bedtimeCaption.right <= root.width - 16 * density)
        val chart = root.findViewById<ImageView>(R.id.analytics_widget_curve)
        assertNotNull(chart)
        val bitmap = (chart.drawable as BitmapDrawable).bitmap
        assertTrue(bitmap.width > 0)
        assertTrue(bitmap.height > 0)
        assertEquals(chartWidthPx(size, contentWidthDp = size.width - 32f), bitmap.width)
        assertEquals(((size.height - 132f) * context.resources.displayMetrics.density).toInt(), bitmap.height)
        val overlay = root.findViewById<ImageView>(R.id.analytics_widget_curve_overlay)
        val overlayBitmap = (overlay.drawable as BitmapDrawable).bitmap
        assertEquals(bitmap.width, overlayBitmap.width)
        assertEquals(bitmap.height, overlayBitmap.height)
        assertHeaderAbsent(root)
    }

    @Test
    @Config(sdk = [35], qualifiers = "xxhdpi")
    fun expandedPortugueseTopRowFits888MgAtThreeByThreeDensity() {
        val portuguese = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("pt-BR")) },
        )
        val size = SizeF(396f, 381f)
        val sample = state.copy(caffeine = state.caffeine.copy(nowMg = 888, bedtimeMg = 12))
        val root = provider.viewsForSize(portuguese, 1, listOf(brew), sample, size).apply(portuguese, null)
        val density = portuguese.resources.displayMetrics.density
        assertEquals(3f, density)
        val widthPx = (size.width * density).toInt()
        val heightPx = (size.height * density).toInt()
        root.measure(MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightPx, MeasureSpec.EXACTLY))
        root.layout(0, 0, widthPx, heightPx)

        val hero = root.findViewById<TextView>(R.id.analytics_widget_caffeine)
        val bedtimeCaption = root.findViewById<TextView>(R.id.analytics_widget_bedtime_caption)
        val bedtimeValue = root.findViewById<TextView>(R.id.analytics_widget_bedtime_value)
        assertEquals("888 mg", hero.text)
        assertEquals("Ao dormir · 23:00", bedtimeCaption.text)
        assertEquals("12 mg", bedtimeValue.text)
        listOf(hero, root.findViewById(R.id.analytics_widget_caffeine_caption), bedtimeCaption, bedtimeValue).forEach { text ->
            assertEquals(1, text.maxLines)
            assertEquals(1, text.layout.lineCount)
            assertTrue(text.layout.getLineWidth(0) <= text.width)
        }
        assertTrue(root.findViewById<View>(R.id.analytics_widget_hero_group).right <= root.findViewById<View>(R.id.analytics_widget_bedtime_group).left)
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
        assertEquals(R.layout.widget_analytics_wide, provider.layoutForSize(SizeF(396f, 254f)))
        assertEquals(R.layout.widget_analytics_expanded, provider.layoutForSize(SizeF(396f, 381f)))
        assertEquals(R.layout.widget_analytics_square, provider.layoutForSize(SizeF(200f, 254f)))
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
    fun expandedBedtimeValueHasReadableDayAndNightTextColors() {
        for (nightMode in listOf(false, true)) {
            val configuration = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (nightMode) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val themed = context.createConfigurationContext(configuration)
            val root = provider.viewsForSize(themed, 1, listOf(brew), state, SizeF(396f, 381f)).apply(themed, null)
            val value = root.findViewById<TextView>(R.id.analytics_widget_bedtime_value)
            assertEquals(themed.getColor(R.color.widget_primary_text), value.currentTextColor)
            assertTrue(contrast(value.currentTextColor, themed.getColor(R.color.widget_background)) >= 4.5)

            val high = state.copy(caffeine = state.caffeine.copy(bedtimeMg = 51))
            val highRoot = provider.viewsForSize(themed, 2, listOf(brew), high, SizeF(396f, 381f)).apply(themed, null)
            val highValue = highRoot.findViewById<TextView>(R.id.analytics_widget_bedtime_value)
            assertEquals(themed.getColor(R.color.widget_error_text), highValue.currentTextColor)
            assertTrue(contrast(highValue.currentTextColor, themed.getColor(R.color.widget_background)) >= 4.5)
        }
    }

    @Test
    fun bedtimeValueIsVisibleAndReadableInLayoutsAAndBForDayAndNight() {
        val valueState = state.copy(caffeine = state.caffeine.copy(bedtimeMg = 11))
        for (nightMode in listOf(false, true)) {
            val configuration = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (nightMode) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val themed = context.createConfigurationContext(configuration)
            for ((size, id) in listOf(
                SizeF(396f, 254f) to R.id.analytics_widget_bedtime,
                SizeF(396f, 381f) to R.id.analytics_widget_bedtime_value,
            )) {
                val root = provider.viewsForSize(themed, 1, listOf(brew), valueState, size).apply(themed, null)
                val bedtime = root.findViewById<TextView>(id)
                assertEquals(View.VISIBLE, bedtime.visibility)
                assertTrue(bedtime.text.isNotEmpty())
                assertTrue(contrast(bedtime.currentTextColor, themed.getColor(R.color.widget_background)) >= 4.5)
            }
        }
    }

    @Test
    @Config(sdk = [35])
    fun remoteViewsApplyNightColorsAfterBeingCreatedInDayMode() {
        val day = themed(Configuration.UI_MODE_NIGHT_NO)
        val night = themed(Configuration.UI_MODE_NIGHT_YES)
        for ((size, bedtimeId, curveId) in listOf(
            Triple(SizeF(396f, 254f), R.id.analytics_widget_bedtime, R.id.analytics_widget_sparkline),
            Triple(SizeF(396f, 381f), R.id.analytics_widget_bedtime_value, R.id.analytics_widget_curve),
        )) {
            for (bedtimeMg in listOf(11, 51)) {
                val valueState = state.copy(caffeine = state.caffeine.copy(bedtimeMg = bedtimeMg))
                val remoteViews = provider.viewsForSize(day, 1, listOf(brew), valueState, size)
                val root = remoteViews.apply(night, null)
                val bedtime = root.findViewById<TextView>(bedtimeId)
                assertEquals(
                    night.getColor(if (bedtimeMg > 50) R.color.widget_error_text else R.color.widget_primary_text),
                    bedtime.currentTextColor,
                )
                val curve = root.findViewById<ImageView>(curveId)
                assertEquals(night.getColor(R.color.widget_hero_text), tintedPixel(curve))
                if (size.height >= 330f) {
                    assertEquals(night.getColor(R.color.widget_secondary_text), tintedPixel(root.findViewById(R.id.analytics_widget_curve_overlay)))
                }
            }
        }
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
        assertEquals(R.string.last_brew_widget_label, context.packageManager.getReceiverInfo(ComponentName(context, BrewWidgetProvider::class.java), 0).labelRes)
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
        val expanded = provider.viewsForSize(portuguese, 1, listOf(brew), state, SizeF(396f, 381f)).apply(portuguese, null)
        assertEquals("Esta semana", expanded.findViewById<TextView>(R.id.analytics_widget_week_label).text)
        assertTrue(expanded.findViewById<TextView>(R.id.analytics_widget_bedtime_caption).text.toString().startsWith("Ao dormir ·"))
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
        val root = provider.viewsForSize(night, 1, listOf(brew), state, SizeF(396f, 381f)).apply(night, null)
        assertEquals(night.getColor(R.color.widget_hero_text), root.findViewById<TextView>(R.id.analytics_widget_caffeine).currentTextColor)
        assertEquals(night.getColor(R.color.widget_secondary_text), root.findViewById<TextView>(R.id.analytics_widget_caffeine_caption).currentTextColor)
        assertEquals(night.getColor(R.color.widget_secondary_text), root.findViewById<TextView>(R.id.analytics_widget_week_label).currentTextColor)
        assertEquals(night.getColor(R.color.widget_secondary_text), root.findViewById<TextView>(R.id.analytics_widget_delta).currentTextColor)
        assertEquals(night.getColor(R.color.widget_primary_text), root.findViewById<TextView>(R.id.analytics_widget_week).currentTextColor)
        assertNotEquals(night.getColor(R.color.widget_primary_text), root.findViewById<TextView>(R.id.analytics_widget_caffeine).currentTextColor)
    }

    @Test
    fun chartLineColorAdaptsToDayAndNightContexts() {
        val size = SizeF(396f, 381f)
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

    @Test
    fun chartPlotsTwoBrewsInsideTheNowToBedtimeWindow() {
        val chartNow = Instant.parse("2026-11-17T15:00:00Z")
        val morning = brew.copy(id = "morning", startedAt = chartNow.minusSeconds(6 * 3600L).toEpochMilli())
        val afternoon = brew.copy(id = "afternoon", startedAt = chartNow.minusSeconds(2 * 3600L).toEpochMilli())
        val chartState = AnalyticsViewModel.summarize(listOf(morning, afternoon), emptyList(), chartNow, ZoneId.of("UTC"))
        val nowMinutes = 15 * 60
        val (startHour, bedtimeHour) = AnalyticsWidgetProvider.chartWindowHours(chartState, nowMinutes, 23 * 60)
        assertTrue(startHour < nowMinutes / 60.0)
        assertTrue(nowMinutes / 60.0 < bedtimeHour)

        val bitmap = provider.curveBitmap(context, chartState, SizeF(396f, 381f), 23 * 60, detailed = false, nowMinutes = nowMinutes)
        assertTrue(opaquePixels(bitmap) > 20)
    }

    @Test
    @Config(sdk = [35])
    fun appEnglishLocaleLocalizesAllExpandedWidgetStrings() {
        val localeManager = context.getSystemService(LocaleManager::class.java)
        val previous = localeManager.applicationLocales
        try {
            localeManager.applicationLocales = LocaleList.forLanguageTags("en-US")
            val widgetContext = AnalyticsWidgetProvider.localizedContext(context)
            val root = provider.viewsForSize(widgetContext, 1, listOf(brew), state, SizeF(396f, 381f)).apply(widgetContext, null)
            assertEquals("caffeine now", root.findViewById<TextView>(R.id.analytics_widget_caffeine_caption).text)
            assertEquals("This week", root.findViewById<TextView>(R.id.analytics_widget_week_label).text)
            assertTrue(root.findViewById<TextView>(R.id.analytics_widget_bedtime_caption).text.toString().startsWith("Bedtime ·"))
            assertTrue(root.findViewById<TextView>(R.id.analytics_widget_delta).text.toString().matches(Regex("[↓↑·] \\d+ g")))
        } finally {
            localeManager.applicationLocales = previous
        }
    }

    private fun render(size: SizeF): View = provider
        .viewsForSize(context, 1, listOf(brew), state, size)
        .apply(context, null)

    private fun tintedPixel(image: ImageView): Int {
        val output = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val white = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { setPixel(0, 0, Color.WHITE) }
        Canvas(output).drawBitmap(white, 0f, 0f, Paint().apply { colorFilter = image.colorFilter })
        return output.getPixel(0, 0)
    }

    private fun themed(nightMode: Int): Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        },
    )

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

    private fun contrast(foreground: Int, background: Int): Double {
        fun luminance(color: Int): Double {
            fun channel(value: Int): Double {
                val normalized = value / 255.0
                return if (normalized <= 0.04045) normalized / 12.92 else ((normalized + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(android.graphics.Color.red(color)) +
                0.7152 * channel(android.graphics.Color.green(color)) +
                0.0722 * channel(android.graphics.Color.blue(color))
        }
        val first = luminance(foreground)
        val second = luminance(background)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    private fun opaquePixels(bitmap: android.graphics.Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { android.graphics.Color.alpha(it) > 0 }
    }

    private fun android.graphics.Bitmap.pixels(): Set<Int> = buildSet {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        addAll(pixels.toList())
    }
}
