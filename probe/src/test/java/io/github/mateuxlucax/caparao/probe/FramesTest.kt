package io.github.mateuxlucax.caparao.probe

import org.junit.Assert.assertEquals
import org.junit.Test

class FramesTest {
    @Test
    fun `tare frame matches the documented bytes`() {
        assertEquals("A5 5A 03 0D 00 00 64 D1", Frames.hex(Frames.build(0x03, 0x0D)))
    }

    @Test
    fun `reader reassembles split and concatenated frames`() {
        // weight 1234 (123.4 g), flow 5, timer 7, no overload
        val weight = byteArrayOf(
            0xA5.toByte(), 0x5A, 1, 1, 0, 9, 0, 0, 0x04, 0xD2.toByte(), 0, 5, 0, 7, 0, 0, 0,
        )
        val reader = Frames.Reader()
        assertEquals(0, reader.feed(weight.copyOfRange(0, 5)).size)
        val frames = reader.feed(weight.copyOfRange(5, weight.size) + weight)
        assertEquals(2, frames.size)
        assertEquals(0x04D2, ((frames[0].payload[2].toInt() and 0xFF) shl 8) or (frames[0].payload[3].toInt() and 0xFF))
    }
}
