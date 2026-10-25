package io.github.mateuxlucax.caparao.probe

/** Frame helpers for the Dot protocol (docs/hardware.md). Pure Kotlin so it can be unit tested on the JVM. */
object Frames {
    /** CRC-16/MODBUS: init 0xFFFF, reflected polynomial 0xA001. */
    fun crc16(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
        }
        return crc
    }

    /** `A5 5A <type> <cmd> <len_hi> <len_lo> <payload> <crc_hi> <crc_lo>`; the CRC covers every byte before it. */
    fun build(type: Int, cmd: Int, vararg payload: Int): ByteArray {
        val head = byteArrayOf(0xA5.toByte(), 0x5A, type.toByte(), cmd.toByte(), 0, payload.size.toByte()) +
            ByteArray(payload.size) { payload[it].toByte() }
        val crc = crc16(head)
        return head + byteArrayOf((crc ushr 8).toByte(), crc.toByte())
    }

    fun hex(data: ByteArray): String = data.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    /** A decoded incoming frame; the CRC is never validated (the scale sends 00 00). */
    class Incoming(val type: Int, val cmd: Int, val payload: ByteArray)

    /**
     * Reassembles frames from notifications: one notification may hold several frames and a frame may be split across
     * two. Resyncs on `A5 5A` and drops any declared length above 64.
     */
    class Reader {
        private var buf = ByteArray(0)

        fun feed(chunk: ByteArray): List<Incoming> {
            buf += chunk
            val out = mutableListOf<Incoming>()
            while (true) {
                val start = (0 until buf.size - 1).firstOrNull {
                    buf[it] == 0xA5.toByte() && buf[it + 1] == 0x5A.toByte()
                }
                if (start == null) {
                    buf = if (buf.lastOrNull() == 0xA5.toByte()) byteArrayOf(0xA5.toByte()) else ByteArray(0)
                    return out
                }
                if (start > 0) buf = buf.copyOfRange(start, buf.size)
                if (buf.size < 6) return out
                val len = ((buf[4].toInt() and 0xFF) shl 8) or (buf[5].toInt() and 0xFF)
                if (len > 64) {
                    buf = buf.copyOfRange(2, buf.size)
                    continue
                }
                val total = 6 + len + 2
                if (buf.size < total) return out
                out += Incoming(buf[2].toInt() and 0xFF, buf[3].toInt() and 0xFF, buf.copyOfRange(6, 6 + len))
                buf = buf.copyOfRange(total, buf.size)
            }
        }
    }
}
