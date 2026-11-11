package io.github.mateuxlucax.caparao.ui

import androidx.navigation3.runtime.NavKey

/**
 * Pops [key] only while it is still the top entry, and never the last one (#352). A screen stays composed during its
 * exit transition, so a second close or system back must not pop the entry underneath it and leave an empty stack.
 */
internal fun MutableList<NavKey>.popEntry(key: NavKey) {
    if (size > 1 && lastOrNull() == key) removeAt(lastIndex)
}
