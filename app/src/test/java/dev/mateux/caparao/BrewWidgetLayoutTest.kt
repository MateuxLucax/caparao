package dev.mateux.caparao

import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
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
    private fun views(brew: Brew?, small: Boolean = false, wide: Boolean = false) =
        BrewWidgetProvider().views(context, 1, brew, wide = wide, small = small).apply(context, null)

    @Test fun oneByOneIsIconOnlyAndRepeatsLastMethod() {
        val root = views(brew, small = true)
        assertNotNull(root.findViewById<View>(R.id.widget_small_icon))
        assertEquals(null, root.findViewById<View>(R.id.widget_coffee))
        assertEquals(context.getString(R.string.widget_content_description, "Fazenda Ambiental"), root.contentDescription)
    }

    @Test fun twoByOneShowsCoffeeAndRepeatsLastMethod() {
        val root = views(brew)
        assertEquals("Fazenda Ambiental", root.findViewById<TextView>(R.id.widget_coffee).text)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_meta).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_action_label).visibility)
        assertEquals(LauncherShortcut.FILTER, shortcutForWidget(BrewMode.FILTER))
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
        assertEquals("No brews yet", root.findViewById<TextView>(R.id.widget_coffee).text)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_action_label).visibility)
        assertEquals(context.getString(R.string.widget_empty_start), root.contentDescription)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_action_button).visibility)
        assertEquals(LauncherShortcut.WIDGET_HOME, shortcutForWidget(null))
    }

    @Test fun pickerUsesTwoByOnePillAndMatchingPreview() {
        val receiver = context.packageManager.getReceiverInfo(android.content.ComponentName(context, BrewWidgetProvider::class.java), PackageManager.GET_META_DATA)
        val parser = context.resources.getXml(receiver.metaData.getInt("android.appwidget.provider"))
        try {
            while (parser.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) parser.next()
            val ns = "http://schemas.android.com/apk/res/android"
            assertEquals("Last brew", context.getString(parser.getAttributeResourceValue(ns, "label", 0)))
            assertEquals(context.getString(R.string.widget_description), context.getString(parser.getAttributeResourceValue(ns, "description", 0)))
            assertEquals(2, parser.getAttributeIntValue(ns, "targetCellWidth", 0))
            assertEquals(1, parser.getAttributeIntValue(ns, "targetCellHeight", 0))
            assertEquals("widget_brew", context.resources.getResourceEntryName(parser.getAttributeResourceValue(ns, "previewLayout", 0)))
            assertEquals(R.layout.widget_brew, parser.getAttributeResourceValue(ns, "initialLayout", 0))
        } finally { parser.close() }
        val preview = LayoutInflater.from(context).inflate(R.layout.widget_brew, null)
        assertEquals("Fazenda Ambiental", preview.findViewById<TextView>(R.id.widget_coffee).text)
        assertNotNull(preview.findViewById<View>(R.id.widget_action_button))
        assertTrue(context.resources.getDimensionPixelSize(R.dimen.widget_min_height) <= 50 * context.resources.displayMetrics.density)
    }

}
