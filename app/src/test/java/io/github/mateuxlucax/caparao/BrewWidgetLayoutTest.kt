package io.github.mateuxlucax.caparao

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.ui.widget.BrewWidgetDayBucket
import io.github.mateuxlucax.caparao.ui.widget.BrewWidgetWeek
import java.time.LocalDate
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
                    val root = BrewWidgetProvider().views(context, 123, latest).apply(context, null)
                    root.measure(
                        View.MeasureSpec.makeMeasureSpec(minWidth, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(minHeight, View.MeasureSpec.EXACTLY),
                    )
                    root.layout(0, 0, minWidth, minHeight)

                    val iconContainer = root.findViewById<View>(R.id.widget_method_icon_container)
                    val methodIcon = root.findViewById<View>(R.id.widget_method_icon)
                    // The 56 dp container around the 32 dp icon leaves 12 dp on every side: the PR's clearance.
                    val requiredIconClearance = (12 * base.resources.displayMetrics.density).roundToInt()
                    assertTrue(
                        "icon left clearance ${methodIcon.left}px, expected at least ${requiredIconClearance}px",
                        methodIcon.left >= requiredIconClearance,
                    )
                    assertTrue(
                        "icon top clearance: container=${iconContainer.height}, icon=${methodIcon.height}, " +
                            "top=${methodIcon.top}, min=$requiredIconClearance",
                        methodIcon.top >= requiredIconClearance,
                    )
                    assertTrue(
                        "icon right clearance ${iconContainer.width - methodIcon.right}px, " +
                            "expected at least ${requiredIconClearance}px",
                        iconContainer.width - methodIcon.right >= requiredIconClearance,
                    )
                    assertTrue(
                        "icon bottom clearance ${iconContainer.height - methodIcon.bottom}px, " +
                            "expected at least ${requiredIconClearance}px",
                        iconContainer.height - methodIcon.bottom >= requiredIconClearance,
                    )
                    assertEquals("icon centered horizontally", methodIcon.left, iconContainer.width - methodIcon.right)
                    assertEquals("icon centered vertically", methodIcon.top, iconContainer.height - methodIcon.bottom)

                    val action = root.findViewById<View>(R.id.widget_action)
                    assertTrue("$locale night=$night action height ${action.height}", action.height >= minimumActionHeight)
                    assertTrue("$locale night=$night action bottom ${action.bottom}", action.bottom <= minHeight - root.paddingBottom)
                    assertNoEllipsis(root.findViewById(R.id.widget_action_label), locale, night)

                    if (latest == null) {
                        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_last_brew).visibility)
                        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_empty_content).visibility)
                        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_action_icon).visibility)
                        val emptyActionLabel = root.findViewById<TextView>(R.id.widget_action_label)
                        assertTrue(
                            "empty chooser action is centered",
                            abs(emptyActionLabel.left + emptyActionLabel.width / 2 - action.width / 2) <= 2,
                        )
                        assertNoEllipsis(root.findViewById(R.id.widget_empty_title), locale, night)
                    } else {
                        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_last_brew).visibility)
                        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_empty_content).visibility)
                        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_action_icon).visibility)
                        assertNoEllipsis(root.findViewById(R.id.widget_method), locale, night)
                    }
                }
            }
        }
    }

    @Test
    fun weeklyLayoutFitsAtItsResponsiveMinimumInBothThemesAndLocales() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val minWidth = base.resources.getDimensionPixelSize(R.dimen.widget_weekly_min_width)
        val minHeight = base.resources.getDimensionPixelSize(R.dimen.widget_weekly_min_height)
        val saved = sampleWeek()
        val empty = sampleWeek(empty = true)
        val statIds = listOf(
            R.id.widget_weekly_brews_label,
            R.id.widget_weekly_brews_value,
            R.id.widget_weekly_beverage_label,
            R.id.widget_weekly_beverage_value,
            R.id.widget_weekly_dose_label,
            R.id.widget_weekly_dose_value,
            R.id.widget_weekly_caffeine_label,
            R.id.widget_weekly_caffeine_value,
        )
        val dayIds = listOf(
            R.id.widget_weekly_day_0,
            R.id.widget_weekly_day_1,
            R.id.widget_weekly_day_2,
            R.id.widget_weekly_day_3,
            R.id.widget_weekly_day_4,
            R.id.widget_weekly_day_5,
            R.id.widget_weekly_day_6,
        )

        for (locale in listOf(Locale.US, Locale.forLanguageTag("pt-BR"))) {
            for (night in listOf(false, true)) {
                val context = configured(base, locale, night)
                assertContrast(context)

                val savedRoot = BrewWidgetProvider().weeklyViews(context, 123, BrewMode.FILTER, saved).apply(context, null)
                savedRoot.measure(
                    View.MeasureSpec.makeMeasureSpec(minWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(minHeight, View.MeasureSpec.EXACTLY),
                )
                savedRoot.layout(0, 0, minWidth, minHeight)

                assertEquals(View.VISIBLE, savedRoot.findViewById<View>(R.id.widget_weekly_content).visibility)
                assertEquals(View.GONE, savedRoot.findViewById<View>(R.id.widget_weekly_empty).visibility)
                assertNoEllipsis(savedRoot.findViewById(R.id.widget_weekly_title), locale, night)
                assertNoEllipsis(savedRoot.findViewById(R.id.widget_weekly_estimates), locale, night)
                (statIds + dayIds).forEach { id -> assertNoEllipsis(savedRoot.findViewById(id), locale, night) }

                val chart = savedRoot.findViewById<ImageView>(R.id.widget_weekly_chart)
                assertNotNull("$locale night=$night chart image", chart.drawable)
                assertEquals(context.getColor(R.color.widget_action_background), chart.imageTintList?.defaultColor)
                val bitmap = (chart.drawable as android.graphics.drawable.BitmapDrawable).bitmap
                assertTrue("bitmap allocation ${bitmap.allocationByteCount}", bitmap.allocationByteCount <= 256 * 1024)

                val emptyRoot = BrewWidgetProvider().weeklyViews(context, 123, null, empty).apply(context, null)
                emptyRoot.measure(
                    View.MeasureSpec.makeMeasureSpec(minWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(minHeight, View.MeasureSpec.EXACTLY),
                )
                emptyRoot.layout(0, 0, minWidth, minHeight)
                assertEquals(View.GONE, emptyRoot.findViewById<View>(R.id.widget_weekly_content).visibility)
                assertEquals(View.VISIBLE, emptyRoot.findViewById<View>(R.id.widget_weekly_empty).visibility)
                assertNoEllipsis(emptyRoot.findViewById(R.id.widget_weekly_title), locale, night)
                assertNoEllipsis(emptyRoot.findViewById(R.id.widget_weekly_empty), locale, night)
            }
        }
    }

    private fun sampleWeek(empty: Boolean = false): BrewWidgetWeek {
        val counts = if (empty) List(7) { 0 } else listOf(1, 2, 0, 3, 0, 1, 2)
        val anchor = LocalDate.of(2026, 10, 18)
        return BrewWidgetWeek(
            buckets = counts.mapIndexed { index, count -> BrewWidgetDayBucket(anchor.minusDays((6 - index).toLong()), count) },
            brewCount = counts.sum(),
            estimatedBeverageMl = if (empty) 0.0 else 1_234.0,
            doseGrams = if (empty) 0.0 else 78.0,
            estimatedCaffeineMg = if (empty) 0.0 else 945.0,
        )
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
