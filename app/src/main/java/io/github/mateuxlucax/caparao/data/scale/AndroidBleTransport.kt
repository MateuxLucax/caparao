package io.github.mateuxlucax.caparao.data.scale

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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
import android.content.pm.PackageManager
import android.os.Build
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [BleTransport] on the platform [BluetoothGatt] API, without a library (docs/adr/0002-native-ble.md). The flow mirrors
 * the stage 0 probe that ran against a real Dot (docs/hardware.md "Connection").
 *
 * Why it is shaped this way: Android allows one GATT operation in flight and reports each result on a binder thread.
 * Every step therefore sets a single [pending] slot, starts the operation, and suspends until its callback completes
 * the slot or a timeout fires, so a lost callback cannot stall the session. [opMutex] keeps [open] and [write] from
 * interleaving. Callbacks only touch volatile or atomic state, so no handler thread is needed.
 *
 * Privacy: the device name and address are used to match and connect, never logged or put in an exception message;
 * the only place the address leaves is [scan]'s return value.
 */
@SuppressLint("MissingPermission") // callers check readiness() before scan, open or write
class AndroidBleTransport(context: Context) : BleTransport {
    private val context = context.applicationContext
    private val adapter: BluetoothAdapter? = this.context.getSystemService(BluetoothManager::class.java)?.adapter

    // Notifications arrive at 10 Hz and must not be dropped: the buffer is large and never overwrites. With no
    // collector yet, tryEmit just discards, which is right (nobody asked for this connection's events).
    private val sharedEvents = MutableSharedFlow<BleEvent>(replay = 0, extraBufferCapacity = EVENT_BUFFER)
    override val events: SharedFlow<BleEvent> = sharedEvents

    private enum class Kind { CONNECT, MTU, BOND, DISCOVER, CCCD, WRITE }

    private class Step(val kind: Kind) {
        val done = CompletableDeferred<Int>()
    }

    private val opMutex = Mutex()
    private val pending = AtomicReference<Step?>(null)
    private val gatt = AtomicReference<BluetoothGatt?>(null)
    @Volatile private var writeChar: BluetoothGattCharacteristic? = null
    // True once open() succeeded and until the link drops or close() runs: only then does a drop mean LinkLost.
    private val opened = AtomicBoolean(false)
    // HCI status of the last disconnect of the current attempt, or [CLOSED]; null while the link is up.
    @Volatile private var linkDown: Int? = null
    @Volatile private var bondReceiver: BroadcastReceiver? = null

    override fun readiness(): BleReadiness = when {
        !granted(Manifest.permission.BLUETOOTH_SCAN) || !granted(Manifest.permission.BLUETOOTH_CONNECT) ->
            BleReadiness.PERMISSION_MISSING
        adapter == null || !adapter.isEnabled -> BleReadiness.BLUETOOTH_OFF
        else -> BleReadiness.READY
    }

