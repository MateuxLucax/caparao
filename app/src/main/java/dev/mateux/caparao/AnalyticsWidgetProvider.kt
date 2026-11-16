package dev.mateux.caparao

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.DashPathEffect
import android.graphics.RectF
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import dev.mateux.caparao.data.local.SettingsStore
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.ui.MainActivity
import dev.mateux.caparao.ui.analytics.AnalyticsViewModel
import dev.mateux.caparao.ui.analytics.AnalyticsState
import dev.mateux.caparao.ui.components.LauncherShortcut
import dev.mateux.caparao.ui.widget.BrewWidgetRefresh
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/** Home-screen summary using the same weekly and caffeine calculations as the Analytics tab. */
class AnalyticsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) = refresh(context, manager, appWidgetIds)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) =
        refresh(context, manager, intArrayOf(appWidgetId))

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            refresh(context, manager, manager.getAppWidgetIds(ComponentName(context, AnalyticsWidgetProvider::class.java)))
        }
    }

    private fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        val finished = AtomicBoolean(false)
        val finish = { if (finished.compareAndSet(false, true)) pending.finish() }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val watchdog = launch { delay(BrewWidgetRefresh.TIMEOUT_MS + 250); finish() }
            try {
                withTimeoutOrNull(BrewWidgetRefresh.TIMEOUT_MS) {
                    val container = (context.applicationContext as CaparaoApplication).container
                    val brews = container.brews.brews.first()
                    val bags = container.coffeeBags.bags.first()
                    val state = AnalyticsViewModel.summarize(brews, bags, bedtimeMinutes = container.settings.bedtimeMinutes)
                    ids.forEach { id ->
                        val options = manager.getAppWidgetOptions(id)
                        val expanded = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) >= 240
                        manager.updateAppWidget(id, views(context, id, brews, state, expanded))
                    }
                } ?: Log.w(TAG, "Analytics widget refresh timed out")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "Analytics widget refresh failed", failure)
            } finally {
                watchdog.cancel()
                finish()
            }
        }
    }

    internal fun views(context: Context, id: Int, brews: List<Brew>, state: AnalyticsState, expanded: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_analytics)
        val empty = brews.isEmpty()
        views.setViewVisibility(R.id.analytics_widget_empty, if (empty) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.analytics_widget_content, if (empty) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.analytics_widget_curve, if (!empty && expanded) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.analytics_widget_caffeine, context.getString(R.string.analytics_widget_now, state.caffeine.nowMg))
        views.setTextViewText(R.id.analytics_widget_bedtime, context.getString(R.string.analytics_widget_bedtime, state.caffeine.bedtimeMg))
        views.setTextViewText(
            R.id.analytics_widget_week,
            context.getString(R.string.analytics_widget_week, state.thisWeek.grams.toInt(), state.thisWeek.brews, state.lastWeek.grams.toInt(), state.lastWeek.brews),
        )
        views.setImageViewBitmap(R.id.analytics_widget_curve, curveBitmap(context, state, 520, 132))
        val intent = Intent(context, MainActivity::class.java)
            .setAction(LauncherShortcut.ANALYTICS.action)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        views.setOnClickPendingIntent(R.id.analytics_widget_root, PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ))
        return views
    }

    private fun curveBitmap(context: Context, state: AnalyticsState, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val color = context.getColor(R.color.widget_icon_foreground)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = 48; style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 4f; style = Paint.Style.STROKE }
        val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = 2f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(8f, 7f), 0f)
        }
        val bounds = RectF(4f, 8f, width - 4f, height - 8f)
        val points = state.caffeine.points
        val maxHour = (points.lastOrNull()?.hour ?: 23.0).coerceAtLeast(23.0)
        val maxMg = (points.maxOfOrNull { it.mg } ?: 0.0).coerceAtLeast(100.0)
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = bounds.left + ((point.hour - 6.0) / (maxHour - 6.0)).coerceIn(0.0, 1.0).toFloat() * bounds.width()
            val y = bounds.bottom - (point.mg / maxMg).toFloat() * bounds.height()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (points.isNotEmpty()) {
            val area = Path(path).apply { lineTo(bounds.right, bounds.bottom); lineTo(bounds.left, bounds.bottom); close() }
            canvas.drawPath(area, fill)
            val referenceY = bounds.bottom - (50.0 / maxMg).toFloat() * bounds.height()
            canvas.drawLine(bounds.left, referenceY, bounds.right, referenceY, guide)
            canvas.drawLine(bounds.right, bounds.top, bounds.right, bounds.bottom, guide)
            state.caffeine.brewHours.forEach { hour ->
                val x = bounds.left + ((hour - 6.0) / (maxHour - 6.0)).coerceIn(0.0, 1.0).toFloat() * bounds.width()
                canvas.drawLine(x, bounds.bottom - 9f, x, bounds.bottom, stroke)
            }
            canvas.drawPath(path, stroke)
        }
        return bitmap
    }

    companion object {
        private const val TAG = "AnalyticsWidget"
        private const val ACTION_REFRESH = "dev.mateux.caparao.widget.ANALYTICS_REFRESH"

        fun requestRefresh(context: Context) {
            val component = ComponentName(context, AnalyticsWidgetProvider::class.java)
            if (AppWidgetManager.getInstance(context).getAppWidgetIds(component).isNotEmpty()) {
                context.sendBroadcast(Intent(context, AnalyticsWidgetProvider::class.java).setAction(ACTION_REFRESH))
            }
        }
    }
}
