package dev.mateux.caparao

import android.content.Context
import android.content.res.Configuration
import android.util.SizeF
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeekWidgetLayoutTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val provider = WeekWidgetProvider()
    private val brew = Brew("week", BrewMode.FILTER, System.currentTimeMillis() - 60_000, 30_000, 18.0, 300.0, null, 1.0, 2.0, false)
    private fun render(width: Float, height: Float, data: List<Brew> = listOf(brew)) = provider.render(context, 42, data, SizeF(width, height)).apply(context, null)

    @Test fun standardAndNarrowHaveChartAndNoHeader() {
        val standard = render(330f, 110f)
        assertNotNull(standard.findViewById<TextView>(R.id.week_count))
        assertNotNull(standard.findViewById<ImageView>(R.id.week_chart))
        assertEquals(View.VISIBLE, standard.findViewById<TextView>(R.id.week_stats).visibility)
        assertNotNull(standard.findViewById<TextView>(R.id.week_day_6))
        assertTrue(standard.findViewById<View>(R.id.week_root).hasOnClickListeners())
        val narrow = render(220f, 110f)
        assertNotNull(narrow.findViewById<ImageView>(R.id.week_narrow_chart))
        assertEquals(View.GONE, narrow.findViewById<TextView>(R.id.week_narrow_stats).visibility)
        assertNotNull(narrow.findViewById<TextView>(R.id.week_narrow_day_6))
    }

    @Test fun tallAndEmptyShowRequiredContent() {
        val tall = render(330f, 230f)
        assertNotNull(tall.findViewById<TextView>(R.id.week_day_6))
        val empty = render(330f, 110f, emptyList())
        assertEquals("0", empty.findViewById<TextView>(R.id.week_count).text.toString())
        assertEquals(View.VISIBLE, empty.findViewById<TextView>(R.id.week_empty_text).visibility)
    }

    @Test fun pluralsAndPickerMetadataAreLocalized() {
        assertEquals("1 brew", context.resources.getQuantityString(R.plurals.week_brews, 1, 1))
        assertEquals("9 brews", context.resources.getQuantityString(R.plurals.week_brews, 9, 9))
        val pt = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(java.util.Locale("pt", "BR")) })
        assertEquals("1 preparo", pt.resources.getQuantityString(R.plurals.week_brews, 1, 1))
        assertEquals("9 preparos", pt.resources.getQuantityString(R.plurals.week_brews, 9, 9))
        assertEquals("Semana", pt.getString(R.string.week_widget_name))
        assertEquals("preparos", pt.getString(R.string.week_preview_count_unit))
        assertEquals("143 g de café · 2,1 L", pt.getString(R.string.week_preview_stats))
    }

    @Test fun weekdayInitialsEndOnTuesdayInEnglishAndBrazilianPortuguese() {
        val tuesday = java.time.LocalDate.parse("2026-11-17")
        val pt = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(java.util.Locale("pt", "BR")) })
        val en = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.ENGLISH) })
        fun labels(ctx: Context): List<String> = provider.render(ctx, 43, emptyList(), SizeF(330f, 110f), today = tuesday).apply(ctx, null).let { root -> (0..6).map { root.findViewById<TextView>(R.id.week_day_0 + it).text.toString() } }
        assertEquals(listOf("Q", "Q", "S", "S", "D", "S", "T"), labels(pt))
        assertEquals(listOf("W", "T", "F", "S", "S", "M", "T"), labels(en))
        val styled = provider.render(pt, 44, emptyList(), SizeF(330f, 110f), today = tuesday).apply(pt, null).findViewById<TextView>(R.id.week_day_6).text
        assertTrue((styled as android.text.Spanned).getSpans(0, styled.length, android.text.style.StyleSpan::class.java).isNotEmpty())
    }
}
