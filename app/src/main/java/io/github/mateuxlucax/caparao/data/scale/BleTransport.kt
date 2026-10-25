package io.github.mateuxlucax.caparao.data.scale

import kotlinx.coroutines.flow.Flow

/**
 * The Bluetooth operations [ScaleClient] needs, one at a time. The Android implementation wraps [android.bluetooth]
 * (docs/adr/0002-native-ble.md); tests use a fake, so the connection logic runs on the JVM.
 */
interface BleTransport {
    /** Whether Bluetooth can be used right now. */
    fun readiness(): BleReadiness

    /**
     * Scans for a Timemore Dot (name `TIMEMORE_Dot` or containing `TES017`) for at most [timeoutMs] and returns its
     * address, or null when none is found.
     */
    suspend fun scan(timeoutMs: Long): String?

    /**
     * Connects to [address] and makes it ready to talk: MTU 247, bonded, services discovered, notifications on.
     * Throws [BleException] when it cannot.
     */
    suspend fun open(address: String)

    /** Writes [frame] to the scale's write characteristic (without response). Throws [BleException] on failure. */
    suspend fun write(frame: ByteArray)

    /** Drops the connection, if any, and frees its resources. Safe to call at any time. */
    fun close()

    /** Notification chunks from the scale and link loss, for the connection opened last. */
    val events: Flow<BleEvent>
}

enum class BleReadiness { READY, BLUETOOTH_OFF, PERMISSION_MISSING }

sealed interface BleEvent {
    /** Bytes of one notification, exactly as received (may hold several frames or part of one). */
    class Notification(val bytes: ByteArray) : BleEvent

    /** The connection dropped. */
    data object LinkLost : BleEvent
}

class BleException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason {
        /** Pairing was refused or the Dot dropped the link right away: it is bonded to another phone. */
        PAIRING_REFUSED,

        /** Any other failure: connection, MTU, service discovery, notifications or a write. */
        FAILED,
    }
}
