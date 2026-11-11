package io.github.mateuxlucax.caparao.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.compositionLocalOf

/**
 * Where a screen registers its own system-back policy. Navigation 3 owns the gesture and invokes the action of the top
 * entry, so predictive back follows the navigation transition instead of stopping at the screen (#352).
 */
internal val LocalSystemBack = compositionLocalOf<((() -> Unit)?) -> Unit> { {} }

/** Registers [action] as this entry's system-back behavior while the screen is composed (#352). */
@Composable
fun SystemBack(action: () -> Unit) {
    val register = LocalSystemBack.current
    val current by rememberUpdatedState(action)
    DisposableEffect(register) {
        register { current() }
        onDispose { register(null) }
    }
}
