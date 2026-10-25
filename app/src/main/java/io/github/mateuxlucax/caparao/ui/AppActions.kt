package io.github.mateuxlucax.caparao.ui

/** Things only the activity can do, passed to the screens. They never go into a ViewModel. */
class AppActions(
    /** Shows the system dialog for the nearby devices permission; returns whether it was granted. */
    val requestBluetoothPermissions: suspend () -> Boolean,
    val enableBluetooth: () -> Unit,
    val openAppSettings: () -> Unit,
)
