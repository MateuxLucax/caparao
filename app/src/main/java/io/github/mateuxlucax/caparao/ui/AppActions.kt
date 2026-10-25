package io.github.mateuxlucax.caparao.ui

/** Things only the activity can do, passed to the screens. They never go into a ViewModel. */
class AppActions(
    /** Shows the system dialog for the nearby devices permission; returns whether it was granted. */
    val requestBluetoothPermissions: suspend () -> Boolean,
    /** Whether the nearby devices (Bluetooth) permission is granted right now. */
    val bluetoothPermissionGranted: () -> Boolean,
    val enableBluetooth: () -> Unit,
    val openAppSettings: () -> Unit,
    /** Asks for the local network permission (or opens the app's settings once it was denied for good); returns whether it is granted. */
    val requestLocalNetwork: suspend () -> Boolean,
)
