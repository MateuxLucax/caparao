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
import io.github.mateuxlucax.caparao.ui.components.icon
import io.github.mateuxlucax.caparao.ui.components.label
import io.github.mateuxlucax.caparao.ui.components.LauncherShortcut
import io.github.mateuxlucax.caparao.ui.widget.BrewWidgetRefresh
import io.github.mateuxlucax.caparao.ui.widget.actionIconForWidget
import io.github.mateuxlucax.caparao.ui.widget.shortcutForWidget
import io.github.mateuxlucax.caparao.ui.widget.suggestedModeAfter
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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

    private fun refresh(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (appWidgetIds.isEmpty()) return
        val pending = goAsync()
        // Both the normal path and the watchdog below may reach the deadline, so finish the broadcast once.
        val finished = AtomicBoolean(false)
        val finish = { if (finished.compareAndSet(false, true)) pending.finish() }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            refreshBounded(
                read = { (context.applicationContext as CaparaoApplication).container.brews.latest() },
                publish = { latest ->
                    appWidgetIds.forEach { id -> manager.updateAppWidget(id, views(context, id, latest)) }
                },
                finish = finish,
            )
        }
    }

    /**
     * Finishes the broadcast even when the read hangs or throws: Android reports a `goAsync()` window longer than about
     * 10 s as a background ANR (#326). A timeout keeps the previous widget content and logs no personal data.
     *
     * [BrewWidgetRefresh.run] bounds cancellable work, but cancellation is cooperative: the read starts by touching
     * `AppContainer`, a synchronous lazy init, and a blocking read cannot be interrupted. The watchdog therefore calls
     * [finish] at the deadline on its own, so the broadcast is released even when `run` is still waiting for the read.
     */
    internal suspend fun <T> refreshBounded(read: suspend () -> T, publish: (T) -> Unit, finish: () -> Unit): Unit = coroutineScope {
        val watchdog = launch {
            delay(BrewWidgetRefresh.TIMEOUT_MS + FINISH_MARGIN_MS)
            finish()
        }
        try {
            if (BrewWidgetRefresh.run(read, publish) == BrewWidgetRefresh.Outcome.TIMED_OUT) {
                Log.w(TAG, "Brew widget refresh timed out")
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            Log.w(TAG, "Brew widget refresh failed", failure)
        } finally {
            watchdog.cancel()
            finish()
        }
    }

    internal fun views(context: Context, appWidgetId: Int, latest: Brew?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_brew)
        val mode = latest?.mode
        val suggestedMode = mode?.let(::suggestedModeAfter)
        val icon = mode?.icon ?: R.drawable.ic_coffee
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

    private fun actionLabel(mode: BrewMode?): Int = when (mode) {
        null -> R.string.widget_brew_coffee
        BrewMode.ESPRESSO -> R.string.widget_brew_espresso
        BrewMode.FILTER -> R.string.widget_brew_filter
        BrewMode.FRENCH_PRESS -> R.string.widget_brew_french_press
    }

    companion object {
        private const val TAG = "BrewWidget"

        /** The watchdog fires just after the cooperative timeout, still well inside the ~10 s broadcast window. */
        private const val FINISH_MARGIN_MS = 250L
        private const val ACTION_REFRESH = "io.github.mateuxlucax.caparao.widget.REFRESH"
        /** Request a best-effort refresh after a committed save or delete. */
        fun requestRefresh(context: Context) {
            val component = ComponentName(context, BrewWidgetProvider::class.java)
            if (AppWidgetManager.getInstance(context).getAppWidgetIds(component).isEmpty()) return
            context.sendBroadcast(Intent(context, BrewWidgetProvider::class.java).setAction(ACTION_REFRESH))
        }
    }

}
