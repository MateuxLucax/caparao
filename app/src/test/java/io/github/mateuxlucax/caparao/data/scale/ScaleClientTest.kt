package io.github.mateuxlucax.caparao.data.scale

import io.github.mateuxlucax.caparao.model.ScaleBattery
import io.github.mateuxlucax.caparao.model.ScaleError
import io.github.mateuxlucax.caparao.model.ScaleReading
import io.github.mateuxlucax.caparao.model.ScaleState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScaleClientTest {
    /** Records every call; scan and open answers are scripted per call, the last one repeating. */
    private class FakeTransport : BleTransport {
        var readiness = BleReadiness.READY
        var scans = mutableListOf<String?>("dot")
        var opens = mutableListOf<BleException?>(null)
        val calls = mutableListOf<String>()
        val scanTimes = mutableListOf<Long>()
        val writes = mutableListOf<Pair<Long, List<Byte>>>()
        lateinit var now: () -> Long
        override val events = MutableSharedFlow<BleEvent>(extraBufferCapacity = 64)

        override fun readiness() = readiness

        override suspend fun scan(timeoutMs: Long): String? {
            calls += "scan"
            scanTimes += now()
            return if (scans.size > 1) scans.removeAt(0) else scans.first()
        }

        override suspend fun open(address: String) {
            calls += "open"
            (if (opens.size > 1) opens.removeAt(0) else opens.first())?.let { throw it }
        }

        override suspend fun write(frame: ByteArray) {
            writes += now() to frame.toList()
        }

        override fun close() {
            calls += "close"
        }

        fun frames() = writes.map { it.second }
    }

    private fun bytes(hex: String) = hex.split(' ').map { it.toInt(16).toByte() }.toByteArray()

    private val weight47 = bytes("a5 5a 01 01 00 09 00 00 01 da 00 00 00 00 00 00 00")
    private val battery100 = bytes("a5 5a 02 05 00 02 03 64 00 00")
    private val refused = BleException(BleException.Reason.PAIRING_REFUSED, "refused")
    private val failed = BleException(BleException.Reason.FAILED, "gatt 133")

    private fun TestScope.client(transport: FakeTransport): ScaleClient {
        transport.now = { currentTime }
        return ScaleClient(transport, backgroundScope) { currentTime }
    }

    @Test
    fun connectsWithTheHandshakeInOrder() = runTest {
        val transport = FakeTransport()
        val client = client(transport)
        client.start()
        runCurrent()
        assertEquals(ScaleState.Connecting, client.state.value)
        advanceTimeBy(701)
        assertEquals(listOf("scan", "open"), transport.calls)
        assertEquals(
            listOf(DotProtocol.unitGrams(), DotProtocol.standardMode(), DotProtocol.readBattery()).map { it.toList() },
            transport.frames(),
        )
        assertEquals(listOf(500L, 700L, 700L), transport.writes.map { it.first })
        assertEquals(ScaleState.Connected(battery = null), client.state.value)
    }

    @Test
    fun readingsAndBatteryComeFromNotifications() = runTest {
        val transport = FakeTransport()
        val client = client(transport)
        client.start()
        advanceTimeBy(701)
        transport.events.emit(BleEvent.Notification(weight47 + battery100))
        runCurrent()
        assertEquals(ScaleReading(47.4, 0.0, 0, overload = false), client.reading.value)
        assertEquals(ScaleState.Connected(ScaleBattery(percent = 100, bars = 3)), client.state.value)
    }

    @Test
    fun asksForTheBatteryEveryMinute() = runTest {
        val transport = FakeTransport()
        val client = client(transport)
        client.start()
        advanceTimeBy(701 + 120_000)
        val batteryReads = transport.writes.filter { it.second == DotProtocol.readBattery().toList() }.map { it.first }
        assertEquals(listOf(700L, 60_700L, 120_700L), batteryReads)
    }

    @Test
    fun bluetoothOffOrNoPermissionStopsWithoutRetrying() = runTest {
        for ((readiness, error) in listOf(
            BleReadiness.BLUETOOTH_OFF to ScaleError.BLUETOOTH_OFF,
            BleReadiness.PERMISSION_MISSING to ScaleError.PERMISSION_MISSING,
        )) {
            val transport = FakeTransport().apply { this.readiness = readiness }
            val client = client(transport)
            client.start()
            advanceTimeBy(60_000)
            assertEquals(ScaleState.Failed(error, retrying = false), client.state.value)
            assertEquals(emptyList<String>(), transport.calls)
        }
    }

    @Test
    fun notFoundRetriesWithGrowingBackoff() = runTest {
        val transport = FakeTransport().apply { scans = mutableListOf(null) }
        val client = client(transport)
        client.start()
        advanceTimeBy(35_001)
        assertEquals(ScaleState.Failed(ScaleError.NOT_FOUND, retrying = true), client.state.value)
        // The wait doubles from 1 s and stays at 10 s.
        assertEquals(listOf(0L, 1_000L, 3_000L, 7_000L, 15_000L, 25_000L, 35_000L), transport.scanTimes)
    }

    @Test
    fun openIsRetriedThreeTimesBeforeGivingUp() = runTest {
        val transport = FakeTransport().apply { opens = mutableListOf(failed, failed, null) }
        val client = client(transport)
        client.start()
        advanceTimeBy(1_000 + 701)
        assertEquals(listOf("scan", "open", "close", "open", "close", "open"), transport.calls)
        assertTrue(client.state.value is ScaleState.Connected)

        val failing = FakeTransport().apply { opens = mutableListOf(failed) }
        val other = client(failing)
        other.start()
        advanceTimeBy(1_001)
        assertEquals(3, failing.calls.count { it == "open" })
        assertEquals(ScaleState.Failed(ScaleError.NOT_FOUND, retrying = true), other.state.value)
    }

    @Test
    fun refusedPairingMeansPairedElsewhereWithoutExtraAttempts() = runTest {
        val transport = FakeTransport().apply { opens = mutableListOf(refused) }
        val client = client(transport)
        client.start()
        runCurrent()
        assertEquals(ScaleState.Failed(ScaleError.PAIRED_ELSEWHERE, retrying = true), client.state.value)
        assertEquals(1, transport.calls.count { it == "open" })
    }

    @Test
    fun lostLinkClearsTheReadingAndReconnectsAfterASecond() = runTest {
        val transport = FakeTransport()
        val client = client(transport)
        client.start()
        advanceTimeBy(701)
        transport.events.emit(BleEvent.Notification(weight47))
        transport.events.emit(BleEvent.LinkLost)
        runCurrent()
        assertEquals(ScaleState.Failed(ScaleError.CONNECTION_LOST, retrying = true), client.state.value)
        assertNull(client.reading.value)
        advanceTimeBy(1_000 + 701)
        assertTrue(client.state.value is ScaleState.Connected)
        assertEquals(2, transport.calls.count { it == "scan" })
    }

    @Test
    fun aFrameSplitAcrossTheReconnectIsNotGluedToTheNextSession() = runTest {
        val transport = FakeTransport()
        val client = client(transport)
        client.start()
        advanceTimeBy(701)
        transport.events.emit(BleEvent.Notification(weight47.copyOfRange(0, 8)))
        transport.events.emit(BleEvent.LinkLost)
        advanceTimeBy(1_000 + 701)
        transport.events.emit(BleEvent.Notification(weight47))
        runCurrent()
        assertEquals(47.4, client.reading.value!!.weightGrams, 0.0)
    }

    @Test
    fun commandsGoOutOnlyWhileConnected() = runTest {
        val transport = FakeTransport().apply { scans = mutableListOf(null) }
        val client = client(transport)
        client.start()
        runCurrent()
        client.tare()
        runCurrent()
        assertEquals(emptyList<List<Byte>>(), transport.frames())

        val connected = FakeTransport()
        val other = client(connected)
        other.start()
        advanceTimeBy(701)
        other.tare()
        other.timer(TimerAction.START)
        other.timer(TimerAction.RESET)
        runCurrent()
        assertEquals(
            listOf(DotProtocol.tare(), DotProtocol.timerStart(), DotProtocol.timerReset()).map { it.toList() },
            connected.frames().takeLast(3),
        )
    }

    @Test
    fun stopClosesAndStaysIdle() = runTest {
        val transport = FakeTransport()
        val client = client(transport)
        client.start()
        advanceTimeBy(701)
        client.stop()
        advanceTimeBy(60_000) // the battery poll never ends, so wait a minute of virtual time
        assertEquals(ScaleState.Idle, client.state.value)
        assertEquals("close", transport.calls.last())
        assertEquals(1, transport.calls.count { it == "scan" })
    }

    @Test
    fun startTwiceKeepsOneConnection() = runTest {
        val transport = FakeTransport()
        val client = client(transport)
        client.start()
        client.start()
        advanceTimeBy(701)
        assertEquals(1, transport.calls.count { it == "scan" })
    }

    @Test
    fun theDebugLogNeverHoldsTheAddress() = runTest {
        val transport = FakeTransport().apply { scans = mutableListOf("AA:BB:CC:DD:EE:FF") }
        val client = client(transport)
        client.start()
        advanceTimeBy(701)
        assertTrue(client.log.value.isNotEmpty())
        assertTrue(client.log.value.none { "AA:BB" in it })
    }
}
