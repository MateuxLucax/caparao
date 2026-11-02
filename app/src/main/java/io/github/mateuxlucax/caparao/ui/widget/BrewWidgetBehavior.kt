package io.github.mateuxlucax.caparao.ui.widget

import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.ui.components.icon
import io.github.mateuxlucax.caparao.ui.launcher.LauncherShortcut

/** The widget alternates Espresso and Filter; any future mode (including French press) suggests Espresso. */
internal fun suggestedModeAfter(latestMode: BrewMode): BrewMode =
    if (latestMode == BrewMode.ESPRESSO) BrewMode.FILTER else BrewMode.ESPRESSO

/** The action icon matches the suggested mode; the empty widget opens the chooser and has no method icon. */
internal fun actionIconForWidget(latestMode: BrewMode?): Int? =
    latestMode?.let(::suggestedModeAfter)?.icon

/** The widget's action always uses the same action MainActivity routes for launcher shortcuts. */
internal fun shortcutForWidget(latestMode: BrewMode?): LauncherShortcut =
    latestMode?.let { LauncherShortcut.forMode(suggestedModeAfter(it)) } ?: LauncherShortcut.WIDGET_HOME
