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
    pendingIntent: (String) -> PendingIntent,
): RemoteViews {
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
    views.setOnClickPendingIntent(R.id.widget_action, pendingIntent(shortcut.action))
    return views
}

private fun actionLabel(mode: BrewMode?): Int = when (mode) {
    null -> R.string.widget_brew_coffee
    BrewMode.ESPRESSO -> R.string.widget_brew_espresso
    BrewMode.FILTER -> R.string.widget_brew_filter
    BrewMode.FRENCH_PRESS -> R.string.widget_brew_french_press
}
