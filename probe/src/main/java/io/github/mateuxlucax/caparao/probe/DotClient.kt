package io.github.mateuxlucax.caparao.probe

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Throwaway client for the stage 0 spike: only the platform [BluetoothGatt] API, no BLE library.
 *
 * Why it is shaped this way: the Android GATT stack allows one operation in flight, so every step (MTU, bond,
 * discovery, CCCD, writes) goes through a tiny queue where each op finishes in its GATT callback, with a watchdog so a
 * lost callback cannot stall the session. All callbacks hop to the main thread, which makes the rest single-threaded.
 *
 * Privacy: the MAC address and device name are used to connect but never logged, shown or written to the session
 * file (the recordings become public test fixtures).
 */
@SuppressLint("MissingPermission") // the UI asks for BLUETOOTH_SCAN and BLUETOOTH_CONNECT before calling anything
class DotClient(private val context: Context, private val session: SessionLog) {
    // Shown on screen.
    var state by mutableStateOf("idle")
        private set
    var weight by mutableStateOf<Double?>(null)
        private set
    var flow by mutableStateOf<Double?>(null)
        private set
    var timer by mutableStateOf<Int?>(null)
        private set
    var battery by mutableStateOf<Int?>(null)
        private set
    var tail by mutableStateOf<List<String>>(emptyList())
        private set

    private val main = Handler(Looper.getMainLooper())
    private val adapter = context.getSystemService(BluetoothManager::class.java).adapter
    private val reader = Frames.Reader()

    private var device: BluetoothDevice? = null
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var attempts = 0
    private var linkUp = false
    private var wantDisconnect = false
    private var scanning = false

    // --- logging -------------------------------------------------------------------------------------------------

    /** Writes to the session file (if recording) and to the on-screen tail. Never pass identifiers in [note]. */
    private fun record(dir: String, hex: String, note: String) {
        session.write(dir, hex, note)
        tail = (tail + "$dir $hex $note".trim()).takeLast(TAIL_LINES)
    }

    private fun event(note: String) = record("event", "", note)

    /** Lets the UI mark session boundaries in the tail and the file. */
    fun mark(note: String) = event(note)

    // --- operation queue -----------------------------------------------------------------------------------------

    private class Op(val name: String, val timeoutMs: Long, val start: () -> Boolean)

    private val queue = ArrayDeque<Op>()
    private var current: Op? = null
    private val watchdog = Runnable {
        event("op_timeout ${current?.name}")
        current = null
        pump()
    }

    /** [start] returns true when the op began and a callback (or timer) will call [opDone]; false skips it. */
    private fun enqueue(name: String, timeoutMs: Long = 3_000, start: () -> Boolean) {
        queue.addLast(Op(name, timeoutMs, start))
        pump()
    }

    private fun enqueueDelay(ms: Long) = enqueue("delay") {
        main.postDelayed({ opDone("delay") }, ms)
        true
    }

    private fun pump() {
        if (current != null) return
        val op = queue.removeFirstOrNull() ?: return
        current = op
        val started = try {
            op.start()
        } catch (e: RuntimeException) {
            event("op_failed ${op.name} ${e.javaClass.simpleName}")
            false
        }
        if (started) {
            main.postDelayed(watchdog, op.timeoutMs)
        } else {
            current = null
            pump()
        }
    }

    /** Completes the in-flight op if it is [name]; late callbacks of an op that already timed out are ignored. */
    private fun opDone(name: String) {
        if (current?.name != name) return
        main.removeCallbacks(watchdog)
        current = null
        pump()
    }

    private fun clearQueue() {
        main.removeCallbacks(watchdog)
        queue.clear()
        current = null
    }

    // --- scan and connect ----------------------------------------------------------------------------------------

