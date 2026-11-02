package io.github.mateuxlucax.caparao

import android.content.Context
import android.content.res.Configuration
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import java.util.Locale
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class BrewWidgetLayoutTest {
    @Test
    fun savedBrewActionFitsAtTheAdvertisedMinimumInBothLocales() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val minWidth = base.resources.getDimensionPixelSize(R.dimen.widget_min_width)
        val minHeight = base.resources.getDimensionPixelSize(R.dimen.widget_min_height)
        val minimumActionHeight = (38 * base.resources.displayMetrics.density).roundToInt()
        val brew = Brew("layout", BrewMode.ESPRESSO, 0L, 28_000L, 18.0, 36.0, 4_000L, 1.3, 2.4, false)

        listOf(Locale.US, Locale.forLanguageTag("pt-BR")).forEach { locale ->
            val context = base.createConfigurationContext(
                Configuration(base.resources.configuration).apply { setLocale(locale) },
            )
            val root = BrewWidgetProvider().views(context, 123, brew, 28_000L).apply(context, null)
            root.measure(
                View.MeasureSpec.makeMeasureSpec(minWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(minHeight, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, minWidth, minHeight)

            val action = root.findViewById<View>(R.id.widget_action)
            assertTrue("$locale action height ${action.height}", action.height >= minimumActionHeight)
            assertTrue("$locale action bottom ${action.bottom}", action.bottom <= minHeight - root.paddingBottom)
        }
    }
}
