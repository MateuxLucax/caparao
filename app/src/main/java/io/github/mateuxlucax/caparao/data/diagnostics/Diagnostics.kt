package io.github.mateuxlucax.caparao.data.diagnostics

import io.github.mateuxlucax.caparao.model.SyncError

/**
 * App-owned diagnostic vocabulary (#332): fixed events whose payloads are enums or numbers only, so one can travel off
 * the phone without a server URL, a token, a brew note or a coffee name.
 */
sealed interface DiagnosticEvent {
    data object AppForeground : DiagnosticEvent
    data object AppBackground : DiagnosticEvent
    data object ScaleStarted : DiagnosticEvent
    data object ScanStarted : DiagnosticEvent
    data object ScaleNotFound : DiagnosticEvent
    data object ScaleConnecting : DiagnosticEvent
    data object ScaleConnected : DiagnosticEvent
    data object ScaleConnectionFailed : DiagnosticEvent
    data object ScaleLinkLost : DiagnosticEvent
    data object ScaleStopped : DiagnosticEvent
    data object BrewScreenOpened : DiagnosticEvent
    data object BrewScreenClosed : DiagnosticEvent
    data object BrewArmed : DiagnosticEvent
    data object BrewStopRequested : DiagnosticEvent
    /** A filter lift past the last pour ends the brew, without Done (#277). */
    data object BrewLiftEnded : DiagnosticEvent
    data object BrewInterrupted : DiagnosticEvent
    data object BrewResultReady : DiagnosticEvent
    data object UploadSucceeded : DiagnosticEvent
    /** The server refused or could not take the brew: only the stable kind, never its detail. */
    data class UploadFailed(val kind: SyncError.Kind) : DiagnosticEvent
    /** The worker will try the same brew again later. */
    data object UploadRetried : DiagnosticEvent
    data object SessionStarted : DiagnosticEvent

    data class BrewPhaseChanged(val mode: DiagnosticBrewMode, val phase: DiagnosticBrewPhase) : DiagnosticEvent
}

enum class DiagnosticBrewMode(val note: String) {
    ESPRESSO("espresso"),
    FILTER("filter"),
    FRENCH_PRESS("french_press"),
}

/** Detector phases are copied into the data layer so free-form recipe or coffee text can never enter a log line. */
enum class DiagnosticBrewPhase(val note: String) {
    WAITING_FOR_CUP("waiting_for_cup"),
    TARING("taring"),
    READY("ready"),
    BREWING("brewing"),
    DONE("done"),
}

/** Where a caught exception was caught: a fixed token, never its message or a URL. */
enum class DiagnosticErrorContext {
    SCALE,
    UPLOAD,
    APP,
}

/**
 * The seam every diagnostic call site uses (#332), so a remote implementation can replace the local recorder behind it.
 * Implementations must return quickly: the BLE collector and the brew detector call them without a blocking I/O step.
 */
interface Diagnostics {
    fun event(event: DiagnosticEvent)

    /** One original GATT notification chunk, before the scale decoder consumes or transforms it. */
    fun notification(bytes: ByteArray)

    /** One successful outgoing write, snapshotted before the bytes went to Android BLE. */
    fun command(bytes: ByteArray)

    /**
     * The BLE link dropped after the scale state was published, so an active brew could report its interrupted phase
     * first. A capture in progress ends incomplete; a deliberate app stop never calls this.
     */
    fun disconnect()

    /** A caught exception worth reporting; the implementation scrubs the message before storing or sending it. */
    fun error(throwable: Throwable, context: DiagnosticErrorContext)
}

/** Sink for builds and tests that collect nothing; the default until the Settings capture is started. */
object NoDiagnostics : Diagnostics {
    override fun event(event: DiagnosticEvent) = Unit
    override fun notification(bytes: ByteArray) = Unit
    override fun command(bytes: ByteArray) = Unit
    override fun disconnect() = Unit
    override fun error(throwable: Throwable, context: DiagnosticErrorContext) = Unit
}
