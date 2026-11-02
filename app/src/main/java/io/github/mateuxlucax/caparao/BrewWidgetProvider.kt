package io.github.mateuxlucax.caparao

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.ui.components.label
import io.github.mateuxlucax.caparao.ui.widget.BrewAge
import io.github.mateuxlucax.caparao.ui.widget.brewAge
import io.github.mateuxlucax.caparao.ui.widget.shortcutForWidget
import io.github.mateuxlucax.caparao.ui.widget.suggestedModeAfter
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Home-screen view of the latest saved brew, refreshed after writes and by the system periodically. */
class BrewWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context, manager, appWidgetIds)
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
                val latest = (context.applicationContext as CaparaoApplication).container.brews.latest()
                val now = System.currentTimeMillis()
                appWidgetIds.forEach { id -> manager.updateAppWidget(id, views(context, id, latest, now)) }
            } catch (failure: Exception) {
                Log.w(TAG, "Brew widget refresh failed", failure)
            } finally {
                pending.finish()
            }
        }
    }

    private fun views(context: Context, appWidgetId: Int, latest: Brew?, now: Long): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_brew)
        val mode = latest?.mode
        val suggestedMode = mode?.let(::suggestedModeAfter)
        val shortcut = shortcutForWidget(mode)
        if (latest == null) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_empty_title))
            views.setViewVisibility(R.id.widget_last_brew, View.GONE)
            views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
            views.setViewVisibility(R.id.widget_next, View.GONE)
            views.setTextViewText(R.id.widget_action, context.getString(R.string.widget_choose_method))
        } else {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_last_brew_title))
            views.setViewVisibility(R.id.widget_last_brew, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty_text, View.GONE)
            views.setViewVisibility(R.id.widget_next, View.VISIBLE)
            views.setTextViewText(R.id.widget_method, context.getString(latest.mode.label))
            views.setTextViewText(R.id.widget_age, savedAge(context, latest.startedAt + latest.durationMs, now))
            views.setTextViewText(
                R.id.widget_next,
                context.getString(R.string.widget_next_suggestion, context.getString(requireNotNull(suggestedMode).labelForWidgetAction())),
            )
            views.setTextViewText(R.id.widget_action, context.getString(actionLabel(suggestedMode)))
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

    private fun BrewMode.labelForWidgetAction(): Int = if (this == BrewMode.FILTER) R.string.mode_filter else R.string.mode_espresso

    private fun actionLabel(mode: BrewMode?): Int = when {
        mode == null -> R.string.widget_choose_method
        mode == BrewMode.FILTER -> R.string.widget_brew_filter
        else -> R.string.widget_brew_espresso
    }

    private fun savedAge(context: Context, endAtMs: Long, nowMs: Long): String = when (val age = brewAge(endAtMs, nowMs, ZoneId.systemDefault())) {
        BrewAge.Today -> context.getString(R.string.widget_saved_today)
        BrewAge.Yesterday -> context.getString(R.string.widget_saved_yesterday)
        is BrewAge.DaysAgo -> context.resources.getQuantityString(R.plurals.widget_saved_days, age.days, age.days)
    }

    companion object {
        private const val TAG = "BrewWidget"
        private const val ACTION_REFRESH = "io.github.mateuxlucax.caparao.widget.REFRESH"

        /** Request a best-effort refresh after a committed save or delete. */
        fun requestRefresh(context: Context) {
            val component = ComponentName(context, BrewWidgetProvider::class.java)
            if (AppWidgetManager.getInstance(context).getAppWidgetIds(component).isEmpty()) return
            context.sendBroadcast(Intent(context, BrewWidgetProvider::class.java).setAction(ACTION_REFRESH))
        }
    }
}
