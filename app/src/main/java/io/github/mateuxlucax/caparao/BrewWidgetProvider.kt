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
import io.github.mateuxlucax.caparao.ui.widget.BrewWidgetRefresh
import io.github.mateuxlucax.caparao.ui.widget.actionIconForWidget
import io.github.mateuxlucax.caparao.ui.widget.shortcutForWidget
import io.github.mateuxlucax.caparao.ui.widget.suggestedModeAfter
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
                BrewWidgetRefresh.run(
                    read = { (context.applicationContext as CaparaoApplication).container.brews.latest() },
                    publish = { latest ->
                        appWidgetIds.forEach { id -> manager.updateAppWidget(id, views(context, id, latest)) }
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

    private fun actionLabel(mode: BrewMode?): Int = when {
        mode == null -> R.string.widget_brew_coffee
        mode == BrewMode.FILTER -> R.string.widget_brew_filter
        else -> R.string.widget_brew_espresso
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
