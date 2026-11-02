package io.github.mateuxlucax.caparao.ui.launcher

import io.github.mateuxlucax.caparao.model.BrewMode

/** Where a launcher shortcut lands (#116). */
sealed interface ShortcutDestination {
    /** A brew of [mode], opened at the dose step: the user still weighs the beans and starts it. */
    data class Brew(val mode: BrewMode) : ShortcutDestination

    /** The saved-brews tab. */
    data object Brews : ShortcutDestination
}

/** The intent action prefix of every shortcut; the uppercase id follows it. */
private const val ACTION_PREFIX = "io.github.mateuxlucax.caparao.shortcut"

/**
 * The static launcher shortcuts (#116): Espresso, Filter and Brews. [id] and [action] are written in
 * `res/xml/shortcuts.xml` (and its debug copy, which only changes the target package): the launcher stores them, so
 * they stay stable.
 */
enum class LauncherShortcut(val id: String, val action: String, val destination: ShortcutDestination) {
    ESPRESSO("espresso", "$ACTION_PREFIX.ESPRESSO", ShortcutDestination.Brew(BrewMode.ESPRESSO)),
    FILTER("filter", "$ACTION_PREFIX.FILTER", ShortcutDestination.Brew(BrewMode.FILTER)),
    BREWS("brews", "$ACTION_PREFIX.BREWS", ShortcutDestination.Brews),
    ;

    companion object {
        /** The shortcut an intent action asks for; anything else (the launcher's MAIN included) opens the app as usual. */
        fun fromAction(action: String?): LauncherShortcut? = entries.firstOrNull { it.action == action }

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
