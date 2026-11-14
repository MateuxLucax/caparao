package dev.mateux.caparao

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.mateux.caparao.data.exporter.ExporterClient
import dev.mateux.caparao.data.diagnostics.CaptureWriter
import dev.mateux.caparao.data.diagnostics.removeLegacyDiagnosticCaptures
import dev.mateux.caparao.data.diagnostics.Diagnostics
import dev.mateux.caparao.data.diagnostics.FanOutDiagnostics
import dev.mateux.caparao.data.diagnostics.SentryDiagnostics
import dev.mateux.caparao.data.local.BagRecipeAdjustmentRepository
import dev.mateux.caparao.data.local.BrewDatabase
import dev.mateux.caparao.data.local.BrewRepository
import dev.mateux.caparao.data.local.CoffeeBagRepository
import dev.mateux.caparao.data.local.RecipeRepository
import dev.mateux.caparao.data.local.SettingsStore
import dev.mateux.caparao.data.network.LocalNetwork
import dev.mateux.caparao.data.scale.AndroidBleTransport
import dev.mateux.caparao.data.scale.ScaleClock
import dev.mateux.caparao.data.scale.ScaleClient
import dev.mateux.caparao.data.scale.ScaleSource
import dev.mateux.caparao.data.watch.AndroidWatchTransport
import dev.mateux.caparao.sync.UploadRunner
import dev.mateux.caparao.sync.UploadScheduler
import dev.mateux.caparao.sync.UploadWorker
import dev.mateux.caparao.sync.WorkManagerUploadQueue
import dev.mateux.caparao.ui.brew.BrewViewModel
import dev.mateux.caparao.ui.brews.BrewDetailViewModel
import dev.mateux.caparao.ui.brews.BrewRatingViewModel
import dev.mateux.caparao.ui.brews.BrewsViewModel
import dev.mateux.caparao.ui.home.HomeViewModel
import dev.mateux.caparao.ui.onboarding.OnboardingViewModel
import dev.mateux.caparao.ui.recipes.RecipeEditorViewModel
import dev.mateux.caparao.ui.coffees.CoffeeBagDetailViewModel
import dev.mateux.caparao.ui.coffees.CoffeeBagsViewModel
import dev.mateux.caparao.ui.recipes.RecipesViewModel
import dev.mateux.caparao.ui.settings.ScaleStatusViewModel
import dev.mateux.caparao.ui.server.ServerViewModel
import dev.mateux.caparao.ui.settings.TargetRatioViewModel
import dev.mateux.caparao.ui.settings.CaptureViewModel
import dev.mateux.caparao.watch.ScaleTimer
import dev.mateux.caparao.watch.WatchLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.SupervisorJob

