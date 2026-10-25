package io.github.mateuxlucax.caparao.data.scale

import io.github.mateuxlucax.caparao.model.ScaleBattery
import io.github.mateuxlucax.caparao.model.ScaleReading

/** A frame sent by the scale, decoded. */
sealed class ScaleMessage {
    data class Weight(val reading: ScaleReading) : ScaleMessage()

    data class Battery(val battery: ScaleBattery) : ScaleMessage()

    /** Acknowledgement of the write command [cmd]. */
    data class Ack(val cmd: Int) : ScaleMessage()

    data class Model(val model: String) : ScaleMessage()

    /** A well-formed frame the app does not know (or with a payload too short for its type). */
    class Unknown(val type: Int, val cmd: Int, val payload: ByteArray) : ScaleMessage() {
        override fun equals(other: Any?) =
            other is Unknown && type == other.type && cmd == other.cmd && payload.contentEquals(other.payload)

        override fun hashCode() = (type * 31 + cmd) * 31 + payload.contentHashCode()

        override fun toString() = "Unknown(type=$type, cmd=$cmd, payload=${payload.joinToString("") { "%02x".format(it) }})"
    }
}

/**
 * Turns the scale's notification chunks into messages. A notification may hold several frames and a frame may be split
 * across two, so bytes are buffered; after garbage or an absurd length the decoder resyncs on the next `A5 5A`.
 * The incoming CRC is never checked: the Dot sends `00 00` (docs/hardware.md). Not thread-safe; feed it from one
 * thread, in order.
 */
class FrameDecoder {
    private val buffer = ByteArray(MAX_BUFFER)
    private var size = 0

    /** Appends [chunk] and returns every frame it completed, in order. */
    fun feed(chunk: ByteArray): List<ScaleMessage> {
        append(chunk)
        val messages = mutableListOf<ScaleMessage>()
        while (true) {
            val start = indexOfHeader()
            if (start < 0) {
                // Keep a trailing A5: its 5A may arrive in the next chunk.
                val keep = if (size > 0 && buffer[size - 1] == DotProtocol.HEADER_1) 1 else 0
                discard(size - keep)
                break
            }
            discard(start)
            if (size < DotProtocol.HEADER_SIZE) break
            val length = ((buffer[4].toInt() and 0xFF) shl 8) or (buffer[5].toInt() and 0xFF)
            if (length > DotProtocol.MAX_PAYLOAD) {
                discard(1) // not a real header: look for the next one
                continue
            }
            val total = DotProtocol.HEADER_SIZE + length + DotProtocol.CRC_SIZE
            if (size < total) break
            val payload = buffer.copyOfRange(DotProtocol.HEADER_SIZE, DotProtocol.HEADER_SIZE + length)
            messages += decode(buffer[2].toInt() and 0xFF, buffer[3].toInt() and 0xFF, payload)
            discard(total)
        }
        return messages
    }

    /** Forgets buffered bytes, e.g. after a reconnect. */
    fun reset() {
        size = 0
    }

    /** Keeps the buffer bounded: when it would overflow, the oldest bytes go. */
    private fun append(chunk: ByteArray) {
        val from = maxOf(0, chunk.size - MAX_BUFFER)
        val count = chunk.size - from
        if (size + count > MAX_BUFFER) discard(size + count - MAX_BUFFER)
        chunk.copyInto(buffer, size, from)
        size += count
    }

    private fun discard(count: Int) {
        buffer.copyInto(buffer, 0, count, size)
        size -= count
    }

    private fun indexOfHeader(): Int {
        for (i in 0 until size - 1) {
            if (buffer[i] == DotProtocol.HEADER_1 && buffer[i + 1] == DotProtocol.HEADER_2) return i
        }
        return -1
    }

    private fun decode(type: Int, cmd: Int, p: ByteArray): ScaleMessage = when {
        type == DotProtocol.TYPE_NOTIFY && cmd == DotProtocol.CMD_WEIGHT && p.size in WEIGHT_SIZES ->
            ScaleMessage.Weight(
                ScaleReading(
                    weightGrams = int32(p, 0) / 10.0,
                    flowGramsPerSecond = int16(p, 4) / 10.0,
                    timerSeconds = uint16(p, 6),
                    overload = p.size > 8 && p[8].toInt() != 0,
                ),
            )
        // The reply to a battery read, and the push the Dot sends on its own about every 30 s (docs/hardware.md).
        (type == DotProtocol.TYPE_READ || type == DotProtocol.TYPE_NOTIFY) && cmd == DotProtocol.CMD_BATTERY && p.size >= 2 ->
            ScaleMessage.Battery(ScaleBattery(percent = p[1].toInt() and 0xFF, bars = p[0].toInt() and 0xFF))
        type == DotProtocol.TYPE_READ && cmd == DotProtocol.CMD_MODEL ->
            ScaleMessage.Model(String(p, Charsets.US_ASCII).trimEnd('\u0000'))
        type == DotProtocol.TYPE_WRITE -> ScaleMessage.Ack(cmd)
        else -> ScaleMessage.Unknown(type, cmd, p)
    }

    private fun uint16(p: ByteArray, at: Int) = ((p[at].toInt() and 0xFF) shl 8) or (p[at + 1].toInt() and 0xFF)

    private fun int16(p: ByteArray, at: Int) = uint16(p, at).toShort().toInt()

    private fun int32(p: ByteArray, at: Int) = (uint16(p, at) shl 16) or uint16(p, at + 2)

    private companion object {
        /** More than one MTU-sized notification plus a partial frame. */
        const val MAX_BUFFER = 512

        /** Weight payload with and without the trailing overload flag. */
        val WEIGHT_SIZES = 8..9
    }
}
