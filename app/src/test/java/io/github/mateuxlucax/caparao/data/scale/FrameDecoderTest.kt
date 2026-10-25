package io.github.mateuxlucax.caparao.data.scale

import io.github.mateuxlucax.caparao.model.ScaleBattery
import io.github.mateuxlucax.caparao.model.ScaleReading
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameDecoderTest {
    /** Observed on a real Dot: 47.4 g, no flow, timer 0. The scale sends CRC 00 00. */
    private val observed = "a5 5a 01 01 00 09 00 00 01 da 00 00 00 00 00 00 00"

    private fun weight(g: Double, flow: Double = 0.0, timer: Int = 0, overload: Boolean = false) =
        ScaleMessage.Weight(ScaleReading(g, flow, timer, overload))

    @Test
    fun decodesTheObservedFrameIgnoringItsCrc() {
        assertEquals(listOf(weight(47.4)), FrameDecoder().feed(bytes(observed)))
    }

    @Test
    fun decodesFlowTimerAndOverload() {
        // 12.3 g, -1.5 g/s, 75 s, overload set
        val frame = "a5 5a 01 01 00 09 00 00 00 7b ff f1 00 4b 01 00 00"
        assertEquals(listOf(weight(12.3, -1.5, 75, true)), FrameDecoder().feed(bytes(frame)))
    }

    @Test
    fun acceptsAnEightBytePayloadWithoutOverloadFlag() {
        val frame = "a5 5a 01 01 00 08 00 00 01 da 00 05 00 02 00 00"
        assertEquals(listOf(weight(47.4, 0.5, 2)), FrameDecoder().feed(bytes(frame)))
    }

    @Test
    fun decodesNegativeWeight() {
        // -0.3 g
        val frame = "a5 5a 01 01 00 09 ff ff ff fd 00 00 00 00 00 00 00"
        assertEquals(listOf(weight(-0.3)), FrameDecoder().feed(bytes(frame)))
    }

    @Test
    fun decodesSeveralFramesInOneChunk() {
        val two = bytes(observed) + bytes(observed.replace("01 da", "01 db"))
        assertEquals(listOf(weight(47.4), weight(47.5)), FrameDecoder().feed(two))
    }

    @Test
    fun joinsAFrameSplitAcrossTwoChunks() {
        val all = bytes(observed)
        val decoder = FrameDecoder()
        assertEquals(emptyList<ScaleMessage>(), decoder.feed(all.copyOf(7)))
        assertEquals(listOf(weight(47.4)), decoder.feed(all.copyOfRange(7, all.size)))
    }

    @Test
    fun joinsFramesFedByteByByte() {
        val decoder = FrameDecoder()
        val out = bytes("$observed $observed").flatMap { decoder.feed(byteArrayOf(it)) }
        assertEquals(listOf(weight(47.4), weight(47.4)), out)
    }

    @Test
    fun keepsAHeaderSplitBetweenItsTwoBytes() {
        val all = bytes(observed)
        val decoder = FrameDecoder()
        assertEquals(emptyList<ScaleMessage>(), decoder.feed(all.copyOf(1)))
        assertEquals(listOf(weight(47.4)), decoder.feed(all.copyOfRange(1, all.size)))
    }

    @Test
    fun skipsGarbageBeforeTheHeader() {
        val chunk = bytes("00 a5 12 34 5a a5") + bytes(observed)
        assertEquals(listOf(weight(47.4)), FrameDecoder().feed(chunk))
    }

    @Test
    fun resyncsAfterAnOversizeLength() {
        // A fake header declaring 0x0101 bytes, followed by a real frame.
        val chunk = bytes("a5 5a 01 01 01 01") + bytes(observed)
        assertEquals(listOf(weight(47.4)), FrameDecoder().feed(chunk))
    }

    @Test
    fun acceptsTheLargestAllowedPayloadAndRejectsOneMore() {
        val ok = bytes("a5 5a 01 7f 00 40") + ByteArray(64) + bytes("00 00")
        val unknown = FrameDecoder().feed(ok).single() as ScaleMessage.Unknown
        assertEquals(64, unknown.payload.size)
        val tooBig = bytes("a5 5a 01 7f 00 41") + ByteArray(65) + bytes("00 00")
        assertEquals(emptyList<ScaleMessage>(), FrameDecoder().feed(tooBig))
    }

    @Test
    fun decodesABatteryFrameAfterAWeightFrame() {
        val chunk = bytes(observed) + bytes("a5 5a 02 05 00 02 03 55 00 00")
        assertEquals(
            listOf(weight(47.4), ScaleMessage.Battery(ScaleBattery(percent = 0x55, bars = 3))),
            FrameDecoder().feed(chunk),
        )
    }

    @Test
    fun decodesTheBatteryReplyAndTheUnsolicitedBatteryPush() {
        // Both recorded from a real Dot (spike, 2026-10-25): the reply to `02 05`, and the `01 05` it pushes every ~30 s.
        val expected = ScaleMessage.Battery(ScaleBattery(percent = 100, bars = 3))
        assertEquals(listOf(expected), FrameDecoder().feed(bytes("a5 5a 02 05 00 02 03 64 00 00")))
        assertEquals(listOf(expected), FrameDecoder().feed(bytes("a5 5a 01 05 00 02 03 64 00 00")))
    }

    @Test
    fun decodesAnAck() {
        assertEquals(listOf(ScaleMessage.Ack(0x0D)), FrameDecoder().feed(bytes("a5 5a 03 0d 00 01 01 00 00")))
    }

    @Test
    fun decodesTheModel() {
        val chunk = bytes("a5 5a 02 13 00 06") + "TES017".toByteArray() + bytes("00 00")
        assertEquals(listOf(ScaleMessage.Model("TES017")), FrameDecoder().feed(chunk))
    }

    @Test
    fun reportsUnknownFramesAndTooShortPayloads() {
        val chunk = bytes("a5 5a 01 77 00 02 aa bb 00 00") + bytes("a5 5a 01 01 00 03 00 00 01 00 00")
        assertEquals(
            listOf(
                ScaleMessage.Unknown(0x01, 0x77, bytes("aa bb")),
                ScaleMessage.Unknown(0x01, 0x01, bytes("00 00 01")),
            ),
            FrameDecoder().feed(chunk),
        )
    }

    @Test
    fun staysBoundedUnderEndlessGarbageAndStillRecovers() {
        val decoder = FrameDecoder()
        repeat(100) { assertEquals(emptyList<ScaleMessage>(), decoder.feed(ByteArray(300) { 0x11 })) }
        assertEquals(listOf(weight(47.4)), decoder.feed(bytes(observed)))
    }

    @Test
    fun resetDropsAPartialFrame() {
        val decoder = FrameDecoder()
        decoder.feed(bytes(observed).copyOf(8))
        decoder.reset()
        assertEquals(listOf(weight(47.4)), decoder.feed(bytes(observed)))
    }
}
