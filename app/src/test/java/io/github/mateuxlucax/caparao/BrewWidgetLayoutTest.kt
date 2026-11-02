package io.github.mateuxlucax.caparao

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import java.util.Locale
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
    fun savedAndEmptyStatesFitAtMinimumInBothThemesAndLocales() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val minWidth = base.resources.getDimensionPixelSize(R.dimen.widget_min_width)
        val minHeight = base.resources.getDimensionPixelSize(R.dimen.widget_min_height)
        val minimumActionHeight = (48 * base.resources.displayMetrics.density).roundToInt()
        val brew = Brew("layout", BrewMode.ESPRESSO, 0L, 28_000L, 18.0, 36.0, 4_000L, 1.3, 2.4, false)

        for (locale in listOf(Locale.US, Locale.forLanguageTag("pt-BR"))) {
            for (night in listOf(false, true)) {
                val context = configured(base, locale, night)
                for (latest in listOf(brew, null)) {
                    assertContrast(context)
                    val root = BrewWidgetProvider().views(context, 123, latest, 28_000L).apply(context, null)
                    root.measure(
                        View.MeasureSpec.makeMeasureSpec(minWidth, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(minHeight, View.MeasureSpec.EXACTLY),
                    )
                    root.layout(0, 0, minWidth, minHeight)

                    val action = root.findViewById<View>(R.id.widget_action)
                    assertTrue("$locale night=$night action height ${action.height}", action.height >= minimumActionHeight)
                    assertTrue("$locale night=$night action bottom ${action.bottom}", action.bottom <= minHeight - root.paddingBottom)
                    assertNoEllipsis(root.findViewById(R.id.widget_action_label), locale, night)

                    if (latest == null) {
                        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_last_brew).visibility)
                        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_empty_content).visibility)
                        assertNoEllipsis(root.findViewById(R.id.widget_empty_title), locale, night)
                        assertNoEllipsis(root.findViewById(R.id.widget_empty_text), locale, night)
                    } else {
                        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_last_brew).visibility)
                        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_empty_content).visibility)
                        assertNoEllipsis(root.findViewById(R.id.widget_method), locale, night)
                        assertNoEllipsis(root.findViewById(R.id.widget_age), locale, night)
                    }
                }
            }
        }
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
