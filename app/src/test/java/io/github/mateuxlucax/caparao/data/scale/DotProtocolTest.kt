package io.github.mateuxlucax.caparao.data.scale

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DotProtocolTest {
    @Test
    fun tareMatchesTheReferenceFrame() = assertArrayEquals(bytes("A5 5A 03 0D 00 00 64 D1"), DotProtocol.tare())

    @Test
    fun timerFramesMatchTheReferenceFrames() {
        assertArrayEquals(bytes("a5 5a 03 02 00 01 01 18 67"), DotProtocol.timerStart())
        assertArrayEquals(bytes("a5 5a 03 02 00 01 03 d9 e6"), DotProtocol.timerReset())
        assertEquals(0x02, DotProtocol.timerPause()[6].toInt())
    }

    @Test
    fun setupAndQueryFramesHaveTheDocumentedBodies() {
        assertBody("a5 5a 03 06 00 01 00", DotProtocol.unitGrams())
        assertBody("a5 5a 03 08 00 02 01 00", DotProtocol.standardMode())
        assertBody("a5 5a 02 05 00 00", DotProtocol.readBattery())
        assertBody("a5 5a 02 13 00 00", DotProtocol.readModel())
    }

    @Test
    fun everyEncodedFrameEndsWithItsOwnCrc() {
        listOf(
            DotProtocol.tare(), DotProtocol.timerStart(), DotProtocol.timerPause(), DotProtocol.timerReset(),
            DotProtocol.unitGrams(), DotProtocol.standardMode(), DotProtocol.readBattery(), DotProtocol.readModel(),
        ).forEach { f ->
            val crc = ((f[f.size - 2].toInt() and 0xFF) shl 8) or (f[f.size - 1].toInt() and 0xFF)
            assertEquals(DotProtocol.crc16(f, 0, f.size - 2), crc)
        }
    }

    @Test
    fun crcKnownVectors() {
        assertEquals(0x4B37, DotProtocol.crc16("123456789".toByteArray()))
        assertEquals(0xFFFF, DotProtocol.crc16(ByteArray(0)))
        assertEquals(0x64D1, DotProtocol.crc16(bytes("A5 5A 03 0D 00 00")))
    }

    @Test
    fun crcOverASubrangeIgnoresTheRest() =
        assertEquals(0x64D1, DotProtocol.crc16(bytes("A5 5A 03 0D 00 00 64 D1"), 0, 6))

    /** Frame without its two CRC bytes. */
    private fun assertBody(expected: String, actual: ByteArray) =
        assertArrayEquals(bytes(expected), actual.copyOf(actual.size - 2))
}

internal fun bytes(hex: String): ByteArray =
    hex.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()
