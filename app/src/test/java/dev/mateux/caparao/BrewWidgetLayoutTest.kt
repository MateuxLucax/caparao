package dev.mateux.caparao

import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.ui.widget.shortcutForWidget
import dev.mateux.caparao.ui.components.LauncherShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrewWidgetLayoutTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val brew get() = Brew("filter", BrewMode.FILTER, System.currentTimeMillis() - 59 * 60_000L, 28_000L, 15.0, 250.0, null, 1.3, 2.4, false, coffee = "Fazenda Ambiental")
    private fun views(brew: Brew?, small: Boolean = false, wide: Boolean = false, widthDp: Float = 380f) =
        BrewWidgetProvider().views(context, 1, brew, wide = wide, small = small, widthDp = widthDp).apply(context, null)

    @Test fun oneByOneIsIconOnlyAndRepeatsLastMethod() {
        val root = views(brew, small = true)
        assertNotNull(root.findViewById<View>(R.id.widget_small_icon))
        assertEquals(null, root.findViewById<View>(R.id.widget_coffee))
        assertEquals(context.getString(R.string.widget_content_description, "Fazenda Ambiental"), root.contentDescription)
        val circle = root.findViewById<View>(R.id.widget_small_icon).parent as View
        assertEquals((48 * context.resources.displayMetrics.density).toInt(), circle.layoutParams.width)
        assertEquals((48 * context.resources.displayMetrics.density).toInt(), circle.layoutParams.height)
        assertEquals(Gravity.CENTER, (circle.layoutParams as android.widget.FrameLayout.LayoutParams).gravity)
        assertEquals((28 * context.resources.displayMetrics.density).toInt(), root.findViewById<View>(R.id.widget_small_icon).layoutParams.width)
    }

    @Test fun twoByOneShowsCoffeeAndRepeatsLastMethod() {
        val root = views(brew)
        assertEquals("Fazenda Ambiental", root.findViewById<TextView>(R.id.widget_coffee).text)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_meta).visibility)
        assertEquals("15 g → 250 g", root.findViewById<TextView>(R.id.widget_meta).text)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_action_label).visibility)
        assertEquals(LauncherShortcut.FILTER, shortcutForWidget(BrewMode.FILTER))
    }

    @Test fun textColumnUsesAllRemainingWidth() {
        val layout = LayoutInflater.from(context).inflate(R.layout.widget_brew, null)
        val column = layout.findViewById<View>(R.id.widget_text_column)
        val params = column.layoutParams as android.widget.LinearLayout.LayoutParams
        assertEquals(1f, params.weight)
        assertEquals((12 * context.resources.displayMetrics.density).toInt(), params.marginStart)
        assertEquals(0, params.marginEnd)
        assertEquals(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, layout.findViewById<View>(R.id.widget_action_button).layoutParams.width)
        val action = layout.findViewById<View>(R.id.widget_action_button)
        val actionLabel = layout.findViewById<TextView>(R.id.widget_action_label)
        val maxPillWidth = (20 + 4) * context.resources.displayMetrics.density + actionLabel.maxWidth + action.paddingLeft + action.paddingRight
        assertTrue("pill uses ${maxPillWidth / context.resources.displayMetrics.density}dp", maxPillWidth / context.resources.displayMetrics.density <= 112f)
        assertEquals(16f, layout.findViewById<TextView>(R.id.widget_coffee).textSize / context.resources.displayMetrics.scaledDensity, 0.1f)
        assertEquals(13f, layout.findViewById<TextView>(R.id.widget_meta).textSize / context.resources.displayMetrics.scaledDensity, 0.1f)
    }

    @Test fun widgetSizeBucketsUseActualWidth() {
        fun at(width: Float) = BrewWidgetProvider().viewsForWidth(context, 1, brew, width).apply(context, null)
        val small = at(100f)
        val twoCell = at(190f)
        val threeCell = at(293f)
        val fourCell = at(400f)
        assertNotNull(small.findViewById<View>(R.id.widget_small_icon))
        assertEquals(View.GONE, twoCell.findViewById<View>(R.id.widget_action_label).visibility)
        assertEquals("15 g → 250 g", twoCell.findViewById<TextView>(R.id.widget_meta).text)
        assertEquals(View.GONE, threeCell.findViewById<View>(R.id.widget_action_label).visibility)
        assertEquals("Filter · 15 g → 250 g · 59 min ago", threeCell.findViewById<TextView>(R.id.widget_meta).text)
        assertEquals(View.VISIBLE, fourCell.findViewById<View>(R.id.widget_action_label).visibility)
    }

    @Test fun threeCellOptionsSelectBWithFullMeta() {
        val options = android.os.Bundle().apply {
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 293)
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 119)
        }
        val root = BrewWidgetProvider().viewsForOptions(context, 1, brew, options).apply(context, null)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_action_label).visibility)
        assertTrue(root.findViewById<TextView>(R.id.widget_meta).text.contains("Filter"))
        assertTrue(root.findViewById<TextView>(R.id.widget_meta).text.contains("250 g"))
        assertTrue(root.findViewById<TextView>(R.id.widget_meta).text.contains("59 min ago"))
    }

    @Test fun threeAndFourByOneShowMethodTimeAndTextAction() {
        val root = views(brew, wide = true)
        assertEquals("Fazenda Ambiental", root.findViewById<TextView>(R.id.widget_coffee).text)
        assertTrue(root.findViewById<TextView>(R.id.widget_meta).text.contains("Filter"))
        assertTrue(root.findViewById<TextView>(R.id.widget_meta).text.contains("250 g"))
        assertTrue(root.findViewById<TextView>(R.id.widget_meta).text.contains("59 min ago"))
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_action_label).visibility)
        assertEquals(LauncherShortcut.FILTER, shortcutForWidget(BrewMode.FILTER))
    }

    @Test fun emptyShowsLocalizedEmptyAndStartsChooser() {
        val root = views(null)
        assertEquals("No brews yet", root.findViewById<TextView>(R.id.widget_empty_title).text)
        assertEquals(2, root.findViewById<TextView>(R.id.widget_empty_title).maxLines)
        assertEquals(android.text.TextUtils.TruncateAt.END, root.findViewById<TextView>(R.id.widget_empty_title).ellipsize)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_action_label).visibility)
        assertEquals(context.getString(R.string.widget_empty_start), root.contentDescription)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_action_button).visibility)
        assertEquals(LauncherShortcut.WIDGET_HOME, shortcutForWidget(null))
    }

    @Test fun emptyPortugueseTitleFitsAtTwoByOneWithoutEllipsis() {
        val config = android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale.forLanguageTag("pt-BR"))
        }
        val portuguese = context.createConfigurationContext(config)
        val root = BrewWidgetProvider().viewsForWidth(portuguese, 1, null, 190f).apply(portuguese, null)
        val title = root.findViewById<TextView>(R.id.widget_empty_title)
        assertEquals("Nenhum preparo", title.text)
        assertEquals(2, title.maxLines)
        assertEquals(android.text.TextUtils.TruncateAt.END, title.ellipsize)
        assertEquals(14f, title.textSize / portuguese.resources.displayMetrics.scaledDensity, 0.1f)
        val density = portuguese.resources.displayMetrics.density
        root.measure(
            View.MeasureSpec.makeMeasureSpec((190 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((119 * density).toInt(), View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val textLayout = requireNotNull(title.layout)
        assertTrue(textLayout.lineCount <= 2)
        assertEquals(title.text.length, textLayout.getLineEnd(textLayout.lineCount - 1))
        for (line in 0 until textLayout.lineCount) assertEquals(0, textLayout.getEllipsisCount(line))
    }

    @Test fun pickerUsesTwoByOnePillAndMatchingPreview() {
        val receiver = context.packageManager.getReceiverInfo(android.content.ComponentName(context, BrewWidgetProvider::class.java), PackageManager.GET_META_DATA)
        val parser = context.resources.getXml(receiver.metaData.getInt("android.appwidget.provider"))
        try {
            while (parser.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) parser.next()
            val ns = "http://schemas.android.com/apk/res/android"
            assertEquals(-1, parser.getAttributeResourceValue(ns, "label", -1))
            assertEquals(context.getString(R.string.widget_description), context.getString(parser.getAttributeResourceValue(ns, "description", 0)))
            assertEquals(2, parser.getAttributeIntValue(ns, "targetCellWidth", 0))
            assertEquals(1, parser.getAttributeIntValue(ns, "targetCellHeight", 0))
            assertEquals("widget_brew", context.resources.getResourceEntryName(parser.getAttributeResourceValue(ns, "previewLayout", 0)))
            assertEquals(R.layout.widget_brew, parser.getAttributeResourceValue(ns, "initialLayout", 0))
        } finally { parser.close() }
        assertEquals(R.string.last_brew_widget_label, receiver.labelRes)
        assertEquals("Last brew", receiver.loadLabel(context.packageManager).toString())
        val preview = LayoutInflater.from(context).inflate(R.layout.widget_brew, null)
        assertEquals("Fazenda Ambiental", preview.findViewById<TextView>(R.id.widget_coffee).text)
        assertNotNull(preview.findViewById<ImageView>(R.id.widget_method_icon).drawable)
        assertNotNull(preview.findViewById<View>(R.id.widget_action_button))
        assertEquals((56 * context.resources.displayMetrics.density).toInt(), context.resources.getDimensionPixelSize(R.dimen.widget_min_height))
    }

    @Test fun twoByOneUsesTheSameDoseYieldLayoutForEnglishAndPortuguese() {
        val config = android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale.forLanguageTag("pt-BR"))
        }
        val portuguese = context.createConfigurationContext(config)
        val englishRoot = views(brew)
        val portugueseRoot = BrewWidgetProvider().views(portuguese, 2, brew).apply(portuguese, null)
        for (root in listOf(englishRoot, portugueseRoot)) {
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_meta).visibility)
            assertEquals("15 g → 250 g", root.findViewById<TextView>(R.id.widget_meta).text)
            assertEquals(16f, root.findViewById<TextView>(R.id.widget_coffee).textSize / context.resources.displayMetrics.scaledDensity, 0.1f)
            assertEquals(13f, root.findViewById<TextView>(R.id.widget_meta).textSize / context.resources.displayMetrics.scaledDensity, 0.1f)
        }
    }

    @Test fun pickerDescriptionIsTranslatedInBrazilianPortuguese() {
        val configuration = android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale.forLanguageTag("pt-BR"))
        }
        val portuguese = context.createConfigurationContext(configuration)
        assertEquals("Prepare seu último café de novo", portuguese.getString(R.string.widget_description))
        assertEquals("Último preparo", portuguese.getString(R.string.last_brew_widget_label))
    }

}
