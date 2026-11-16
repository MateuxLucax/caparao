package dev.mateux.caparao

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.View
import android.view.LayoutInflater
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class BrewWidgetLayoutTest {
    @Test
    fun pickerUsesWidgetNameAndRealLocalizedPreviewResources() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = context.packageManager.getReceiverInfo(
            android.content.ComponentName(context, BrewWidgetProvider::class.java),
            PackageManager.GET_META_DATA,
        )
        val metadata = receiver.metaData
        val provider = context.resources.getXml(metadata.getInt("android.appwidget.provider"))
        try {
            var label: String? = null
            var previewLayout: String? = null
            var previewImage: String? = null
            var description: String? = null
            while (provider.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (provider.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && provider.name == "appwidget-provider") {
                    val attrs = provider
                    val android = "http://schemas.android.com/apk/res/android"
                    label = attrs.getAttributeResourceValue(android, "label", 0).takeIf { it != 0 }?.let(context::getString)
                    previewLayout = attrs.getAttributeResourceValue(android, "previewLayout", 0).takeIf { it != 0 }?.let { context.resources.getResourceEntryName(it) }
                    previewImage = attrs.getAttributeResourceValue(android, "previewImage", 0).takeIf { it != 0 }?.let { context.resources.getResourceEntryName(it) }
                    description = attrs.getAttributeResourceValue(android, "description", 0).takeIf { it != 0 }?.let(context::getString)
                    break
                }
                provider.next()
            }
            assertEquals(context.getString(R.string.widget_last_brew_title), label)
            assertEquals("widget_brew_preview", previewLayout)
            assertEquals("widget_brew_preview", previewImage)
            assertEquals(context.getString(R.string.widget_description), description)
            val preview = LayoutInflater.from(context).inflate(R.layout.widget_brew_preview, null)
            val width = context.resources.getDimensionPixelSize(R.dimen.widget_min_width)
            val height = context.resources.getDimensionPixelSize(R.dimen.widget_min_height)
            preview.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            preview.layout(0, 0, width, height)
            assertNotNull(preview)
            assertEquals(context.getString(R.string.widget_last_brew_title), preview.findViewById<TextView>(R.id.widget_title).text)
            assertEquals(context.getString(R.string.widget_preview_coffee), preview.findViewById<TextView>(R.id.widget_coffee).text)
            assertEquals(context.getString(R.string.widget_preview_brew_details), preview.findViewById<TextView>(R.id.widget_method).text)
            assertEquals(context.getString(R.string.widget_preview_time), preview.findViewById<TextView>(R.id.widget_time).text)
            assertEquals(context.getString(R.string.widget_brew_filter), preview.findViewById<TextView>(R.id.widget_action_label).text)
        } finally {
            provider.close()
        }
    }

    @Test
    fun savedAndEmptyStatesFitAtMinimumInBothThemesAndLocales() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val minWidth = base.resources.getDimensionPixelSize(R.dimen.widget_min_width)
        val minHeight = base.resources.getDimensionPixelSize(R.dimen.widget_min_height)
                    val minimumActionHeight = (30 * base.resources.displayMetrics.density).roundToInt()
        val brew = Brew("layout", BrewMode.ESPRESSO, 0L, 28_000L, 18.0, 36.0, 4_000L, 1.3, 2.4, false)
        val frenchPress = brew.copy(id = "french-press", mode = BrewMode.FRENCH_PRESS, timeToFirstDropMs = null)

        for (locale in listOf(Locale.US, Locale.forLanguageTag("pt-BR"))) {
            for (night in listOf(false, true)) {
                val context = configured(base, locale, night)
                for (latest in listOf(brew, frenchPress, null)) {
                    assertContrast(context)
                    val root = BrewWidgetProvider().views(context, 123, latest).apply(context, null)
                    root.measure(
                        View.MeasureSpec.makeMeasureSpec(minWidth, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(minHeight, View.MeasureSpec.EXACTLY),
                    )
                    root.layout(0, 0, minWidth, minHeight)

                    val action = root.findViewById<View>(R.id.widget_action)
                    assertTrue("$locale night=$night action height ${action.height}", action.height >= minimumActionHeight)
                    assertEquals(minWidth - root.paddingLeft - root.paddingRight, action.width)
                    assertTrue(
                        "$locale night=$night bottom=${action.bottom}, height=$minHeight, " +
                            "title=${root.findViewById<View>(R.id.widget_title).height}, " +
                            "brew=${root.findViewById<View>(R.id.widget_last_brew).height}, time=${root.findViewById<View>(R.id.widget_time).height}",
                        action.bottom <= minHeight - root.paddingBottom,
                    )
                    assertNoEllipsis(root.findViewById(R.id.widget_action_label), locale, night)

                    if (latest == null) {
                        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_last_brew).visibility)
                        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_empty_content).visibility)
                        val emptyActionLabel = root.findViewById<TextView>(R.id.widget_action_label)
                        assertTrue("empty chooser action remains visible", emptyActionLabel.width > 0)
                        assertNoEllipsis(root.findViewById(R.id.widget_empty_title), locale, night)
                    } else {
                        assertEquals(root.paddingTop, root.findViewById<View>(R.id.widget_title).top)
                        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_last_brew).visibility)
                        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_empty_content).visibility)
                        assertNoEllipsis(root.findViewById(R.id.widget_method), locale, night)
                        if (latest.mode == BrewMode.FRENCH_PRESS) {
                            assertTrue(root.findViewById<TextView>(R.id.widget_method).text.startsWith(context.getString(R.string.mode_french_press_short)))
                            assertEquals(context.getString(R.string.widget_brew_espresso), root.findViewById<TextView>(R.id.widget_action_label).text)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun lastBrewAndEmptyStatesFitExpandedWidgetSizes() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val minWidth = base.resources.getDimensionPixelSize(R.dimen.widget_min_width)
        val minHeight = base.resources.getDimensionPixelSize(R.dimen.widget_min_height)
        val sizes = listOf(Triple(minWidth, minHeight, false), Triple(180, 40, true), Triple(250, 40, true), Triple(180, 110, true))
        val brew = Brew("expanded", BrewMode.FILTER, 0L, 28_000L, 15.0, 250.0, null, 1.3, 2.4, false)
        for (locale in listOf(Locale.US, Locale.forLanguageTag("pt-BR"))) {
            for (night in listOf(false, true)) {
                val context = configured(base, locale, night)
                for ((width, height, wide) in sizes) {
                    for (latest in listOf(brew, null)) {
                        val root = BrewWidgetProvider().views(context, 123, latest, wide = wide).apply(context, null)
                        root.measure(
                            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                        )
                        root.layout(0, 0, width, height)
                        val saved = latest != null
                        assertEquals(if (saved) View.VISIBLE else View.GONE, root.findViewById<View>(R.id.widget_title).visibility)
                        assertEquals(if (saved) View.VISIBLE else View.GONE, root.findViewById<View>(R.id.widget_last_brew).visibility)
                        assertEquals(if (saved) View.GONE else View.VISIBLE, root.findViewById<View>(R.id.widget_empty_content).visibility)
                        assertEquals(
                            context.getString(if (saved) R.string.widget_brew_espresso else R.string.widget_brew_coffee),
                            root.findViewById<TextView>(R.id.widget_action_label).text,
                        )
                        assertNoEllipsis(root.findViewById(R.id.widget_action_label), locale, night)
                        if (saved) {
                            assertEquals(context.getString(R.string.widget_last_brew_title), root.findViewById<TextView>(R.id.widget_title).text)
                            if (wide) {
                                val summary = root.findViewById<TextView>(R.id.widget_method)
                                assertEquals(context.getString(R.string.widget_brew_short_summary, 15.0, 250.0), summary.text)
                                assertEquals(1, summary.layout.lineCount)
                                assertNoEllipsis(summary, locale, night)
                            } else {
                                assertEquals(
                                    context.getString(R.string.widget_brew_summary, context.getString(R.string.mode_filter), 15.0, 250.0),
                                    root.findViewById<TextView>(R.id.widget_method).text,
                                )
                            }
                            if (!wide) {
                                assertTrue(root.findViewById<TextView>(R.id.widget_coffee).text.isNotBlank())
                                assertTrue(root.findViewById<View>(R.id.widget_time).top > root.findViewById<View>(R.id.widget_method).top)
                                assertEquals(root.width - root.paddingRight - root.paddingLeft, root.findViewById<View>(R.id.widget_action).width)
                            } else {
                                assertTrue(root.findViewById<View>(R.id.widget_action).left > root.findViewById<View>(R.id.widget_method).left)
                            }
                            assertEquals(context.getString(R.string.mode_filter), root.findViewById<TextView>(R.id.widget_coffee).text)
                            assertNoEllipsis(root.findViewById(R.id.widget_method), locale, night)
                        } else {
                            assertEquals(context.getString(R.string.widget_empty_title), root.findViewById<TextView>(R.id.widget_empty_title).text)
                            assertNoEllipsis(root.findViewById(R.id.widget_empty_title), locale, night)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun oneByOneWidgetShowsOnlyAnAccessibleCoffeeIcon() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = BrewWidgetProvider().views(context, 123, null, small = true).apply(context, null)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 40, 40)

        assertEquals(context.getString(R.string.widget_brew_coffee), root.contentDescription)
        assertNotNull(root.findViewById<View>(R.id.widget_small_icon))
        assertEquals(null, root.findViewById<View>(R.id.widget_title))
        assertEquals(null, root.findViewById<View>(R.id.widget_action_label))
    }

    private fun configured(base: Context, locale: Locale, night: Boolean): Context =
        base.createConfigurationContext(
            Configuration(base.resources.configuration).apply {
                setLocale(locale)
                val nightMode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            },
        )

    private fun assertContrast(context: Context) {
        assertContrastAtLeast(context, R.color.widget_primary_text, R.color.widget_background, 4.5)
        assertContrastAtLeast(context, R.color.widget_secondary_text, R.color.widget_background, 4.5)
        assertContrastAtLeast(context, R.color.widget_action_text, R.color.widget_action_background, 4.5)
        assertContrastAtLeast(context, R.color.widget_action_background, R.color.widget_background, 3.0)
        assertContrastAtLeast(context, R.color.widget_icon_foreground, R.color.widget_icon_container, 3.0)
    }

    private fun assertContrastAtLeast(context: Context, foreground: Int, background: Int, minimum: Double) {
        val contrast = ColorUtils.calculateContrast(context.getColor(foreground), context.getColor(background))
        assertTrue("${context.resources.configuration} contrast=$contrast, expected >=$minimum", contrast >= minimum)
    }

    private fun assertNoEllipsis(view: TextView, locale: Locale, night: Boolean) {
        val layout = view.layout
        assertNotNull("$locale night=$night missing text layout", layout)
        for (line in 0 until layout.lineCount) {
            assertEquals("$locale night=$night truncated: ${view.text}", 0, layout.getEllipsisCount(line))
        }
    }
}
