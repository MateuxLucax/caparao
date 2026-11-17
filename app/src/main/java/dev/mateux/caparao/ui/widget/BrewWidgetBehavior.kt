package dev.mateux.caparao.ui.widget

import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.ui.components.icon
import dev.mateux.caparao.ui.components.LauncherShortcut

/** The method icon and repeated brew shortcut follow the last saved brew. */
internal fun actionIconForWidget(latestMode: BrewMode?): Int? = latestMode?.icon

internal fun shortcutForWidget(latestMode: BrewMode?): LauncherShortcut =
    latestMode?.let(LauncherShortcut::forMode) ?: LauncherShortcut.WIDGET_HOME
