package io.github.mateuxlucax.caparao

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mateuxlucax.caparao.data.exporter.ExporterClient
import io.github.mateuxlucax.caparao.data.local.BrewDatabase
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.network.LocalNetwork
import io.github.mateuxlucax.caparao.data.scale.AndroidBleTransport
import io.github.mateuxlucax.caparao.data.scale.ReplayScaleSource
import io.github.mateuxlucax.caparao.data.scale.ScaleClient
import io.github.mateuxlucax.caparao.data.scale.ScaleSource
import io.github.mateuxlucax.caparao.sync.UploadRunner
import io.github.mateuxlucax.caparao.sync.UploadScheduler
import io.github.mateuxlucax.caparao.sync.WorkManagerUploadQueue
import io.github.mateuxlucax.caparao.ui.brew.BrewViewModel
import io.github.mateuxlucax.caparao.ui.brews.BrewDetailViewModel
import io.github.mateuxlucax.caparao.ui.brews.BrewsViewModel
import io.github.mateuxlucax.caparao.ui.home.HomeViewModel
import io.github.mateuxlucax.caparao.ui.home.ScaleTimer
import io.github.mateuxlucax.caparao.ui.onboarding.OnboardingViewModel
import io.github.mateuxlucax.caparao.ui.settings.ScaleStatusViewModel
import io.github.mateuxlucax.caparao.ui.settings.ServerViewModel
import io.github.mateuxlucax.caparao.ui.settings.TargetRatioViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency injection: builds the app's long-lived objects once (docs/adr/0011-code-structure.md). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Lives as long as the process: the scale connection outlives screens and is stopped with the activity. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = SettingsStore(SettingsStore.open(appContext))

    val brews = BrewRepository(BrewDatabase.open(appContext).brews())

    private val exporter = ExporterClient()

    /**
     * Whether Android 17 local network protection would block connecting to a URL; used by [uploadRunner] and the
     * server form. Bound to the application context, so ViewModels can hold it.
     */
    private val localNetworkBlocked: suspend (String) -> Boolean = { url -> LocalNetwork.isBlocked(appContext, url) }

    /** Upload jobs, one per brew, only with a server (docs/adr/0016-upload-jobs.md). */
    val uploads = UploadScheduler(settings, WorkManagerUploadQueue(appContext, UploadWorker::class.java), brews::unsyncedIds)

    /** Used by [UploadWorker]. */
    val uploadRunner = UploadRunner(brews, settings, exporter, localNetworkBlocked)

    /** The real Dot over Bluetooth, or a recording after [replay]. */
    var scale: ScaleSource = ScaleClient(AndroidBleTransport(appContext), scope)
        private set

    /**
     * Debug builds only: plays a recorded session from the assets instead of the scale, for the emulator, which has no
     * Bluetooth (scripts/e2e.sh, the instrumented tests). Must run before the first screen is created.
     */
    fun replay(session: String, startAtMs: Long) {
        scale.stop()
        val lines = appContext.assets.open("sessions/$session.jsonl").bufferedReader().use { it.readLines() }
        scale = ReplayScaleSource(scope, lines, startAtMs)
    }

    /** Scale mode's timer, driven from Home and the watch. */
    private val scaleTimer = ScaleTimer { scale }

    /** The paired watch, if any (docs/adr/0023-watch-companion.md); reads [scale] when it starts. */
    val watch = WatchLink(appContext, scope, scaleTimer) { scale }

    /** Creates the screens' ViewModels with their dependencies; reads [scale] when each ViewModel is created. */
    val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { HomeViewModel(scale, scaleTimer) }
        initializer { OnboardingViewModel(scale) { settings.onboardingDone = true } }
        initializer { ScaleStatusViewModel(scale) }
        initializer { TargetRatioViewModel(settings) }
        initializer { BrewsViewModel(brews, settings.syncStatus, uploads::resendUnsynced) }
        initializer { ServerViewModel(settings, exporter::check, uploads::resendUnsynced, uploads::serverRemoved, localNetworkBlocked) }
        initializer { BrewDetailViewModel(requireNotNull(this[BrewDetailViewModel.BREW_ID]), brews) }
        initializer {
            BrewViewModel(
                requireNotNull(this[BrewViewModel.MODE]), scale, brews, settings,
                onSaved = uploads::brewSaved, onDiscarded = uploads::brewDeleted, onLive = { watch.brew.value = it },
            )
        }
    }
}
