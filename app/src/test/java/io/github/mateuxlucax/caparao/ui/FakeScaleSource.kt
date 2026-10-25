package io.github.mateuxlucax.caparao.ui

import io.github.mateuxlucax.caparao.data.scale.ScaleSource
import io.github.mateuxlucax.caparao.data.scale.TimerAction
import io.github.mateuxlucax.caparao.model.ScaleReading
import io.github.mateuxlucax.caparao.model.ScaleState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** A scale the test drives by hand; records the commands it receives. */
class FakeScaleSource : ScaleSource {
    override val state = MutableStateFlow<ScaleState>(ScaleState.Idle)
    override val reading = MutableStateFlow<ScaleReading?>(null)
    override val log = MutableStateFlow<List<String>>(emptyList())
    override val readings = MutableSharedFlow<ScaleReading>(extraBufferCapacity = 64)

    /** Delivers [reading] like the scale does: to both flows. */
    fun send(reading: ScaleReading) {
        this.reading.value = reading
        check(readings.tryEmit(reading))
    }
    val commands = mutableListOf<String>()

    override fun start() {
        commands += "start"
    }

    override fun stop() {
        commands += "stop"
    }

    override fun tare() {
        commands += "tare"
    }

    override fun timer(action: TimerAction) {
        commands += "timer ${action.name.lowercase()}"
    }
}
