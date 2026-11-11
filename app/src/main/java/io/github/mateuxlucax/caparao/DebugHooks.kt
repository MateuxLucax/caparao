package io.github.mateuxlucax.caparao

import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.mateuxlucax.caparao.data.scale.ScaleSource
import kotlinx.coroutines.CoroutineScope

/** Build-variant seam for launch-time tooling; shipping builds use the no-op implementation. */
interface DebugHooks {
    fun prepare(intent: Intent, savedInstanceState: Bundle?) = Unit
    fun createScale(context: Context, scope: CoroutineScope): ScaleSource? = null
    fun onActivityCreate(container: AppContainer) = Unit
}
