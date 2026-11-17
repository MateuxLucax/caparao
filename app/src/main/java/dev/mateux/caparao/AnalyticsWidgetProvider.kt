package dev.mateux.caparao

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.SizeF
import android.util.TypedValue
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.ui.MainActivity
import dev.mateux.caparao.ui.analytics.AnalyticsViewModel
import dev.mateux.caparao.ui.analytics.AnalyticsState
import dev.mateux.caparao.ui.components.LauncherShortcut
import dev.mateux.caparao.data.local.SettingsStore
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
        if (intent.action == ACTION_REFRESH || intent.action in CONFIGURATION_ACTIONS) {
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
                        val density = context.resources.displayMetrics.density
                        val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty().ifEmpty {
                            listOf(
                                SizeF(
                                    context.resources.getDimension(R.dimen.analytics_widget_min_width) / density,
                                    context.resources.getDimension(R.dimen.analytics_widget_min_height) / density,
                                ),
                            )
                        }
                        manager.updateAppWidget(id, views(context, id, brews, state, sizes, container.settings.bedtimeMinutes))
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

    internal fun views(context: Context, id: Int, brews: List<Brew>, state: AnalyticsState, sizes: List<SizeF>, bedtimeMinutes: Int): RemoteViews =
        RemoteViews(sizes.associateWith { size -> viewsForSize(context, id, brews, state, size, bedtimeMinutes) })

    internal fun layoutForSize(size: SizeF): Int {
        return when {
            size.width >= WIDE_MIN_WIDTH_DP && size.height >= EXPANDED_MIN_HEIGHT_DP -> R.layout.widget_analytics_expanded
            size.width >= WIDE_MIN_WIDTH_DP -> R.layout.widget_analytics_wide
            else -> R.layout.widget_analytics_square
        }
    }

    internal fun viewsForSize(context: Context, id: Int, brews: List<Brew>, state: AnalyticsState, size: SizeF, bedtimeMinutes: Int = SettingsStore.DEFAULT_BEDTIME_MINUTES): RemoteViews {
        val layout = layoutForSize(size)
        val views = RemoteViews(context.packageName, layout)
        val empty = brews.isEmpty()
        val hasCurve = state.caffeine.todayBrews > 0 && state.caffeine.points.isNotEmpty()
        views.setViewVisibility(R.id.analytics_widget_empty, if (empty) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.analytics_widget_content, if (empty) View.GONE else View.VISIBLE)
        if (layout == R.layout.widget_analytics_expanded) {
            views.setViewVisibility(R.id.analytics_widget_curve, if (hasCurve) View.VISIBLE else View.GONE)
        } else {
            views.setViewVisibility(R.id.analytics_widget_sparkline, if (hasCurve) View.VISIBLE else View.GONE)
        }
        views.setTextViewText(R.id.analytics_widget_caffeine, context.getString(R.string.analytics_widget_value_mg, state.caffeine.nowMg))
        val aboveBedtimeTarget = state.caffeine.bedtimeMg > 50
        views.setTextViewText(R.id.analytics_widget_week, context.getString(R.string.analytics_widget_value_grams, state.thisWeek.grams.toInt()))
        val delta = state.thisWeek.grams.toInt() - state.lastWeek.grams.toInt()
        views.setTextViewText(R.id.analytics_widget_delta, context.getString(R.string.analytics_widget_delta_grams, if (delta < 0) "↓" else if (delta > 0) "↑" else "·", kotlin.math.abs(delta)))
        val bedtime = bedtimeMinutes.coerceIn(0, 1439)
        if (layout == R.layout.widget_analytics_expanded) {
            views.setTextViewText(
                R.id.analytics_widget_bedtime_chip,
                context.getString(
                    R.string.analytics_widget_bedtime_chip,
                    state.caffeine.bedtimeMg,
                    context.getString(R.string.analytics_widget_clock, bedtime / 60, bedtime % 60),
                ),
            )
            views.setTextColor(R.id.analytics_widget_bedtime_chip, if (aboveBedtimeTarget) errorColor(context) else context.getColor(R.color.widget_primary_text))
            views.setContentDescription(
                R.id.analytics_widget_bedtime_chip,
                if (aboveBedtimeTarget) context.getString(R.string.analytics_widget_bedtime_high_description, state.caffeine.bedtimeMg) else null,
            )
            views.setImageViewBitmap(R.id.analytics_widget_curve, curveBitmap(context, state, size, bedtime, detailed = true))
        } else {
            views.setTextViewText(R.id.analytics_widget_bedtime, context.getString(R.string.analytics_widget_value_mg, state.caffeine.bedtimeMg))
            views.setTextColor(R.id.analytics_widget_bedtime, if (aboveBedtimeTarget) errorColor(context) else context.getColor(R.color.widget_primary_text))
            views.setContentDescription(
                R.id.analytics_widget_bedtime,
                if (aboveBedtimeTarget) context.getString(R.string.analytics_widget_bedtime_high_description, state.caffeine.bedtimeMg) else null,
            )
            if (layout == R.layout.widget_analytics_square) views.setViewVisibility(R.id.analytics_widget_delta, View.GONE)
            if (hasCurve) views.setImageViewBitmap(R.id.analytics_widget_sparkline, curveBitmap(context, state, size, bedtime, detailed = false))
        }
        val intent = Intent(context, MainActivity::class.java)
            .setAction(LauncherShortcut.ANALYTICS.action)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        views.setOnClickPendingIntent(android.R.id.background, activityIntent(context, id, intent))
        views.setOnClickPendingIntent(
            R.id.analytics_widget_start_brew,
            activityIntent(context, id, Intent(context, MainActivity::class.java)
                .setAction(LauncherShortcut.WIDGET_HOME.action)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)),
        )
        return views
    }

    private fun activityIntent(context: Context, id: Int, intent: Intent) = PendingIntent.getActivity(
        context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun errorColor(context: Context): Int {
        val value = TypedValue()
        context.theme.resolveAttribute(android.R.attr.colorError, value, true)
        return if (value.resourceId != 0) context.getColor(value.resourceId) else value.data
    }

    internal fun curveBitmap(context: Context, state: AnalyticsState, size: SizeF, bedtimeMinutes: Int, detailed: Boolean): Bitmap {
        val density = context.resources.displayMetrics.density
        val layout = layoutForSize(size)
        val widthDp = when {
            detailed -> size.width - 32f
            layout == R.layout.widget_analytics_wide -> (size.width - 48f) * 1.2f / 2.2f
            else -> size.width - 32f
        }
        val heightDp = when {
            detailed -> size.height - EXPANDED_CHART_FIXED_HEIGHT_DP
            layout == R.layout.widget_analytics_wide -> SPARKLINE_HEIGHT_DP
            else -> (size.height - SQUARE_CHART_FIXED_HEIGHT_DP).coerceAtLeast(SPARKLINE_MIN_HEIGHT_DP)
        }
        val width = (widthDp * density).toInt().coerceAtLeast(1)
        val height = (heightDp * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.density = Bitmap.DENSITY_NONE
        val canvas = Canvas(bitmap)
        val primary = context.getColor(R.color.widget_icon_foreground)
        val variant = context.getColor(R.color.widget_secondary_text)
        val fontSize = 12f * density
        val labels = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = variant; textSize = fontSize; typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL); textAlign = Paint.Align.RIGHT }
        val plotBottom = if (detailed) height - CHART_LABEL_ROW_HEIGHT_DP * density else height - 2f * density
        val bounds = RectF(3f * density, 3f * density, width - if (detailed) 30f * density else 3f * density, plotBottom)
        val firstBrew = state.caffeine.brewHours.minOrNull() ?: 6.0
        val startHour = maxOf(6.0, firstBrew - 1.0)
        val bedtimeHour = bedtimeMinutes / 60.0 + bedtimeMinutes % 60 / 60.0
        val endHour = maxOf(startHour + 1.0, bedtimeHour + 1.0)
        val now = java.time.LocalTime.now().let { it.hour + it.minute / 60.0 }
        val points = state.caffeine.points.filter { it.hour >= firstBrew }
        val maxMg = maxOf(100.0, points.maxOfOrNull { it.mg } ?: 0.0)
        fun x(hour: Double) = bounds.left + ((hour - startHour) / (endHour - startHour)).coerceIn(0.0, 1.0).toFloat() * bounds.width()
        fun y(mg: Double) = bounds.bottom - (mg / maxMg).toFloat() * bounds.height()
        val path = Path()
        points.forEachIndexed { index, point ->
            if (index == 0) path.moveTo(x(point.hour), y(point.mg)) else path.lineTo(x(point.hour), y(point.mg))
        }
        if (points.isNotEmpty()) {
            val area = Path(path).apply { lineTo(x(points.last().hour), bounds.bottom); lineTo(x(points.first().hour), bounds.bottom); close() }
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, bounds.top, 0f, bounds.bottom, (primary and 0x00ffffff) or 0x33000000, (primary and 0x00ffffff), Shader.TileMode.CLAMP)
            }
            canvas.drawPath(area, fill)
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = primary; strokeWidth = 2f * density; style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(path, stroke)
            if (detailed) {
                val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = variant; strokeWidth = density; style = Paint.Style.STROKE; pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f * density, 3f * density), 0f) }
                val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = variant; strokeWidth = density }
                val bedtimeX = x(bedtimeHour)
                val nowX = x(now)
                val referenceY = y(50.0)
                canvas.drawLine(bounds.left, referenceY, bounds.right, referenceY, guide)
                canvas.drawLine(bedtimeX, bounds.top, bedtimeX, bounds.bottom, guide)
                canvas.drawLine(nowX, bounds.top, nowX, bounds.bottom, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = variant; strokeWidth = density })
                canvas.drawCircle(nowX, y(points.minByOrNull { kotlin.math.abs(it.hour - now) }?.mg ?: 0.0), 2.5f * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primary })
                canvas.drawText("50 mg", width - 2f * density, referenceY - 2f * density, labels)
                canvas.drawLine(bounds.left, bounds.bottom, bounds.right, bounds.bottom, axis)
                val baseline = height - CHART_LABEL_BOTTOM_PADDING_DP * density
                labels.textAlign = Paint.Align.LEFT
                canvas.drawText(context.getString(R.string.analytics_widget_clock, (startHour * 60).toInt() / 60, (startHour * 60).toInt() % 60), bounds.left, baseline, labels)
                val nowLabel = context.getString(R.string.analytics_widget_now_marker)
                canvas.drawText(nowLabel, (nowX - labels.measureText(nowLabel) / 2f).coerceIn(bounds.left, bounds.right - labels.measureText(nowLabel)), baseline, labels)
                val bedtimeLabel = context.getString(R.string.analytics_widget_clock, bedtimeMinutes / 60, bedtimeMinutes % 60)
                labels.textAlign = Paint.Align.RIGHT
                canvas.drawText(bedtimeLabel, width - 2f * density, baseline, labels)
            } else {
                val nowX = x(now)
                val point = points.minByOrNull { kotlin.math.abs(it.hour - now) }
                if (point != null) canvas.drawCircle(nowX, y(point.mg), 2.5f * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primary })
            }
        }
        return bitmap
    }

    companion object {
        private const val TAG = "AnalyticsWidget"
        private const val ACTION_REFRESH = "dev.mateux.caparao.widget.ANALYTICS_REFRESH"
        private const val WIDE_MIN_WIDTH_DP = 250f
        private const val EXPANDED_MIN_HEIGHT_DP = 180f
        private const val EXPANDED_CHART_FIXED_HEIGHT_DP = 104f
        private const val CHART_LABEL_ROW_HEIGHT_DP = 16f
        private const val CHART_LABEL_BOTTOM_PADDING_DP = 4f
        private const val SQUARE_CHART_FIXED_HEIGHT_DP = 156f
        private const val SPARKLINE_MIN_HEIGHT_DP = 24f
        private const val SPARKLINE_HEIGHT_DP = 28f
        internal val CONFIGURATION_ACTIONS = setOf(Intent.ACTION_LOCALE_CHANGED, Intent.ACTION_CONFIGURATION_CHANGED)

        fun requestRefresh(context: Context) {
            val component = ComponentName(context, AnalyticsWidgetProvider::class.java)
            if (AppWidgetManager.getInstance(context).getAppWidgetIds(component).isNotEmpty()) {
                context.sendBroadcast(Intent(context, AnalyticsWidgetProvider::class.java).setAction(ACTION_REFRESH))
            }
        }
    }
}
