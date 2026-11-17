package dev.mateux.caparao.ui.widget

import android.app.PendingIntent
import android.content.Context
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import dev.mateux.caparao.R
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.ui.components.icon
import dev.mateux.caparao.ui.components.label

/** Builds icon-only and pill variants; larger widths reveal the method summary and text action. */
internal fun brewWidgetViews(
    context: Context,
    latest: Brew?,
    small: Boolean = false,
    wide: Boolean = false,
    pendingIntent: (String) -> PendingIntent,
): RemoteViews {
    val mode = latest?.mode
    val shortcut = shortcutForWidget(mode)
    val coffee = latest?.coffee?.takeIf(String::isNotBlank) ?: latest?.recipeName ?: latest?.let { context.getString(it.mode.label) }
    val accessibility = if (latest == null) context.getString(R.string.widget_empty_start)
    else context.getString(R.string.widget_content_description, coffee)
    if (small) return RemoteViews(context.packageName, R.layout.widget_brew_small).apply {
        setImageViewResource(R.id.widget_small_icon, mode?.icon ?: R.drawable.ic_coffee)
        setContentDescription(R.id.widget_action, accessibility)
        setOnClickPendingIntent(R.id.widget_action, pendingIntent(shortcut.action))
    }

    val views = RemoteViews(context.packageName, R.layout.widget_brew)
    views.setImageViewResource(R.id.widget_method_icon, mode?.icon ?: R.drawable.ic_coffee)
    views.setViewVisibility(R.id.widget_meta, if (wide) View.VISIBLE else View.GONE)
    views.setTextViewText(R.id.widget_action_label, context.getString(R.string.widget_brew_again))
    views.setViewLayoutWidth(R.id.widget_action_button, if (wide) 132f else 40f, android.util.TypedValue.COMPLEX_UNIT_DIP)
    views.setContentDescription(R.id.widget_action, accessibility)
    views.setContentDescription(R.id.widget_action_button, accessibility)
    views.setOnClickPendingIntent(R.id.widget_action, pendingIntent(shortcut.action))
    views.setOnClickPendingIntent(R.id.widget_action_button, pendingIntent(shortcut.action))
    if (latest == null) {
        views.setTextViewText(R.id.widget_coffee, context.getString(R.string.widget_empty_title_yet))
        views.setTextViewText(R.id.widget_meta, "")
        views.setContentDescription(R.id.widget_action, context.getString(R.string.widget_empty_start))
        views.setContentDescription(R.id.widget_action_button, context.getString(R.string.widget_empty_start))
        views.setViewVisibility(R.id.widget_action_button, View.VISIBLE)
    } else {
        views.setTextViewText(R.id.widget_coffee, coffee)
        val details = context.getString(R.string.widget_brew_summary, context.getString(latest.mode.label), latest.doseGrams, latest.yieldGrams)
        val summary = context.getString(R.string.widget_brew_short_summary, latest.doseGrams, latest.yieldGrams)
        views.setTextViewText(R.id.widget_meta, if (wide) "$details · ${timeAgo(context, latest.startedAt)}" else summary)
        views.setViewVisibility(R.id.widget_action_button, View.VISIBLE)
        if (wide) {
            views.setViewVisibility(R.id.widget_action_icon, View.VISIBLE)
            views.setViewVisibility(R.id.widget_action_label, View.VISIBLE)
            views.setViewVisibility(R.id.widget_action_button, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_action_icon, View.VISIBLE)
            views.setViewVisibility(R.id.widget_action_label, View.GONE)
        }
    }
    if (latest == null) {
        views.setImageViewResource(R.id.widget_action_icon, R.drawable.ic_coffee)
        views.setViewVisibility(R.id.widget_action_label, View.GONE)
    }
    return views
}

internal fun timeAgo(context: Context, startedAt: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - startedAt).coerceAtLeast(0) / 60_000).toInt()
    return when {
        minutes < 1 -> context.getString(R.string.widget_just_now)
        minutes < 60 -> context.getString(R.string.widget_minutes_ago, minutes)
        minutes < 1_440 -> context.getString(R.string.widget_hours_ago, minutes / 60)
        else -> context.getString(R.string.widget_days_ago, minutes / 1_440)
    }
}
