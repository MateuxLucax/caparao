package io.github.mateuxlucax.caparao.ui.screens
import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.luminance
import io.github.mateuxlucax.caparao.ui.components.PressArt
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mateuxlucax.caparao.BrewWidgetProvider
import io.github.mateuxlucax.caparao.BuildConfig
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.data.export.BrewExportFiles
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticRecorder
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticShare
import io.github.mateuxlucax.caparao.data.local.BrewDatabase
import io.github.mateuxlucax.caparao.data.local.BrewEntity
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.BuiltInRecipes
import io.github.mateuxlucax.caparao.data.local.CoffeeBagRepository
import io.github.mateuxlucax.caparao.data.local.RecipeRepository
import io.github.mateuxlucax.caparao.data.local.SeedBuiltInRecipes
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewSample
import io.github.mateuxlucax.caparao.model.CoffeeBag
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.ScaleError
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.model.StepKind
import io.github.mateuxlucax.caparao.ui.widget.brewWidgetWeekWindow
import io.github.mateuxlucax.caparao.ui.widget.summarizeBrewWidgetWeek
import io.github.mateuxlucax.caparao.ui.recipes.RecipeEditorViewModel
import io.github.mateuxlucax.caparao.ui.coffees.CoffeeBagDetailViewModel
import io.github.mateuxlucax.caparao.ui.coffees.CoffeeBagsViewModel
import io.github.mateuxlucax.caparao.ui.recipes.RecipesViewModel
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.ui.App
import io.github.mateuxlucax.caparao.ui.AppActions
import io.github.mateuxlucax.caparao.ui.Tab
import io.github.mateuxlucax.caparao.brew.StepState
import io.github.mateuxlucax.caparao.brew.Pace
import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.ui.brew.BrewNumbers
import io.github.mateuxlucax.caparao.ui.brew.BrewStep
import io.github.mateuxlucax.caparao.ui.brew.BrewUiState
import io.github.mateuxlucax.caparao.ui.brew.BrewViewModel
import io.github.mateuxlucax.caparao.ui.brew.FlowChart
import io.github.mateuxlucax.caparao.ui.brew.FlowPoint
import io.github.mateuxlucax.caparao.ui.brew.GuidedStep
import io.github.mateuxlucax.caparao.ui.brews.BrewDetailViewModel
import io.github.mateuxlucax.caparao.ui.brews.BrewsViewModel
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.home.HomeViewModel
import io.github.mateuxlucax.caparao.ui.onboarding.OnboardingViewModel
import io.github.mateuxlucax.caparao.ui.settings.ScaleStatusViewModel
import io.github.mateuxlucax.caparao.ui.settings.ServerViewModel
import io.github.mateuxlucax.caparao.ui.settings.ExportViewModel
import io.github.mateuxlucax.caparao.ui.settings.DiagnosticsViewModel
import io.github.mateuxlucax.caparao.ui.settings.TargetRatioViewModel
import io.github.mateuxlucax.caparao.ui.theme.AppTheme
import io.github.mateuxlucax.caparao.ui.theme.BrandColor
import io.github.mateuxlucax.caparao.ui.components.ScaleReadout
import io.github.mateuxlucax.caparao.ui.components.readableOn
import io.github.mateuxlucax.caparao.ui.components.ringStroke
import io.github.mateuxlucax.caparao.ui.components.targetBand
import io.github.mateuxlucax.caparao.ui.components.TargetBand
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestName
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenWidgetTests(lang: String, theme: String, compactOnly: Boolean = false) : ScreenTestSupport(lang, theme, compactOnly) {
    @Test
    fun widget() {
        var latest by mutableStateOf<Brew?>(
            Brew("screen-test-widget", BrewMode.ESPRESSO, 0L, 28_000L, 18.0, 36.0, 4_000L, 1.3, 2.4, false),
        )
        // A neutral home screen behind the widget, so its rounded shape reads in both themes.
        val wallpaper = if (theme == "dark") Color(0xFF2B3038) else Color(0xFFDDE4ED)
        var rendered: View? = null
        compose.setContent {
            Box(Modifier.background(wallpaper)) {
                key(latest) {
                    AndroidView(
                        // The RemoteViews the provider builds, applied to a real view as on a home screen.
                        factory = { BrewWidgetProvider().views(context, 1, latest).apply(context, null).also { rendered = it } },
                        modifier = Modifier.size(180.dp, 176.dp).testTag("widget"),
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("widget").assertExists()

        // The saved state shows the last brew: title, method label, and the suggested action.
        val saved = rendered
        assertNotNull("the applied widget view was not created", saved)
        assertWidgetText(saved!!, R.id.widget_title, text(R.string.widget_last_brew_title))
        assertWidgetVisibility(saved, R.id.widget_last_brew, View.VISIBLE)
        assertWidgetVisibility(saved, R.id.widget_empty_content, View.GONE)
        assertWidgetText(saved, R.id.widget_method, text(R.string.mode_espresso))
        assertWidgetText(saved, R.id.widget_action_label, text(R.string.widget_brew_filter))

        latest = null
        compose.waitForIdle()
        compose.onNodeWithTag("widget").assertExists()

        // The empty state shows the empty title and the coffee action.
        val empty = rendered
        assertNotNull("the applied widget view was not created", empty)
        assertWidgetVisibility(empty!!, R.id.widget_title, View.GONE)
        assertWidgetVisibility(empty, R.id.widget_last_brew, View.GONE)
        assertWidgetVisibility(empty, R.id.widget_empty_content, View.VISIBLE)
        assertWidgetText(empty, R.id.widget_empty_title, text(R.string.widget_empty_title))
        assertWidgetText(empty, R.id.widget_action_label, text(R.string.widget_brew_coffee))
    }

    /** The expanded widget's estimates and neutral empty-week state, rendered at its 260×210 dp breakpoint. */
    @Test
    fun weeklyWidget() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 10, 18)
        val window = brewWidgetWeekWindow(today, zone)
        val days = window.days
        val atNoon = { date: LocalDate -> date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli() }
        fun savedBrew(
            id: String,
            dayIndex: Int,
            mode: BrewMode,
            dose: Double,
            yield: Double,
            interrupted: Boolean = false,
        ) = Brew(
            id = id,
            mode = mode,
            startedAt = atNoon(days[dayIndex]) - 28_000,
            durationMs = 28_000,
            doseGrams = dose,
            yieldGrams = yield,
            timeToFirstDropMs = if (mode == BrewMode.ESPRESSO) 4_000 else null,
            flowAvgGramsPerSecond = 1.3,
            flowPeakGramsPerSecond = 2.4,
            interrupted = interrupted,
        )

        val saved = listOf(
            savedBrew("filter-1", 1, BrewMode.FILTER, 15.0, 250.0),
            savedBrew("espresso-1", 2, BrewMode.ESPRESSO, 18.0, 36.0),
            savedBrew("espresso-2", 2, BrewMode.ESPRESSO, 18.0, 40.0),
            savedBrew("filter-2", 4, BrewMode.FILTER, 15.0, 250.0),
            savedBrew("filter-3", 4, BrewMode.FILTER, 15.0, 250.0),
            savedBrew("filter-4", 5, BrewMode.FILTER, 15.0, 250.0),
            savedBrew("filter-5", 5, BrewMode.FILTER, 15.0, 250.0),
            savedBrew("espresso-3", 5, BrewMode.ESPRESSO, 18.0, 36.0),
            savedBrew("interrupted-filter", 6, BrewMode.FILTER, 15.0, 100.0, interrupted = true),
        )
        var week by mutableStateOf(summarizeBrewWidgetWeek(saved, window, zone))
        val emptyWeek = summarizeBrewWidgetWeek(emptyList(), window, zone)
        val wallpaper = if (theme == "dark") Color(0xFF2B3038) else Color(0xFFDDE4ED)
        val widthDp = context.resources.getDimension(R.dimen.widget_weekly_min_width) / context.resources.displayMetrics.density
        val heightDp = context.resources.getDimension(R.dimen.widget_weekly_min_height) / context.resources.displayMetrics.density
        var rendered: View? = null
        compose.setContent {
            Box(Modifier.background(wallpaper)) {
                key(week) {
                    AndroidView(
                        // The RemoteViews the provider builds, applied to a real view as on a home screen.
                        factory = { BrewWidgetProvider().weeklyViews(context, 1, BrewMode.FILTER, week).apply(context, null).also { rendered = it } },
                        modifier = Modifier.size(widthDp.dp, heightDp.dp).testTag("weekly-widget"),
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("weekly-widget").assertExists()

        // The rendered week: title, brew count, and the estimate values as shown (not just the format strings).
        val savedView = rendered
        assertNotNull("the applied widget view was not created", savedView)
        assertWidgetText(savedView!!, R.id.widget_weekly_title, text(R.string.widget_weekly_title))
        assertWidgetVisibility(savedView, R.id.widget_weekly_content, View.VISIBLE)
        assertWidgetVisibility(savedView, R.id.widget_weekly_empty, View.GONE)
        assertWidgetText(
            savedView,
            R.id.widget_weekly_brews_value,
            io.github.mateuxlucax.caparao.ui.widget.BrewWidgetFormat.count(context, week.brewCount),
        )
        assertWidgetText(
            savedView,
            R.id.widget_weekly_beverage_value,
            io.github.mateuxlucax.caparao.ui.widget.BrewWidgetFormat.beverageVolume(context, week.estimatedBeverageMl),
        )
        assertWidgetText(
            savedView,
            R.id.widget_weekly_dose_value,
            io.github.mateuxlucax.caparao.ui.widget.BrewWidgetFormat.dose(context, week.doseGrams),
        )
        assertWidgetText(
            savedView,
            R.id.widget_weekly_caffeine_value,
            io.github.mateuxlucax.caparao.ui.widget.BrewWidgetFormat.caffeine(context, week.estimatedCaffeineMg),
        )
        // The rendered estimates: beverage and caffeine carry ≈, dose is recorded and has none.
        val beverage = widgetText(savedView, R.id.widget_weekly_beverage_value)
        val caffeine = widgetText(savedView, R.id.widget_weekly_caffeine_value)
        val dose = widgetText(savedView, R.id.widget_weekly_dose_value)
        assertTrue(beverage.startsWith("≈ "))
        assertTrue(caffeine.startsWith("≈ "))
        assertFalse(dose.startsWith("≈"))

        week = emptyWeek
        compose.waitForIdle()
        compose.onNodeWithTag("weekly-widget").assertExists()

        // The rendered empty week: the neutral message, no stats row.
        val emptyView = rendered
        assertNotNull("the applied widget view was not created", emptyView)
        assertWidgetText(emptyView!!, R.id.widget_weekly_empty, text(R.string.widget_weekly_empty))
        assertWidgetVisibility(emptyView, R.id.widget_weekly_empty, View.VISIBLE)
        assertWidgetVisibility(emptyView, R.id.widget_weekly_content, View.GONE)
    }

    /** The Coffees tab, with an open bag and a finished one (#110). */
}

@Config(qualifiers = "en-w411dp-h891dp-notnight-xxhdpi")
class WidgetEnLightTest : ScreenWidgetTests("en", "light")

@Config(qualifiers = "en-w411dp-h891dp-night-xxhdpi")
class WidgetEnDarkTest : ScreenWidgetTests("en", "dark")

@Config(qualifiers = "pt-rBR-w411dp-h891dp-notnight-xxhdpi")
class WidgetPtBrLightTest : ScreenWidgetTests("pt-BR", "light")

@Config(qualifiers = "pt-rBR-w411dp-h891dp-night-xxhdpi")
class WidgetPtBrDarkTest : ScreenWidgetTests("pt-BR", "dark")

@Config(qualifiers = "en-w320dp-h640dp-notnight-xxhdpi")
class WidgetSmallEnLightTest : ScreenWidgetTests("en", "light", compactOnly = true)

@Config(qualifiers = "en-w320dp-h640dp-night-xxhdpi")
class WidgetSmallEnDarkTest : ScreenWidgetTests("en", "dark", compactOnly = true)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-notnight-xxhdpi")
class WidgetSmallPtBrLightTest : ScreenWidgetTests("pt-BR", "light", compactOnly = true)

@Config(qualifiers = "pt-rBR-w320dp-h640dp-night-xxhdpi")
class WidgetSmallPtBrDarkTest : ScreenWidgetTests("pt-BR", "dark", compactOnly = true)
