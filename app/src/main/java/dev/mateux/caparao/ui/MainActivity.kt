package dev.mateux.caparao.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.mateux.caparao.BuildConfig
import dev.mateux.caparao.CaparaoApplication
import dev.mateux.caparao.data.network.LocalNetwork
import dev.mateux.caparao.data.diagnostics.DiagnosticEvent
import dev.mateux.caparao.model.ScaleState
import dev.mateux.caparao.ui.App
import dev.mateux.caparao.ui.components.AppActions
import dev.mateux.caparao.ui.components.LauncherShortcut
import dev.mateux.caparao.ui.theme.AppTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The app's launcher activity, moved into the UI layer by the compatibility reset (#396). Its name is public (launcher
 * shortcuts and the widget); from the first Play upload on, do not move or rename it.
 */
class MainActivity : ComponentActivity() {
    internal val navEntryStoreProvider by lazy { ViewModelStoreProvider(this, "navigation-entries") }
    private val container get() = (application as CaparaoApplication).container
    internal var configurationChange: () -> Boolean = { isChangingConfigurations }
    private val debugHooks get() = (application as CaparaoApplication).debugHooks

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

    /** A launcher shortcut (#116) asked for a destination that [App] has not applied yet; null once it has. */
    private val requestedShortcut = MutableStateFlow<LauncherShortcut?>(null)
    /** A reminder notification asked MainActivity to show this brew's rating screen. */
    private val requestedRating = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        debugHooks.prepare(intent, savedInstanceState)
        debugHooks.onActivityCreate(container)
        requestedShortcut.value = LauncherShortcut.pendingOnCreate(
            savedId = savedInstanceState?.getString(STATE_SHORTCUT),
            launchAction = intent.action,
            recreated = savedInstanceState != null,
        )
        requestedRating.value = savedInstanceState?.getString(STATE_RATING) ?: intent?.ratingBrewId()
        val actions = AppActions(
            requestBluetoothPermissions = ::requestBluetoothPermissions,
            bluetoothPermissionGranted = { BLUETOOTH_PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED } },
            enableBluetooth = ::enableBluetooth,
            openAppSettings = ::openAppSettings,
            requestLocalNetwork = ::requestLocalNetwork,
            requestNotificationsPermission = ::requestNotificationsPermission,
            notificationsPermissionGranted = ::notificationsPermissionGranted,
        )
        if (savedInstanceState == null) lifecycleScope.launch { container.uploads.ensurePending() }
        keepScreenOnWhileConnected()
        setContent {
            val shortcut by requestedShortcut.collectAsStateWithLifecycle()
            val ratingBrewId by requestedRating.collectAsStateWithLifecycle()
            AppTheme {
                App(
                    container.viewModelFactory,
                    container.scale,
                    container.settings.onboardingDone,
                    actions,
                    BuildConfig.VERSION_NAME,
                    shortcut = shortcut,
                    onShortcutConsumed = ::shortcutConsumed,
                    ratingBrewId = ratingBrewId,
                    onRatingConsumed = ::ratingConsumed,
                    entryStoreProvider = navEntryStoreProvider,
                )
            }
        }
    }

    /** A shortcut tapped while the task is alive: MainActivity is singleTop, so the system delivers it here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        LauncherShortcut.fromAction(intent.action)?.let { requestedShortcut.value = it }
        requestedRating.value = intent.ratingBrewId()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // A consumed shortcut is saved as absent, so a rotation or a restored process does not apply it again.
        outState.putString(STATE_SHORTCUT, requestedShortcut.value?.id)
        outState.putString(STATE_RATING, requestedRating.value)
    }

    private fun shortcutConsumed() {
        requestedShortcut.value = null
        // The launch intent outlives the activity that consumed it: drop the action so a later re-creation cannot read it.
        intent?.action = null
    }

    private fun ratingConsumed() {
        requestedRating.value = null
        intent?.action = null
        intent?.removeExtra(EXTRA_RATING_BREW_ID)
    }

    /**
     * Keeps the screen on while the scale is connected: hands are busy with the coffee, and a sleeping phone drops the
     * connection, which lets the scale go to sleep too.
     */
    private fun keepScreenOnWhileConnected() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.scale.state.collect { state ->
                    if (state is ScaleState.Connected) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }
    }

    // The watch mirrors the phone only while the app is visible, like the scale connection (#92).
    override fun onStart() {
        super.onStart()
        container.diagnostics.event(DiagnosticEvent.AppForeground)
        container.scale.start()
        container.watch.start()
    }

    override fun onResume() {
        super.onResume()
        container.scale.start()
    }

    override fun onStop() {
        container.diagnostics.event(DiagnosticEvent.AppBackground)
        container.watch.stop()
        stopScaleIfBackgrounded(configurationChange())
        super.onStop()
    }

    private fun stopScaleIfBackgrounded(changingConfigurations: Boolean) {
        if (!changingConfigurations) container.scale.stop()
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

    /** Android 17+ local network access for a server on the LAN (#101). */
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

    private fun notificationsPermissionGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private suspend fun requestNotificationsPermission(): Boolean {
        if (notificationsPermissionGranted()) return true
        notificationsPermissionResult?.complete(false)
        val result = CompletableDeferred<Boolean>().also { notificationsPermissionResult = it }
        notificationsPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        return result.await()
    }

    companion object {
        val BLUETOOTH_PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

        /** The shortcut still pending in the saved instance state (#116). */
        private const val STATE_SHORTCUT = "shortcut"
        private const val STATE_RATING = "rating_brew_id"
        const val ACTION_RATE_BREW = "dev.mateux.caparao.action.RATE_BREW"
        const val EXTRA_RATING_BREW_ID = "rating_brew_id"

        private fun Intent.ratingBrewId(): String? =
            takeIf { action == ACTION_RATE_BREW }?.getStringExtra(EXTRA_RATING_BREW_ID)?.takeIf(String::isNotBlank)
    }

    /** Waiting for the answer to the first Notifications permission request. */
    private var notificationsPermissionResult: CompletableDeferred<Boolean>? = null

    private val notificationsPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsPermissionResult?.complete(granted)
        notificationsPermissionResult = null
    }
}
