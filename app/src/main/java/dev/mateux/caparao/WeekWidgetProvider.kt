package dev.mateux.caparao

import android.app.LocaleManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import dev.mateux.caparao.data.local.SettingsStore
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.ui.MainActivity
import dev.mateux.caparao.ui.widget.WeekStats
import dev.mateux.caparao.ui.widget.weekStats
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class WeekWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) = refresh(context, manager, appWidgetIds)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) = refresh(context, manager, intArrayOf(appWidgetId))
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH || intent.action in CONFIGURATION_ACTIONS) {
            val manager = AppWidgetManager.getInstance(context)
            refresh(context, manager, manager.getAppWidgetIds(ComponentName(context, WeekWidgetProvider::class.java)))
        }
    }

    private fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pending = goAsync(); val finished = AtomicBoolean(false); val finish = { if (finished.compareAndSet(false, true)) pending.finish() }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val watchdog = launch { delay(2_250); finish() }
            try {
                withTimeoutOrNull(2_000) {
                    val container = (context.applicationContext as CaparaoApplication).container
                    val brews = container.brews.brews.first()
                    ids.forEach { id ->
                        val options = manager.getAppWidgetOptions(id)
                        val density = context.resources.displayMetrics.density
                        val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty().ifEmpty { listOf(SizeF(220f, 110f)) }
                        manager.updateAppWidget(id, RemoteViews(sizes.associateWith { size -> render(context, id, brews, size, density) }))
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { android.util.Log.w("WeekWidget", "Week widget refresh failed", failure) }
            finally { watchdog.cancel(); finish() }
        }
    }

    internal fun render(source: Context, id: Int, brews: List<Brew>, size: SizeF, density: Float = source.resources.displayMetrics.density, today: LocalDate = LocalDate.now(ZoneId.systemDefault())): RemoteViews {
        val context = localizedContext(source)
        val narrow = size.width < 250
        val views = RemoteViews(context.packageName, if (narrow) R.layout.widget_week_narrow else R.layout.widget_week_tall)
        val zone = ZoneId.systemDefault()
        val stats = weekStats(brews, today, zone)
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val labels = stats.days.map { day -> day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale).uppercase(locale).take(1) }
        val empty = stats.brews == 0
        val countView = if (narrow) R.id.week_narrow_count else R.id.week_count
        val statsView = if (narrow) R.id.week_narrow_stats else R.id.week_stats
        val chartView = if (narrow) R.id.week_narrow_chart else R.id.week_chart
        views.setTextViewText(countView, stats.brews.toString())
        views.setTextViewText(if (narrow) R.id.week_narrow_brew_label else R.id.week_brew_label, context.getString(if (stats.brews == 1) R.string.week_brew_label else R.string.week_brews_label))
        views.setTextColor(countView, context.getColor(if (empty) R.color.widget_secondary_text else R.color.widget_hero_text))
        views.setViewVisibility(statsView, if (narrow || empty) View.GONE else View.VISIBLE)
        if (!narrow) {
            views.setViewVisibility(R.id.week_empty_text, if (empty) View.VISIBLE else View.GONE)
            if (empty) {
            views.setTextViewText(R.id.week_empty_text, context.getString(R.string.week_empty))
            }
        }
        if (!narrow) {
            views.setTextViewText(statsView, if (empty) context.getString(R.string.week_empty) else context.getString(R.string.week_stats, String.format(locale, "%.0f", stats.grams), String.format(locale, "%.1f", stats.liters)))
        }
        labels.forEachIndexed { index, label ->
            val dayId = (if (narrow) R.id.week_narrow_day_0 else R.id.week_day_0) + index
            val text = if (index == 6) SpannableString(label).apply { setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) } else label
            views.setTextViewText(dayId, text)
            views.setTextColor(dayId, context.getColor(R.color.widget_secondary_text))
            if (index == 6) views.setTextViewTextSize(dayId, android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        val chartWidth = ((size.width - 32) * density).toInt().coerceAtLeast(1)
        val chartHeight = ((size.height - if (narrow) 94 else 110) * density).toInt().coerceAtLeast(1)
        views.setImageViewBitmap(chartView, chart(context, stats, chartWidth, chartHeight, density))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(countView, statsView).forEach { view ->
                val color = if (view == countView) if (empty) R.color.widget_secondary_text else R.color.widget_hero_text else R.color.widget_secondary_text
                views.setColorStateList(view, "setTextColor", color)
            }
        }
        views.setContentDescription(if (narrow) R.id.week_narrow_root else R.id.week_root, context.getString(R.string.week_description, stats.counts.joinToString()))
        val intent = Intent(context, MainActivity::class.java).setAction("dev.mateux.caparao.shortcut.ANALYTICS")
        val pending = android.app.PendingIntent.getActivity(context, id, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(if (narrow) R.id.week_narrow_root else R.id.week_root, pending)
        return views
    }

    private fun chart(context: Context, stats: WeekStats, width: Int, height: Int, density: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); val canvas = Canvas(bitmap)
        val primary = context.getColor(R.color.widget_hero_text)
        val outline = context.getColor(R.color.widget_outline)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val slot = width / 7f; val max = stats.counts.maxOrNull()?.coerceAtLeast(1) ?: 1
        stats.counts.forEachIndexed { i, count ->
            val barWidth = slot * .56f; val left = i * slot + (slot - barWidth) / 2
            paint.color = if (count == 0) outline else primary
            paint.alpha = if (i == 6 || count == 0) 255 else 102
            val barHeight = if (count == 0) 2f * density else (height * .88f * count / max).coerceAtLeast(2f * density)
            val top = height - barHeight
            canvas.drawRoundRect(RectF(left, top, left + barWidth, height.toFloat()), 4f * density, 4f * density, paint)
        }
        return bitmap
    }

    private fun localizedContext(context: Context): Context {
        if (Build.VERSION.SDK_INT < 33) return context
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        return if (locales.isEmpty) context else context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocales(locales) })
    }

    private fun Context.getColorInMode(color: Int, mode: Int): Int {
        val configuration = Configuration(resources.configuration).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode }
        return createConfigurationContext(configuration).getColor(color)
    }

    companion object {
        private const val ACTION_REFRESH = "dev.mateux.caparao.widget.WEEK_REFRESH"
        private val CONFIGURATION_ACTIONS = setOf(Intent.ACTION_LOCALE_CHANGED, Intent.ACTION_CONFIGURATION_CHANGED)
        fun requestRefresh(context: Context) {
            if (AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, WeekWidgetProvider::class.java)).isNotEmpty()) context.sendBroadcast(Intent(context, WeekWidgetProvider::class.java).setAction(ACTION_REFRESH))
        }
    }
}
