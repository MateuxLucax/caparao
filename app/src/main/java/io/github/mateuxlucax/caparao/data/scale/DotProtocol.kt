package io.github.mateuxlucax.caparao.data.scale

import java.util.UUID

/**
 * Wire format of the Timemore Dot: GATT identifiers, frame types, command ids and the encoders for
 * what the app sends. Pure Kotlin so it runs on the JVM with recorded frames (#86).
 *
 * Device: model `TES017`, advertised name `TIMEMORE_Dot` (matched by name or model only, never a bare "dot").
 * No display; one power button under the scale and an LED. Remembers one bonded phone and drops other
 * connections (HCI 0x3E); to pair another phone hold the button until the LED blinks fast.
 *
 * GATT: service `0000fff0-…`, notify `0000fff1-…` (scale → app), write without response `0000fff2-…` (app → scale).
 * Connection: connect (up to 3 attempts), request MTU 247, bond (required: the Dot streams only on an encrypted
 * link), enable notifications on FFF1, then grams (`03 06 [00]`) and standard mode (`03 08 [01 00]`); battery
 * is polled every 60 s (`02 05`).
 *
 * Frame layout: `A5 5A <type> <cmd> <len_hi> <len_lo> <payload…> <crc_hi> <crc_lo>`. `type` is `01` notification,
 * `02` read, `03` write (and its acknowledgement as `03 <cmd> 00 01 01`). Outgoing CRC is CRC-16/MODBUS
 * (init `0xFFFF`, reflected poly `0xA001`) over every byte before it, big-endian. Incoming CRC is `00 00` and
 * is never validated. One notification may hold several frames and a frame may be split across two; the decoder
 * buffers and resyncs on `A5 5A`, dropping any declared length above 64.
 *
 * Known quirks (confirmed on a real Dot): the scale's flow saturates at 99.9 on sudden loads — clamp and smooth
 * in the app (`FlowSmoother`). The overload flag is payload byte 8 (set ~1300 g). Weight at power-on reads
 * negative once removed (the Dot zeroes itself at boot). No stability flag is provided — stability is computed
 * in the app. No keep-alive is needed: 5 min 42 s connected and idle saw no drop. Battery sometimes arrives
 * as a push (`01 05`) and sometimes only as a read reply (`02 05`); the app polls and takes either.
 */
object DotProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")

    /** Scale to app notifications. */
    val NOTIFY_UUID: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")

    /** App to scale, written without response. */
    val WRITE_UUID: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")

    const val HEADER_1: Byte = 0xA5.toByte()
    const val HEADER_2: Byte = 0x5A

    /** Header, type, command and the two length bytes. */
    const val HEADER_SIZE = 6
    const val CRC_SIZE = 2

    /** Largest payload the decoder accepts; a bigger declared length means we are out of sync. */
    const val MAX_PAYLOAD = 64

    const val TYPE_NOTIFY = 0x01
    const val TYPE_READ = 0x02
    const val TYPE_WRITE = 0x03

    const val CMD_WEIGHT = 0x01
    const val CMD_TIMER = 0x02
    const val CMD_BATTERY = 0x05
    const val CMD_UNIT = 0x06
    const val CMD_MODE = 0x08
    const val CMD_TARE = 0x0D
    const val CMD_MODEL = 0x13

    private const val TIMER_START = 0x01
    private const val TIMER_PAUSE = 0x02
    private const val TIMER_RESET = 0x03
    private const val UNIT_GRAMS = 0x00
    private const val MODE_STANDARD = 0x01

    fun tare(): ByteArray = frame(TYPE_WRITE, CMD_TARE)

    fun timerStart(): ByteArray = frame(TYPE_WRITE, CMD_TIMER, TIMER_START)

    fun timerPause(): ByteArray = frame(TYPE_WRITE, CMD_TIMER, TIMER_PAUSE)

    fun timerReset(): ByteArray = frame(TYPE_WRITE, CMD_TIMER, TIMER_RESET)

    fun unitGrams(): ByteArray = frame(TYPE_WRITE, CMD_UNIT, UNIT_GRAMS)

    /** The scale's plain weighing mode; pour-over (02) and espresso (03) modes exist but the app does not use them. */
    fun standardMode(): ByteArray = frame(TYPE_WRITE, CMD_MODE, MODE_STANDARD, 0x00)

    fun readBattery(): ByteArray = frame(TYPE_READ, CMD_BATTERY)

    fun readModel(): ByteArray = frame(TYPE_READ, CMD_MODEL)

    /** CRC-16/MODBUS (init 0xFFFF, reflected polynomial 0xA001) over [length] bytes of [data] from [offset]. */
    fun crc16(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        var crc = 0xFFFF
        for (i in offset until offset + length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
        }
        return crc
    }

    /** Header + type + command + big-endian length + payload + big-endian CRC of everything before it. */
    private fun frame(type: Int, cmd: Int, vararg payload: Int): ByteArray {
        val body = HEADER_SIZE + payload.size
        val out = ByteArray(body + CRC_SIZE)
        out[0] = HEADER_1
        out[1] = HEADER_2
        out[2] = type.toByte()
        out[3] = cmd.toByte()
        out[4] = (payload.size ushr 8).toByte()
        out[5] = payload.size.toByte()
        payload.forEachIndexed { i, b -> out[HEADER_SIZE + i] = b.toByte() }
        val crc = crc16(out, 0, body)
        out[body] = (crc ushr 8).toByte()
        out[body + 1] = crc.toByte()
        return out
    }
}
