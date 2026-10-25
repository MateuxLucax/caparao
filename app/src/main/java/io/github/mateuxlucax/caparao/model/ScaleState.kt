package io.github.mateuxlucax.caparao.model

/** Where the connection to the scale stands. The scale is only connected while the app is open (docs/adr/0008-connect-while-open.md). */
sealed interface ScaleState {
    /** Not connected and not trying: the app is in the background, or nothing has started the connection yet. */
    data object Idle : ScaleState

    /** Looking for a Timemore Dot nearby (no scale remembered yet, or the remembered one is not around). */
    data object Searching : ScaleState

    /** Found; connecting, pairing and setting it up. */
    data object Connecting : ScaleState

    /** Streaming readings. [battery] is null until the first battery answer. */
    data class Connected(val battery: ScaleBattery?) : ScaleState

    /** Not connected because of [error]; when [retrying], the app keeps trying on its own. */
    data class Failed(val error: ScaleError, val retrying: Boolean) : ScaleState
}

enum class ScaleError {
    /** Bluetooth is turned off on the phone. */
    BLUETOOTH_OFF,

    /** The nearby devices permission is not granted. */
    PERMISSION_MISSING,

    /** No Timemore Dot found nearby: off, out of range, or connected to another app. */
    NOT_FOUND,

    /** The Dot refused the connection or pairing: it is paired with another phone and must be reset with its button. */
    PAIRED_ELSEWHERE,

    /** The connection dropped or a Bluetooth operation failed. */
    CONNECTION_LOST,
}
