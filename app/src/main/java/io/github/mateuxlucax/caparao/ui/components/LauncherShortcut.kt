package io.github.mateuxlucax.caparao.ui.components

import io.github.mateuxlucax.caparao.model.BrewMode

/** Where a launcher shortcut lands (#116). */
sealed interface ShortcutDestination {
    /** The Home mode selector, used by a widget with no saved brews. */
    data object Home : ShortcutDestination

    /** A brew of [mode], opened at the dose step: the user still weighs the beans and starts it. */
    data class Brew(val mode: BrewMode) : ShortcutDestination

    /** The saved-brews tab. */
    data object Brews : ShortcutDestination
}

/** The intent action prefix of every shortcut; the uppercase id follows it. */
private const val ACTION_PREFIX = "io.github.mateuxlucax.caparao.shortcut"

/**
 * Launcher actions: the static Espresso, Filter, French press and Brews shortcuts (#116, #126), plus the widget-only Home action. The
 * static ids and actions are written in `res/xml/shortcuts.xml` (and its debug copy, which only changes the target
 * package); the launcher stores them, so they stay stable.
 */
enum class LauncherShortcut(val id: String, val action: String, val destination: ShortcutDestination) {
    ESPRESSO("espresso", "$ACTION_PREFIX.ESPRESSO", ShortcutDestination.Brew(BrewMode.ESPRESSO)),
    FILTER("filter", "$ACTION_PREFIX.FILTER", ShortcutDestination.Brew(BrewMode.FILTER)),
    FRENCH_PRESS("french-press", "$ACTION_PREFIX.FRENCH_PRESS", ShortcutDestination.Brew(BrewMode.FRENCH_PRESS)),
    BREWS("brews", "$ACTION_PREFIX.BREWS", ShortcutDestination.Brews),
    WIDGET_HOME("widget-home", "$ACTION_PREFIX.WIDGET_HOME", ShortcutDestination.Home),
    ;

    companion object {
        /** The actions shown in the launcher's long-press menu. */
        val staticEntries = listOf(ESPRESSO, FILTER, FRENCH_PRESS, BREWS)

        /** The shortcut an intent action asks for; anything else (the launcher's MAIN included) opens the app as usual. */
        fun fromAction(action: String?): LauncherShortcut? = entries.firstOrNull { it.action == action }

        /** The existing brew entry point for a selected mode. */
        fun forMode(mode: BrewMode): LauncherShortcut = entries.first { (it.destination as? ShortcutDestination.Brew)?.mode == mode }

        /** The shortcut saved across a re-creation (savedInstanceState). */
        fun fromId(id: String?): LauncherShortcut? = entries.firstOrNull { it.id == id }

        /**
         * What is still pending when the activity is created. On a re-creation (rotation, restored process) the saved id
         * is the truth, so a shortcut already consumed is never applied again from the same launch intent.
         */
        fun pendingOnCreate(savedId: String?, launchAction: String?, recreated: Boolean): LauncherShortcut? =
            if (recreated) fromId(savedId) else fromAction(launchAction)
    }
}
