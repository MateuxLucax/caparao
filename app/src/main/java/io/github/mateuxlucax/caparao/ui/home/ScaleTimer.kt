package io.github.mateuxlucax.caparao.ui.home

import io.github.mateuxlucax.caparao.data.scale.ScaleSource
import io.github.mateuxlucax.caparao.data.scale.TimerAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The scale's timer as Scale mode drives it, shared by Home and the watch so both show the same button. The Dot does
 * not report whether its timer runs, so this remembers what was asked.
 */
class ScaleTimer(private val scale: () -> ScaleSource) {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun toggle() {
        val running = !_running.value
        scale().timer(if (running) TimerAction.START else TimerAction.PAUSE)
        _running.value = running
    }

    fun reset() {
        scale().timer(TimerAction.RESET)
        _running.value = false
    }
}
