package io.github.mateuxlucax.caparao

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.ui.components.label
import io.github.mateuxlucax.caparao.ui.launcher.LauncherShortcut
import io.github.mateuxlucax.caparao.ui.widget.BrewWidgetChart
import io.github.mateuxlucax.caparao.ui.widget.BrewWidgetFormat
import io.github.mateuxlucax.caparao.ui.widget.BrewWidgetRefresh
import io.github.mateuxlucax.caparao.ui.widget.BrewWidgetWeek
import io.github.mateuxlucax.caparao.ui.widget.actionIconForWidget
import io.github.mateuxlucax.caparao.ui.widget.brewWidgetWeekWindow
import io.github.mateuxlucax.caparao.ui.widget.shortcutForWidget
import io.github.mateuxlucax.caparao.ui.widget.suggestedModeAfter
import io.github.mateuxlucax.caparao.ui.widget.summarizeBrewWidgetWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Home-screen view of the latest saved brew, refreshed after writes and by the system periodically. */
class BrewWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context, manager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        refresh(context, manager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BrewWidgetProvider::class.java))
            refresh(context, manager, ids)
        }
    }

    private fun refresh(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                BrewWidgetRefresh.run(
                    read = {
                        val repository = (context.applicationContext as CaparaoApplication).container.brews
                        val zone = ZoneId.systemDefault()
                        val window = brewWidgetWeekWindow(LocalDate.now(zone), zone)
                        val weekBrews = repository.endedBetween(window.startInclusiveMs, window.endExclusiveMs)
                        BrewWidgetContent(
                            latest = repository.latest(),
                            week = summarizeBrewWidgetWeek(weekBrews, window, zone),
                        )
                    },
                    publish = { content ->
                        appWidgetIds.forEach { id ->
                            manager.updateAppWidget(id, responsiveViews(context, id, content.latest, content.week))
                        }
                    },
                )
            } catch (failure: Exception) {
                Log.w(TAG, "Brew widget refresh failed", failure)
            } finally {
                pending.finish()
            }
        }
    }

    internal fun views(context: Context, appWidgetId: Int, latest: Brew?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_brew)
        val mode = latest?.mode
        val suggestedMode = mode?.let(::suggestedModeAfter)
        val icon = when (mode) {
            BrewMode.FILTER -> R.drawable.ic_coffee_maker
            BrewMode.ESPRESSO, null -> R.drawable.ic_coffee
        }
        views.setImageViewResource(R.id.widget_method_icon, icon)
        val shortcut = shortcutForWidget(mode)
        if (latest == null) {
            views.setViewVisibility(R.id.widget_title, View.GONE)
            views.setViewVisibility(R.id.widget_last_brew, View.GONE)
            views.setViewVisibility(R.id.widget_empty_content, View.VISIBLE)
            views.setTextViewText(R.id.widget_empty_title, context.getString(R.string.widget_empty_title))
        } else {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_last_brew_title))
            views.setViewVisibility(R.id.widget_title, View.VISIBLE)
            views.setViewVisibility(R.id.widget_last_brew, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty_content, View.GONE)
            views.setTextViewText(R.id.widget_method, context.getString(latest.mode.label))
        }
        val actionLabel = context.getString(actionLabel(suggestedMode))
        views.setTextViewText(R.id.widget_action_label, actionLabel)
        views.setContentDescription(R.id.widget_action, actionLabel)
        val actionIcon = actionIconForWidget(mode)
        if (actionIcon == null) {
            views.setViewVisibility(R.id.widget_action_icon, View.GONE)
        } else {
            views.setImageViewResource(R.id.widget_action_icon, actionIcon)
            views.setViewVisibility(R.id.widget_action_icon, View.VISIBLE)
        }
        val intent = Intent(context, MainActivity::class.java)
            .setAction(shortcut.action)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_action, pendingIntent)
        return views
    }

    /** Android 12+ chooses the largest responsive layout that fits the widget's current size. */
    internal fun responsiveViews(context: Context, appWidgetId: Int, latest: Brew?, week: BrewWidgetWeek): RemoteViews {
        val density = context.resources.displayMetrics.density
        val compactSize = SizeF(
            context.resources.getDimension(R.dimen.widget_min_width) / density,
            context.resources.getDimension(R.dimen.widget_min_height) / density,
        )
        val weeklySize = SizeF(
            context.resources.getDimension(R.dimen.widget_weekly_min_width) / density,
            context.resources.getDimension(R.dimen.widget_weekly_min_height) / density,
        )
        return RemoteViews(
            mapOf(
                compactSize to views(context, appWidgetId, latest),
                weeklySize to weeklyViews(context, appWidgetId, latest?.mode, week),
            ),
        )
    }

    /** The expanded widget shows estimates for the last seven local dates; the compact layout remains unchanged. */
    internal fun weeklyViews(context: Context, appWidgetId: Int, latestMode: BrewMode?, week: BrewWidgetWeek): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_brew_weekly)
        views.setTextViewText(R.id.widget_weekly_title, context.getString(R.string.widget_weekly_title))
        views.setOnClickPendingIntent(R.id.widget_weekly_root, pendingIntent(context, appWidgetId, shortcutForWidget(latestMode)))

        if (week.isEmpty) {
            views.setTextViewText(R.id.widget_weekly_empty, context.getString(R.string.widget_weekly_empty))
            views.setViewVisibility(R.id.widget_weekly_estimates, View.GONE)
            views.setViewVisibility(R.id.widget_weekly_empty, View.VISIBLE)
            views.setViewVisibility(R.id.widget_weekly_content, View.GONE)
            return views
        }

        views.setTextViewText(R.id.widget_weekly_estimates, context.getString(R.string.widget_weekly_estimates))
        views.setViewVisibility(R.id.widget_weekly_estimates, View.VISIBLE)
        views.setViewVisibility(R.id.widget_weekly_empty, View.GONE)
        views.setViewVisibility(R.id.widget_weekly_content, View.VISIBLE)
        views.setTextViewText(R.id.widget_weekly_brews_value, BrewWidgetFormat.count(context, week.brewCount))
        views.setTextViewText(R.id.widget_weekly_beverage_value, BrewWidgetFormat.beverageVolume(context, week.estimatedBeverageMl))
        views.setTextViewText(R.id.widget_weekly_dose_value, BrewWidgetFormat.dose(context, week.doseGrams))
        views.setTextViewText(R.id.widget_weekly_caffeine_value, BrewWidgetFormat.caffeine(context, week.estimatedCaffeineMg))

        val locale = context.resources.configuration.locales[0]
        val weekdayFormat = DateTimeFormatter.ofPattern("EEE", locale)
        week.buckets.forEachIndexed { index, bucket ->
            views.setTextViewText(WEEKDAY_IDS[index], bucket.date.format(weekdayFormat))
        }
        val countLabels = week.buckets.map { BrewWidgetFormat.count(context, it.brewCount) }
        val chartDescription = week.buckets.mapIndexed { index, bucket ->
            "${bucket.date.format(weekdayFormat)} ${countLabels[index]}"
        }.joinToString(", ")
        views.setContentDescription(
            R.id.widget_weekly_chart,
            context.getString(R.string.widget_weekly_chart_description, chartDescription),
        )
        val chartWidthPx = context.resources.getDimensionPixelSize(R.dimen.widget_weekly_min_width) -
            2 * context.resources.getDimensionPixelSize(R.dimen.widget_weekly_padding)
        val chartHeightPx = context.resources.getDimensionPixelSize(R.dimen.widget_weekly_chart_height)
        val bitmap = BrewWidgetChart.render(
            counts = week.buckets.map { it.brewCount },
            countLabels = countLabels,
            widthPx = chartWidthPx,
            heightPx = chartHeightPx,
            density = context.resources.displayMetrics.density,
        )
        views.setImageViewBitmap(R.id.widget_weekly_chart, bitmap)
        return views
    }

    private fun pendingIntent(context: Context, appWidgetId: Int, shortcut: LauncherShortcut): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(shortcut.action)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionLabel(mode: BrewMode?): Int = when {
        mode == null -> R.string.widget_brew_coffee
        mode == BrewMode.FILTER -> R.string.widget_brew_filter
        else -> R.string.widget_brew_espresso
    }

    companion object {
        private const val TAG = "BrewWidget"
        private const val ACTION_REFRESH = "io.github.mateuxlucax.caparao.widget.REFRESH"
        private val WEEKDAY_IDS = intArrayOf(
            R.id.widget_weekly_day_0,
            R.id.widget_weekly_day_1,
            R.id.widget_weekly_day_2,
            R.id.widget_weekly_day_3,
            R.id.widget_weekly_day_4,
            R.id.widget_weekly_day_5,
            R.id.widget_weekly_day_6,
        )

        /** Request a best-effort refresh after a committed save or delete. */
        fun requestRefresh(context: Context) {
            val component = ComponentName(context, BrewWidgetProvider::class.java)
            if (AppWidgetManager.getInstance(context).getAppWidgetIds(component).isEmpty()) return
            context.sendBroadcast(Intent(context, BrewWidgetProvider::class.java).setAction(ACTION_REFRESH))
        }
    }

    private data class BrewWidgetContent(val latest: Brew?, val week: BrewWidgetWeek)
}