    private fun granted(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    // --- scan ----------------------------------------------------------------------------------------------------

    override suspend fun scan(timeoutMs: Long): String? {
        val scanner = adapter?.bluetoothLeScanner ?: throw BleException(BleException.Reason.FAILED, "no BLE scanner")
        // A bonded Dot still advertises, so scan results are enough; no name filter because some stacks only match
        // the name in the scan response, and the app-side check is cheap.
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        val name = result.scanRecord?.deviceName ?: result.device.name ?: return
                        if (!isDot(name) || !cont.isActive) return
                        scanner.stopScan(this)
                        cont.resume(result.device.address)
                    }

                    override fun onScanFailed(errorCode: Int) {
                        if (cont.isActive) {
                            cont.resumeWithException(BleException(BleException.Reason.FAILED, "scan failed ($errorCode)"))
                        }
                    }
                }
                // Covers the timeout and cancellation paths; stopping an already stopped scan is harmless.
                cont.invokeOnCancellation { scanner.stopScan(callback) }
                scanner.startScan(null, settings, callback)
            }
        }
    }

    // Never match a bare "dot": other devices (and bonded phones' accessories) use it.
    private fun isDot(name: String) = name == DOT_NAME || name.contains(DOT_MODEL)

    // --- open ----------------------------------------------------------------------------------------------------

    override suspend fun open(address: String) = opMutex.withLock {
        close()
        linkDown = null
        var ok = false
        try {
            val device = try {
                adapter?.getRemoteDevice(address)
            } catch (e: IllegalArgumentException) {
                null
            } ?: throw BleException(BleException.Reason.FAILED, "invalid device")
            connect(device)
            // Handshake order is the one verified on a Dot: MTU, bond, discovery, notifications.
            expectSuccess(step(Kind.MTU, "mtu") { gatt.get()?.requestMtu(MTU) == true }, "mtu")
            bond(device)
            expectSuccess(step(Kind.DISCOVER, "discovery") { gatt.get()?.discoverServices() == true }, "discovery")
            enableNotifications()
            opened.set(true)
            if (linkDown != null) {
                opened.set(false)
                throw linkDownError()
            }
            ok = true
        } finally {
            if (!ok) close()
        }
    }

    /** Up to [CONNECT_ATTEMPTS] tries: the first connection after a long idle often fails with status 133. */
    private suspend fun connect(device: BluetoothDevice) {
        var lastStatus: Int? = null
        repeat(CONNECT_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(RETRY_DELAY_MS)
            linkDown = null
            val result = try {
                step(Kind.CONNECT, "connect") {
                    @Suppress("DEPRECATION") // the transport overload is still how to force LE on API 31+
                    gatt.set(device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE))
                    true
                }
            } catch (e: BleException) {
                LINK_DOWN // a timeout is retried like a drop
            }
            if (result == BluetoothGatt.GATT_SUCCESS) return
            lastStatus = linkDown
            gatt.getAndSet(null)?.close()
        }
        // A Dot bonded to another phone drops us with 0x3E before anything else happens.
        if (lastStatus == HCI_CONNECTION_FAILED) {
            throw BleException(BleException.Reason.PAIRING_REFUSED, "the scale refused the connection")
        }
        throw BleException(BleException.Reason.FAILED, "could not connect")
    }

    /** Bonding is required: the Dot streams only on an encrypted link. */
    private suspend fun bond(device: BluetoothDevice) {
        if (device.bondState == BluetoothDevice.BOND_BONDED) return
        val code = try {
            step(Kind.BOND, "bonding", BOND_TIMEOUT_MS) {
                registerBondReceiver()
                // Re-check after registering so a bond that finished in between is not missed.
                when (device.bondState) {
                    BluetoothDevice.BOND_BONDED -> {
                        complete(Kind.BOND, BluetoothGatt.GATT_SUCCESS)
                        true
                    }
                    BluetoothDevice.BOND_BONDING -> true
                    else -> device.createBond()
                }
            }
        } finally {
            unregisterBondReceiver()
        }
        if (code == BOND_REFUSED) {
            throw BleException(BleException.Reason.PAIRING_REFUSED, "pairing was refused")
        }
        expectSuccess(code, "bonding")
    }

    private suspend fun enableNotifications() {
        val service = gatt.get()?.getService(SERVICE)
        val notify = service?.getCharacteristic(NOTIFY)
        val write = service?.getCharacteristic(WRITE)
        val cccd = notify?.getDescriptor(CCCD)
        if (notify == null || write == null || cccd == null) {
            throw BleException(BleException.Reason.FAILED, "scale service not found")
        }
        writeChar = write
        expectSuccess(
            step(Kind.CCCD, "notifications") {
                val g = gatt.get() ?: return@step false
                if (!g.setCharacteristicNotification(notify, true)) return@step false
                @Suppress("DEPRECATION") // the value-less overloads are the only ones below API 33
                if (Build.VERSION.SDK_INT >= 33) {
                    g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                        BluetoothStatusCodes.SUCCESS
                } else {
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    g.writeDescriptor(cccd)
                }
            },
            "notifications",
        )
    }

    // --- write ---------------------------------------------------------------------------------------------------

    override suspend fun write(frame: ByteArray) = opMutex.withLock {
        val g = gatt.get()
        val c = writeChar
        if (g == null || c == null || !opened.get()) throw BleException(BleException.Reason.FAILED, "not connected")
        expectSuccess(
            step(Kind.WRITE, "write") {
                @Suppress("DEPRECATION") // the value-taking overload only exists on API 33+
                if (Build.VERSION.SDK_INT >= 33) {
                    g.writeCharacteristic(c, frame, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) ==
                        BluetoothStatusCodes.SUCCESS
                } else {
                    c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    c.value = frame
                    g.writeCharacteristic(c)
                }
            },
            "write",
        )
    }

    // --- close ---------------------------------------------------------------------------------------------------

    override fun close() {
        // Clear the flag first: a deliberate close is not a LinkLost.
        opened.set(false)
        linkDown = CLOSED
        writeChar = null
        pending.get()?.done?.complete(LINK_DOWN)
        unregisterBondReceiver()
        gatt.getAndSet(null)?.let {
            it.disconnect()
            it.close()
        }
    }

    // --- step plumbing -------------------------------------------------------------------------------------------

    /**
     * Runs one GATT operation: [start] returns true when the stack accepted it, then this suspends until the matching
     * callback (or a disconnect) completes the slot. Returns the GATT status, or [LINK_DOWN] / [BOND_REFUSED].
     */
    private suspend fun step(kind: Kind, label: String, timeoutMs: Long = STEP_TIMEOUT_MS, start: () -> Boolean): Int {
        val step = Step(kind)
        pending.set(step)
        try {
            // A disconnect that landed before the slot existed would otherwise be waited out.
            if (linkDown != null) return LINK_DOWN
            if (!start()) throw BleException(BleException.Reason.FAILED, "$label was rejected")
            return withTimeoutOrNull(timeoutMs) { step.done.await() }
                ?: throw BleException(BleException.Reason.FAILED, "$label timed out")
        } finally {
            pending.compareAndSet(step, null)
        }
    }

    private fun complete(kind: Kind, code: Int) {
        val step = pending.get()
        if (step != null && step.kind == kind) step.done.complete(code)
    }

    private fun expectSuccess(code: Int, label: String) {
        when (code) {
            BluetoothGatt.GATT_SUCCESS -> Unit
            LINK_DOWN -> throw linkDownError()
            else -> throw BleException(BleException.Reason.FAILED, "$label failed (status $code)")
        }
    }

    private fun linkDownError(): BleException {
        val status = linkDown
        return if (status == HCI_CONNECTION_FAILED || status == HCI_REMOTE_TERMINATED) {
            BleException(BleException.Reason.PAIRING_REFUSED, "the scale dropped the link right after connecting")
        } else {
            BleException(BleException.Reason.FAILED, "the link dropped")
        }
    }

    // --- bond broadcasts -----------------------------------------------------------------------------------------

    private fun registerBondReceiver() {
        unregisterBondReceiver()
        var bonding = false
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val device = bluetoothDevice(intent)
                if (device == null || device.address != gatt.get()?.device?.address) return
                when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                    BluetoothDevice.BOND_BONDING -> bonding = true
                    BluetoothDevice.BOND_BONDED -> complete(Kind.BOND, BluetoothGatt.GATT_SUCCESS)
                    // NONE after BONDING means the user or the scale said no; NONE alone is a stale state.
                    BluetoothDevice.BOND_NONE -> if (bonding) complete(Kind.BOND, BOND_REFUSED)
                }
            }
        }
        bondReceiver = receiver
        context.registerReceiver(
            receiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            // The stack sends it; the probe received it this way on a real phone. Android 12 ignores the flag (its
            // receivers are exported anyway), so the inlined API 33 constant is harmless there.
            @SuppressLint("InlinedApi") Context.RECEIVER_EXPORTED,
        )
    }

    private fun unregisterBondReceiver() {
        val receiver = bondReceiver ?: return
        bondReceiver = null
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // already unregistered by a concurrent close
        }
    }

    @Suppress("DEPRECATION") // the typed overload only exists on API 33+
    private fun bluetoothDevice(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    // --- GATT callbacks (binder threads) -------------------------------------------------------------------------

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                complete(Kind.CONNECT, BluetoothGatt.GATT_SUCCESS)
                return
            }
            linkDown = status
            pending.get()?.done?.complete(LINK_DOWN)
            // After open() the drop is news for the client; during open() the failing step reports it instead.
            if (opened.compareAndSet(true, false)) {
                gatt.getAndSet(null)?.close()
                sharedEvents.tryEmit(BleEvent.LinkLost)
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) = complete(Kind.MTU, status)

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = complete(Kind.DISCOVER, status)

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) =
            complete(Kind.CCCD, status)

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) =
            complete(Kind.WRITE, status)

        // API 33+ calls this overload; below that, the deprecated one that follows.
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            sharedEvents.tryEmit(BleEvent.Notification(value.copyOf()))
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= 33) return
            c.value?.let { sharedEvents.tryEmit(BleEvent.Notification(it.copyOf())) }
        }
    }

    private companion object {
        const val DOT_NAME = "TIMEMORE_Dot"
        const val DOT_MODEL = "TES017"
        const val MTU = 247
        const val CONNECT_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 500L
        const val STEP_TIMEOUT_MS = 10_000L
        const val BOND_TIMEOUT_MS = 60_000L
        const val EVENT_BUFFER = 1024

        // Step results that are not GATT statuses (those are small non-negative numbers).
        const val LINK_DOWN = -1
        const val BOND_REFUSED = -2

        // Value of linkDown after a deliberate close().
        const val CLOSED = -3

        // HCI reasons the Dot uses when it is bonded to another phone (docs/hardware.md "Device").
        const val HCI_CONNECTION_FAILED = 0x3E
        const val HCI_REMOTE_TERMINATED = 0x13

        val SERVICE: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val NOTIFY: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        val WRITE: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
