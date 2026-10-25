package io.github.mateuxlucax.caparao

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.scale.AndroidBleTransport
import io.github.mateuxlucax.caparao.data.scale.ReplayScaleSource
import io.github.mateuxlucax.caparao.data.scale.ScaleClient
import io.github.mateuxlucax.caparao.data.scale.ScaleSource
import io.github.mateuxlucax.caparao.ui.home.HomeViewModel
import io.github.mateuxlucax.caparao.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency injection: builds the app's long-lived objects once (docs/adr/0011-code-structure.md). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Lives as long as the process: the scale connection outlives screens and is stopped with the activity. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = SettingsStore(SettingsStore.open(appContext))

    /** The real Dot over Bluetooth, or a recording after [replay]. */
    var scale: ScaleSource = ScaleClient(AndroidBleTransport(appContext), scope)
        private set

    /**
     * Debug builds only: plays a recorded session from the assets instead of the scale, for the emulator, which has no
     * Bluetooth (scripts/screenshots.sh, the instrumented tests). Must run before the first screen is created.
     */
    fun replay(session: String, startAtMs: Long) {
        scale.stop()
        val lines = appContext.assets.open("sessions/$session.jsonl").bufferedReader().use { it.readLines() }
        scale = ReplayScaleSource(scope, lines, startAtMs)
    }

    /** Creates the screens' ViewModels with their dependencies; reads [scale] when each ViewModel is created. */
    val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { HomeViewModel(scale) }
        initializer { OnboardingViewModel(scale) { settings.onboardingDone = true } }
    }
}