/** Manual dependency injection: builds the app's long-lived objects once (#95). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Lives as long as the process: the scale connection outlives screens and is stopped with the activity. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = SettingsStore(SettingsStore.open(appContext))

    /** Opt-in remote diagnostics (#333); a no-op unless the setting is on and the build carries a DSN. */
    private val sentry = SentryDiagnostics(
        context = appContext,
        settings = settings,
        dsn = BuildConfig.SENTRY_DSN,
        environment = if (BuildConfig.DEBUG) "debug" else "release",
        release = "caparao@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}",
        scope = scope,
    )

    /** Opt-in BLE and brew lifecycle capture, retained privately only until Sentry confirms delivery. */
    private val captureWriter = CaptureWriter(
        directory = appContext.filesDir.resolve("captures"),
        scope = scope,
        monotonicNanos = SystemClock::elapsedRealtimeNanos,
        optedIn = { settings.remoteDiagnostics },
        send = { file, complete, mode -> sentry.sendCapture(file, complete, BuildConfig.VERSION_NAME, mode) },
        appVersion = BuildConfig.VERSION_NAME,
        phoneModel = Build.MODEL,
        androidVersion = Build.VERSION.RELEASE,
        captureEveryBrew = { settings.captureEveryBrew },
        lastSentAt = { settings.lastCaptureSentMs },
        recordSentAt = { settings.lastCaptureSentMs = it },
    )

    init {
        scope.launch(Dispatchers.IO) {
            val migrations = appContext.getSharedPreferences("caparao-migrations", Context.MODE_PRIVATE)
            removeLegacyDiagnosticCaptures(appContext.filesDir, migrations)
            captureWriter.refresh()
        }
        scope.launch { settings.remoteDiagnosticsChanges.collect { enabled -> if (!enabled) captureWriter.clear() } }
    }

    /** The seam every producer reports through: the replay capture and, while opted in, Sentry. */
    val diagnostics: Diagnostics = FanOutDiagnostics(captureWriter, sentry)

    /** Public for the screen tests, which empty it between runs. */
    val database = BrewDatabase.open(appContext)

    val brews = BrewRepository(database.brews()) { BrewWidgetProvider.requestRefresh(appContext) }

    val recipes = RecipeRepository(database.recipes())

    val coffeeBags = CoffeeBagRepository(database.coffeeBags())

    val bagRecipeAdjustments = BagRecipeAdjustmentRepository(database.bagRecipeAdjustments())

    private val exporter = ExporterClient()

    /**
     * Whether Android 17 local network protection would block connecting to a URL; used by [uploadRunner] and the
     * server form. Bound to the application context, so ViewModels can hold it.
     */
    private val localNetworkBlocked: suspend (String) -> Boolean = { url -> LocalNetwork.isBlocked(appContext, url) }

    /** Upload jobs, one per brew, only with a server (#100). */
    val uploads = UploadScheduler(settings, WorkManagerUploadQueue(appContext, UploadWorker::class.java), brews::unsyncedIds)

    init {
        scope.launch(Dispatchers.IO) {
            // #396: the worker left the root package, so jobs enqueued by the old build are dead. Run once; leaving
            // the flag unset on failure retries on the next launch.
            val migrations = appContext.getSharedPreferences("caparao-migrations", Context.MODE_PRIVATE)
            runCatching {
                uploads.rescheduleJobsFromTheOldWorker(migrations.getBoolean(KEY_UPLOAD_WORKER_MOVED, false)) {
                    migrations.edit().putBoolean(KEY_UPLOAD_WORKER_MOVED, true).commit()
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "could not reschedule upload jobs", error)
            }
        }
    }

    /** Used by [UploadWorker]. */
    val uploadRunner = UploadRunner(brews, settings, exporter, localNetworkBlocked, diagnostics = diagnostics)

    /** Chosen once at startup; debug variants may provide replay or a scripted source. */
    val scale: ScaleSource = (appContext as CaparaoApplication).debugHooks.createScale(appContext, scope)
        ?: ScaleClient(AndroidBleTransport(appContext), scope, diagnostics = diagnostics)
    private val scaleClock = scale as? ScaleClock

    /** Scale mode's timer, driven from Home and the watch. */
    private val scaleTimer = ScaleTimer(scale)

    /** The paired watch, if any (#107); follows one that connects later (#324) and reads [scale] when it starts. */
    val watch = WatchLink(AndroidWatchTransport(appContext), scope, scaleTimer, scale)

    /** Creates the screens' ViewModels with their dependencies; each receives the selected [scale]. */
    val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { HomeViewModel(scale, scaleTimer) }
        initializer { OnboardingViewModel(scale) { settings.onboardingDone = true } }
        initializer { ScaleStatusViewModel(scale) }
        initializer { TargetRatioViewModel(settings) }
        initializer { CaptureViewModel(captureWriter, settings, BuildConfig.SENTRY_DSN.isNotBlank()) }
        initializer { RecipesViewModel(recipes) }
        initializer { CoffeeBagsViewModel(coffeeBags) }
        initializer { CoffeeBagDetailViewModel(requireNotNull(this[CoffeeBagDetailViewModel.BAG_ID]), coffeeBags, brews) }
        initializer { RecipeEditorViewModel(requireNotNull(this[RecipeEditorViewModel.TARGET]), recipes) }
        initializer { BrewsViewModel(brews, settings.syncStatus, uploads::resendUnsynced) }
        initializer { ServerViewModel(settings, exporter::check, uploads::resendUnsynced, uploads::serverRemoved, localNetworkBlocked) }
        initializer { BrewDetailViewModel(requireNotNull(this[BrewDetailViewModel.BREW_ID]), brews, uploads::brewSaved, coffeeBags) }
        initializer { BrewRatingViewModel(requireNotNull(this[BrewRatingViewModel.BREW_ID]), brews, coffeeBags, uploads::brewSaved) }
        initializer {
            BrewViewModel(
                requireNotNull(this[BrewViewModel.MODE]), scale, brews, settings, recipes,
                clock = { scaleClock?.nowMs ?: SystemClock.elapsedRealtime() },
                onSaved = uploads::brewSaved, onDiscarded = uploads::brewDeleted,
                diagnostics = diagnostics,
                animations = ValueAnimator::areAnimatorsEnabled,
                coffeeBags = coffeeBags,
            ).also { watch.attach(it.watchBrew, it.watchAlerts) }
        }
    }
}

private const val TAG = "Caparao"

/** Set once after the upload worker moved out of the root package (#396). */
private const val KEY_UPLOAD_WORKER_MOVED = "upload_worker_moved"
