package dev.mateux.caparao.ui.widget

import android.app.PendingIntent
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import dev.mateux.caparao.R
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.ui.components.LauncherShortcut
import dev.mateux.caparao.ui.components.icon
import dev.mateux.caparao.ui.components.label

/**
 * Builds the home-screen widget's [RemoteViews] from the latest saved brew. The root package keeps the provider and
 * supplies the launch [PendingIntent], so this feature package never refers to the app's entry point.
 */
internal fun brewWidgetViews(
    context: Context,
    latest: Brew?,
    wide: Boolean = false,
    pendingIntent: (String) -> PendingIntent,
): RemoteViews {
    val views = RemoteViews(context.packageName, if (wide) R.layout.widget_brew_wide else R.layout.widget_brew)
    val mode = latest?.mode
    val suggestedMode = mode?.let(::suggestedModeAfter)
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
        val method = context.getString(if (latest.mode == BrewMode.FRENCH_PRESS) R.string.mode_french_press_short else latest.mode.label)
        views.setTextViewText(R.id.widget_method, context.getString(R.string.widget_brew_summary, method, latest.doseGrams, latest.yieldGrams))
        views.setTextViewText(R.id.widget_coffee, latest.coffee ?: latest.recipeName ?: context.getString(latest.mode.label))
        views.setTextViewText(R.id.widget_time, timeAgo(context, latest.startedAt))
    }
    val actionLabel = context.getString(actionLabel(suggestedMode))
    views.setTextViewText(R.id.widget_action_label, actionLabel)
    views.setContentDescription(R.id.widget_action, actionLabel)
    views.setOnClickPendingIntent(R.id.widget_action, pendingIntent(shortcut.action))
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

private fun actionLabel(mode: BrewMode?): Int = when (mode) {
    null -> R.string.widget_brew_coffee
    BrewMode.ESPRESSO -> R.string.widget_brew_espresso
    BrewMode.FILTER -> R.string.widget_brew_filter
    BrewMode.FRENCH_PRESS -> R.string.widget_brew_french_press
}
