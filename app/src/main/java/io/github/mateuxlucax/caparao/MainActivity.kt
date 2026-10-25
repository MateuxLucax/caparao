package io.github.mateuxlucax.caparao

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import io.github.mateuxlucax.caparao.data.network.LocalNetwork
import io.github.mateuxlucax.caparao.ui.App
import io.github.mateuxlucax.caparao.ui.AppActions
import io.github.mateuxlucax.caparao.ui.theme.AppTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

/** The app's launcher activity. Its name is public (launcher shortcuts): do not move or rename it. */
class MainActivity : ComponentActivity() {
    private val container get() = (application as CaparaoApplication).container

    /** Waiting for the answer to the Bluetooth permission request. */
    private var permissionResult: CompletableDeferred<Boolean>? = null

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionResult?.complete(it.values.all { granted -> granted })
        permissionResult = null
    }

    /** Waiting for the answer to the local network permission request. */
    private var localNetworkResult: CompletableDeferred<Boolean>? = null

    /** Denied with "don't ask again": the system no longer shows the prompt, only the app's settings can grant it. */
    private var localNetworkDeniedForGood = false

    private val localNetworkRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) localNetworkDeniedForGood = !shouldShowRequestPermissionRationale(LocalNetwork.PERMISSION)
        localNetworkResult?.complete(granted)
        localNetworkResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            intent.getStringExtra(EXTRA_REPLAY)?.let { container.replay(it, intent.getLongExtra(EXTRA_REPLAY_AT_MS, 0)) }
        }
        val actions = AppActions(
            requestBluetoothPermissions = ::requestBluetoothPermissions,
            bluetoothPermissionGranted = { BLUETOOTH_PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED } },
            enableBluetooth = ::enableBluetooth,
            openAppSettings = ::openAppSettings,
            requestLocalNetwork = ::requestLocalNetwork,
        )
        if (savedInstanceState == null) lifecycleScope.launch { container.uploads.ensurePending() }
        setContent {
            AppTheme {
                App(container.viewModelFactory, container.scale, container.settings.onboardingDone, actions, BuildConfig.VERSION_NAME)
            }
        }
    }

    /** The system's "turn on Bluetooth?" dialog. It needs BLUETOOTH_CONNECT; without it, the settings are the way. */
    private fun enableBluetooth() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    private suspend fun requestBluetoothPermissions(): Boolean {
        permissionResult?.complete(false)
        val result = CompletableDeferred<Boolean>().also { permissionResult = it }
        permissionRequest.launch(BLUETOOTH_PERMISSIONS)
        return result.await()
    }

    /** Android 17+ local network access for a server on the LAN (docs/adr/0017-local-network-permission.md). */
    private suspend fun requestLocalNetwork(): Boolean {
        if (LocalNetwork.isGranted(this)) return true
        if (localNetworkDeniedForGood) {
            openAppSettings()
            return false
        }
        localNetworkResult?.complete(false)
        val result = CompletableDeferred<Boolean>().also { localNetworkResult = it }
        localNetworkRequest.launch(LocalNetwork.PERMISSION)
        return result.await()
    }

    companion object {
        val BLUETOOTH_PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

        /** Debug builds: name of a recorded session in the assets to play instead of the scale (see [AppContainer.replay]). */
        const val EXTRA_REPLAY = "replay"
        const val EXTRA_REPLAY_AT_MS = "replay_at_ms"
    }
}
