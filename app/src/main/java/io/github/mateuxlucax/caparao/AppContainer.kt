package io.github.mateuxlucax.caparao

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mateuxlucax.caparao.data.exporter.ExporterClient
import io.github.mateuxlucax.caparao.data.export.BrewExportFiles
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticRecorder
import io.github.mateuxlucax.caparao.data.local.BrewDatabase
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.CoffeeBagRepository
import io.github.mateuxlucax.caparao.data.local.RecipeRepository
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.network.LocalNetwork
import io.github.mateuxlucax.caparao.data.scale.AndroidBleTransport
import io.github.mateuxlucax.caparao.data.scale.ReplayScaleSource
import io.github.mateuxlucax.caparao.data.scale.ScaleClock
import io.github.mateuxlucax.caparao.data.scale.ScaleClient
import io.github.mateuxlucax.caparao.data.scale.ScaleSource
import io.github.mateuxlucax.caparao.sync.UploadRunner
import io.github.mateuxlucax.caparao.sync.UploadScheduler
import io.github.mateuxlucax.caparao.sync.WorkManagerUploadQueue
import io.github.mateuxlucax.caparao.ui.brew.BrewViewModel
import io.github.mateuxlucax.caparao.ui.brews.BrewDetailViewModel
import io.github.mateuxlucax.caparao.ui.brews.BrewsViewModel
import io.github.mateuxlucax.caparao.ui.brews.CompareViewModel
import io.github.mateuxlucax.caparao.ui.brews.TrendsViewModel
import io.github.mateuxlucax.caparao.ui.home.HomeViewModel
import io.github.mateuxlucax.caparao.ui.home.ScaleTimer
import io.github.mateuxlucax.caparao.ui.onboarding.OnboardingViewModel
import io.github.mateuxlucax.caparao.ui.recipes.RecipeEditorViewModel
import io.github.mateuxlucax.caparao.ui.coffees.CoffeeBagsViewModel
import io.github.mateuxlucax.caparao.ui.recipes.RecipesViewModel
import io.github.mateuxlucax.caparao.ui.settings.ScaleStatusViewModel
import io.github.mateuxlucax.caparao.ui.settings.ServerViewModel
import io.github.mateuxlucax.caparao.ui.settings.TargetRatioViewModel
import io.github.mateuxlucax.caparao.ui.settings.ExportViewModel
import io.github.mateuxlucax.caparao.ui.settings.DiagnosticsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency injection: builds the app's long-lived objects once (#95). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Lives as long as the process: the scale connection outlives screens and is stopped with the activity. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = SettingsStore(SettingsStore.open(appContext))

    /** Opt-in BLE and brew lifecycle capture, retained privately and only shared on request. */
    val diagnostics = DiagnosticRecorder(
        directory = appContext.filesDir.resolve("diagnostics"),
        scope = scope,
        monotonicNanos = SystemClock::elapsedRealtimeNanos,
    )

    /** Public for the screen tests, which empty it between runs. */
    val database = BrewDatabase.open(appContext)

    val brews = BrewRepository(database.brews()) { BrewWidgetProvider.requestRefresh(appContext) }

    val recipes = RecipeRepository(database.recipes())

    val coffeeBags = CoffeeBagRepository(database.coffeeBags())

    /** Writes the exports to the cache and shares them through the FileProvider (#76). */
    val export = BrewExportFiles(appContext, brews, recipes)

    private val exporter = ExporterClient()

    /**
     * Whether Android 17 local network protection would block connecting to a URL; used by [uploadRunner] and the
     * server form. Bound to the application context, so ViewModels can hold it.
     */
    private val localNetworkBlocked: suspend (String) -> Boolean = { url -> LocalNetwork.isBlocked(appContext, url) }

    /** Upload jobs, one per brew, only with a server (#100). */
    val uploads = UploadScheduler(settings, WorkManagerUploadQueue(appContext, UploadWorker::class.java), brews::unsyncedIds)

    /** Used by [UploadWorker]. */
    val uploadRunner = UploadRunner(brews, settings, exporter, localNetworkBlocked)

    /** The real Dot over Bluetooth, or a recording after [replay]. */
    var scale: ScaleSource = ScaleClient(AndroidBleTransport(appContext), scope, diagnostics = diagnostics)
        private set

    /**
     * Debug builds only: plays a recorded session from the assets instead of the scale, for the emulator, which has no
     * Bluetooth (scripts/e2e.sh, the instrumented tests). Must run before the first screen is created.
     */
    fun replay(
        session: String,
        startAtMs: Long,
        startPaused: Boolean = false,
        playbackSpeed: Double = 1.0,
        repeat: Boolean = true,
    ) {
        scale.stop()
        val lines = appContext.assets.open("sessions/$session.jsonl").bufferedReader().use { it.readLines() }
        scale = ReplayScaleSource(scope, lines, startAtMs, startPaused, playbackSpeed, repeat)
    }

    /** Installs a debug-only source created by the debug source set. */
    fun installDebugScaleSource(source: ScaleSource) {
        scale.stop()
        scale = source
    }

    /** Scale mode's timer, driven from Home and the watch. */
    private val scaleTimer = ScaleTimer { scale }

    /** The paired watch, if any (#107); reads [scale] when it starts. */
    val watch = WatchLink(appContext, scope, scaleTimer) { scale }

    /** Creates the screens' ViewModels with their dependencies; reads [scale] when each ViewModel is created. */
    val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { HomeViewModel(scale, scaleTimer) }
        initializer { OnboardingViewModel(scale) { settings.onboardingDone = true } }
        initializer { ScaleStatusViewModel(scale) }
        initializer { TargetRatioViewModel(settings) }
        initializer { ExportViewModel(export) }
        initializer { DiagnosticsViewModel(appContext, diagnostics) }
        initializer { RecipesViewModel(recipes) }
        initializer { CoffeeBagsViewModel(coffeeBags) }
        initializer { RecipeEditorViewModel(requireNotNull(this[RecipeEditorViewModel.TARGET]), recipes) }
        initializer { BrewsViewModel(brews, settings.syncStatus, uploads::resendUnsynced) }
        initializer { ServerViewModel(settings, exporter::check, uploads::resendUnsynced, uploads::serverRemoved, localNetworkBlocked) }
        initializer { BrewDetailViewModel(requireNotNull(this[BrewDetailViewModel.BREW_ID]), brews, export, uploads::brewSaved) }
        initializer { CompareViewModel(requireNotNull(this[CompareViewModel.BREW_IDS]), brews) }
        initializer { TrendsViewModel(requireNotNull(this[TrendsViewModel.KEY]), brews) }
        initializer {
            BrewViewModel(
                requireNotNull(this[BrewViewModel.MODE]), scale, brews, settings, recipes,
                clock = { (scale as? ScaleClock)?.nowMs ?: SystemClock.elapsedRealtime() },
                onSaved = uploads::brewSaved, onDiscarded = uploads::brewDeleted, onLive = { watch.brew.value = it },
                onWatchAlert = watch::alert, diagnostics = diagnostics,
            )
        }
    }
}