    fun connect() {
        if (adapter == null || !adapter.isEnabled) {
            state = "bluetooth off"
            event("bluetooth_off")
            return
        }
        if (state != "idle" && state != "disconnected" && state != "bluetooth off") return
        wantDisconnect = false
        attempts = 0
        state = "scanning"
        event("scan_start")
        scanning = true
        adapter.bluetoothLeScanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCb)
        main.postDelayed(scanTimeout, SCAN_TIMEOUT_MS)
    }

    private val scanTimeout = Runnable {
        if (scanning) {
            stopScan()
            state = "disconnected"
            event("scan_timeout")
        }
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        main.removeCallbacks(scanTimeout)
        adapter.bluetoothLeScanner?.stopScan(scanCb)
    }

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name ?: return
            if (name != "TIMEMORE_Dot" && !name.contains("TES017")) return
            stopScan()
            device = result.device
            event("scan_match")
            connectAttempt()
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            state = "disconnected"
            event("scan_failed code=$errorCode")
        }
    }

    private val retry = Runnable { connectAttempt() }

    @Suppress("DEPRECATION") // the transport overload is still the way to force LE on API 31+
    private fun connectAttempt() {
        val dev = device ?: return
        attempts++
        linkUp = false
        state = "connecting ($attempts/$MAX_ATTEMPTS)"
        event("connect_attempt $attempts")
        gatt = dev.connectGatt(context, false, gattCb, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        wantDisconnect = true
        main.removeCallbacks(retry)
        stopScan()
        clearQueue()
        val g = gatt
        if (g == null) {
            state = "disconnected"
            return
        }
        event("disconnect_requested")
        g.disconnect()
        // The disconnect callback closes the GATT; this covers the case where it never arrives.
        main.postDelayed({
            if (gatt === g) {
                g.close()
                gatt = null
                state = "disconnected"
            }
        }, 2_000)
    }

    /** Frees everything when the activity goes away. */
    fun release() {
        wantDisconnect = true
        main.removeCallbacksAndMessages(null)
        stopScan()
        runCatching { context.unregisterReceiver(bondReceiver) }
        gatt?.close()
        gatt = null
        session.stop()
    }

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val dev = bluetoothDevice(intent) ?: return
            if (dev.address != device?.address) return
            val bond = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
            when (bond) {
                BluetoothDevice.BOND_BONDING -> event("bond_state BONDING")
                BluetoothDevice.BOND_BONDED -> {
                    event("bond_state BONDED")
                    opDone("bond")
                }
                BluetoothDevice.BOND_NONE -> {
                    event("bond_state NONE (bonding failed)")
                    opDone("bond")
                }
            }
        }
    }

    init {
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        context.registerReceiver(bondReceiver, filter, Context.RECEIVER_EXPORTED)
    }

    @Suppress("DEPRECATION") // the typed overload only exists on API 33+
    private fun bluetoothDevice(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    // --- handshake (docs/hardware.md "Connection") ---------------------------------------------------------------

    private fun handshake() {
        enqueue("mtu") { gatt?.requestMtu(247) == true }
        enqueue("bond", timeoutMs = 60_000) {
            val dev = device ?: return@enqueue false
            if (dev.bondState == BluetoothDevice.BOND_BONDED) {
                event("already_bonded")
                false
            } else {
                state = "bonding (accept the pairing dialog)"
                event("bond_start")
                dev.createBond()
            }
        }
        enqueue("discover") { gatt?.discoverServices() == true }
        enqueue("notify") { enableNotifications() }
        enqueueDelay(500)
        sendFrame(Frames.build(0x03, 0x06, 0x00), "unit grams")
        enqueueDelay(200)
        sendFrame(Frames.build(0x03, 0x08, 0x01, 0x00), "standard mode")
        sendFrame(Frames.build(0x02, 0x05), "battery")
        enqueue("ready") {
            state = "streaming"
            event("handshake_done")
            false
        }
    }

    @Suppress("DEPRECATION") // the value-less overloads are the only ones below API 33
    private fun enableNotifications(): Boolean {
        val g = gatt ?: return false
        val service = g.getService(SERVICE)
        val notify = service?.getCharacteristic(NOTIFY)
        writeChar = service?.getCharacteristic(WRITE)
        if (notify == null || writeChar == null) {
            event("fff0_characteristics_missing")
            return false
        }
        g.setCharacteristicNotification(notify, true)
        val cccd = notify.getDescriptor(CCCD) ?: return false
        return if (Build.VERSION.SDK_INT >= 33) {
            g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
        } else {
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            g.writeDescriptor(cccd)
        }
    }

    // --- commands ------------------------------------------------------------------------------------------------

    fun tare() = command(Frames.build(0x03, 0x0D), "tare")
    fun timerStart() = command(Frames.build(0x03, 0x02, 0x01), "timer start")
    fun timerPause() = command(Frames.build(0x03, 0x02, 0x02), "timer pause")
    fun timerReset() = command(Frames.build(0x03, 0x02, 0x03), "timer reset")
    fun readBattery() = command(Frames.build(0x02, 0x05), "battery")
    fun readModel() = command(Frames.build(0x02, 0x13), "model")

    private fun command(frame: ByteArray, note: String) {
        if (gatt == null || writeChar == null) {
            event("not_ready $note")
            return
        }
        sendFrame(frame, note)
    }

    @Suppress("DEPRECATION") // the value-taking overload only exists on API 33+
    private fun sendFrame(frame: ByteArray, note: String) = enqueue("tx") {
        val g = gatt ?: return@enqueue false
        val c = writeChar ?: return@enqueue false
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val ok = if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(c, frame, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothStatusCodes.SUCCESS
        } else {
            c.value = frame
            g.writeCharacteristic(c)
        }
        if (ok) record("tx", Frames.hex(frame), note) else event("write_rejected $note")
        ok
    }

    // --- GATT callbacks (binder threads: hop to main immediately) --------------------------------------------------

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                if (g !== gatt) {
                    g.close()
                    return@post
                }
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    linkUp = true
                    state = "connected"
                    event("connected")
                    handshake()
                } else {
                    event("disconnected status=$status")
                    g.close()
                    gatt = null
                    clearQueue()
                    writeChar = null
                    if (!linkUp && !wantDisconnect && attempts < MAX_ATTEMPTS) {
                        state = "retrying"
                        main.postDelayed(retry, RETRY_DELAY_MS)
                    } else {
                        state = "disconnected"
                    }
                    linkUp = false
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            main.post {
                event("mtu=$mtu status=$status")
                opDone("mtu")
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            main.post {
                event("services status=$status fff0=${g.getService(SERVICE) != null}")
                opDone("discover")
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            main.post {
                event("cccd status=$status")
                opDone("notify")
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            main.post {
                if (status != BluetoothGatt.GATT_SUCCESS) event("write_status=$status")
                opDone("tx")
            }
        }

        // API 33+ calls this one; below that, the deprecated overload that follows.
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            main.post { onNotification(value) }
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= 33) return
            val copy = c.value.copyOf()
            main.post { onNotification(copy) }
        }
    }

    // --- decoding (display only; the JSONL keeps the raw bytes) ----------------------------------------------------

    private fun onNotification(value: ByteArray) {
        val notes = reader.feed(value).joinToString("; ") { decode(it) }
        record("rx", Frames.hex(value), notes)
    }

    private fun decode(f: Frames.Incoming): String {
        val p = ByteBuffer.wrap(f.payload) // big-endian by default
        return when {
            f.type == 0x01 && f.cmd == 0x01 && f.payload.size >= 8 -> {
                weight = p.getInt(0) / 10.0
                flow = p.getShort(4) / 10.0
                timer = p.getShort(6).toInt() and 0xFFFF
                "weight=$weight flow=$flow timer=$timer"
            }
            f.type == 0x02 && f.cmd == 0x05 && f.payload.size >= 2 -> {
                battery = f.payload[1].toInt() and 0xFF
                "battery bars=${f.payload[0]} percent=$battery"
            }
            f.type == 0x02 && f.cmd == 0x13 -> "model=" + String(f.payload, Charsets.US_ASCII).filter { it in ' '..'~' }
            else -> "type=%02X cmd=%02X".format(f.type, f.cmd)
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 500L
        const val SCAN_TIMEOUT_MS = 30_000L
        const val TAIL_LINES = 30
        val SERVICE: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val NOTIFY: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        val WRITE: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
