package io.github.mateuxlucax.caparao.ui.widget

import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.ui.components.icon
import io.github.mateuxlucax.caparao.ui.components.LauncherShortcut

/** Espresso suggests Filter; both water-based methods suggest Espresso. French press is never suggested. */
internal fun suggestedModeAfter(latestMode: BrewMode): BrewMode = when (latestMode) {
    BrewMode.ESPRESSO -> BrewMode.FILTER
    BrewMode.FILTER, BrewMode.FRENCH_PRESS -> BrewMode.ESPRESSO
}

/** The action icon matches the suggested mode; the empty widget opens the chooser and has no method icon. */
internal fun actionIconForWidget(latestMode: BrewMode?): Int? =
    latestMode?.let(::suggestedModeAfter)?.icon

/** The widget's action always uses the same action MainActivity routes for launcher shortcuts. */
internal fun shortcutForWidget(latestMode: BrewMode?): LauncherShortcut =
    latestMode?.let { LauncherShortcut.forMode(suggestedModeAfter(it)) } ?: LauncherShortcut.WIDGET_HOME
