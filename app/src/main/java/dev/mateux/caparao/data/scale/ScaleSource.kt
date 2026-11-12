package dev.mateux.caparao.data.scale

import dev.mateux.caparao.model.ScaleReading
import dev.mateux.caparao.model.ScaleState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The scale as the screens see it: the real Dot over Bluetooth ([ScaleClient]) or a recorded session
 * (debug-only replay, for the emulator and tests).
 */
interface ScaleSource {
    val state: StateFlow<ScaleState>

    /** The latest reading while connected, null otherwise. */
    val reading: StateFlow<ScaleReading?>

    /**
     * Every reading as it arrives, equal ones included ([reading] skips repeats). Brew detection needs them: a still
     * cup sends the same reading for seconds, and that stillness is what ends an espresso.
     */
    val readings: SharedFlow<ScaleReading>

    /** What happened on the connection, newest last, for the "copy debug log" button. Never holds identifiers. */
    val log: StateFlow<List<String>>

    /** Starts connecting and keeps the connection up until [stop]. Does nothing if already started. */
    fun start()

    fun stop()

    fun tare()

    fun timer(action: TimerAction)
}

/** Monotonic time exposed by deterministic scale sources for brew detection. */
interface ScaleClock {
    val nowMs: Long
}

enum class TimerAction { START, PAUSE, RESET }
