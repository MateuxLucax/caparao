package io.github.mateuxlucax.caparao.data.diagnostics

import android.content.Context
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.sentry.Breadcrumb
import io.sentry.Attachment
import io.sentry.Hint
import io.sentry.JsonSerializable
import io.sentry.JsonSerializer
import io.sentry.Sentry
import io.sentry.SentryAttribute
import io.sentry.SentryAttributes
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryLogEvent
import io.sentry.SentryLogEventAttributeValue
import io.sentry.SentryLogLevel
import io.sentry.SentryOptions
import io.sentry.protocol.SentryId
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.SentryAndroidOptions
import io.sentry.logger.SentryLogParameters
import io.sentry.protocol.Mechanism
import io.sentry.protocol.Message
import io.sentry.protocol.SentryException
import io.sentry.protocol.SentryStackFrame
import io.sentry.protocol.SentryStackTrace
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringWriter
import java.util.zip.GZIPOutputStream
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Remote diagnostics for #333, the only file that imports `io.sentry` (LayerDependencyTest keeps it that way).
 *
 * The SDK starts only while the Settings opt-in is on and a DSN exists. Events become fixed-token breadcrumbs and
 * logs; caught exceptions become handled events. Raw BLE frames never leave the phone through this path.
 */
internal class SentryDiagnostics(
    context: Context,
    private val settings: SettingsStore,
    private val dsn: String,
    private val environment: String,
    private val release: String,
    scope: CoroutineScope? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val quota: EventQuota = EventQuota(clock = clock),
    private val captureEvent: (SentryEvent, Hint) -> SentryId = { event, hint -> Sentry.captureEvent(event, hint) },
    private val flush: (Long) -> Boolean = { timeout -> runCatching { Sentry.flush(timeout) }.isSuccess },
) : Diagnostics {
    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.cacheDir, CACHE_DIR_NAME)

    @Volatile
    private var active = false

    @Volatile
    private var consentMs = 0L

    init {
        refreshConsent()
        scope?.launch { settings.remoteDiagnosticsChanges.collect { refreshConsent() } }
    }

    /** Re-reads the stored opt-in and starts or stops the SDK. Called at startup and whenever the toggle changes (#334). */
    fun refreshConsent() {
        val enabled = settings.remoteDiagnostics && dsn.isNotBlank()
        if (!enabled) {
            if (active) closeAndWipe()
            return
        }
        val consent = settings.remoteDiagnosticsConsentMs
        if (!active) {
            start(consent)
        } else if (consent != consentMs) {
            // Consent given again while the SDK is already on: a new window, so past events are not sent.
            quota.reset(clock())
        }
        consentMs = consent
    }

    /** One event becomes a breadcrumb and a log with structured attributes. */
    override fun event(event: DiagnosticEvent) {
        if (!active) return
        Sentry.addBreadcrumb(breadcrumbFor(event))
        Sentry.logger().log(levelFor(event), SentryLogParameters.create(attributesFor(event)), event.note())
    }

    /** Raw frames are for the local capture only; they never become breadcrumbs or logs (#330). */
    override fun notification(bytes: ByteArray, classification: DiagnosticFrameClassification) = Unit

    override fun command(bytes: ByteArray) = Unit

    override fun disconnect() = Unit

    /** A caught exception becomes a handled event; [context] decides what may be sent (#333). */
    override fun error(throwable: Throwable, context: DiagnosticErrorContext) {
        if (!active) return
        val hint = Hint()
        hint.set(HINT_CONTEXT, context)
        Sentry.captureEvent(eventFor(throwable, context), hint)
    }

    /** Upload one opted-in capture as an event-local attachment and retain it unless Sentry confirms delivery. */
    fun sendCapture(file: File, complete: Boolean, appVersion: String, mode: String): Boolean {
        if (!active || !settings.remoteDiagnostics || !file.isFile) return false
        val compressed = runCatching {
            ByteArrayOutputStream().also { bytes ->
                GZIPOutputStream(bytes).use { output -> file.inputStream().use { it.copyTo(output) } }
            }.toByteArray()
        }.getOrNull() ?: return false
        if (compressed.size > MAX_CAPTURE_ATTACHMENT_BYTES) return false
        val event = SentryEvent().apply {
            message = Message().apply { message = "capture" }
            level = SentryLevel.INFO
            fingerprints = listOf("capture")
            user = null
            setTag("mode", mode)
            setTag("complete", complete.toString())
            setTag("app_version", appVersion)
        }
        val hint = Hint.withAttachment(Attachment(compressed, "capture.jsonl.gz", "application/gzip"))
        val sentryId = runCatching { captureEvent(event, hint) }.getOrNull() ?: return false
        return sentryId != SentryId.EMPTY_ID && runCatching { flush(SEND_TIMEOUT_MS) }.getOrDefault(false)
    }

    private fun start(consent: Long): Boolean {
        // Reset before init: the ANR read on the next launch can reach beforeSend as soon as the SDK starts.
        quota.reset(clock())
        val started = runCatching {
            SentryAndroid.init(appContext) { options -> configure(options, consent) }
        }.isSuccess
        if (!started) {
            closeAndWipe()
            return false
        }
        active = true
        consentMs = consent
        return true
    }

    private fun closeAndWipe() {
        active = false
        runCatching { Sentry.close() }
        cacheDir.deleteRecursively()
    }

    @Suppress("DEPRECATION")
    private fun configure(options: SentryAndroidOptions, consent: Long) {
        options.dsn = dsn
        options.cacheDirPath = cacheDir.absolutePath
        options.environment = environment
        options.release = release
        options.setSendDefaultPii(false)
        // No sessions, screenshots, view hierarchy, replay or performance tracing: only crashes, ANRs and our events.
        options.setEnableAutoSessionTracking(false)
        options.setEnableUserInteractionBreadcrumbs(false)
        options.setEnableSystemEventBreadcrumbs(false)
        options.setEnableNetworkEventBreadcrumbs(false)
        options.setAttachScreenshot(false)
        options.setAttachViewHierarchy(false)
        options.setEnableAutoActivityLifecycleTracing(false)
        options.tracesSampleRate = null
        options.profilesSampleRate = null
        // ANR v2 through ApplicationExitInfo; debug builds report them too, so a debug trigger is observable.
        options.setAnrEnabled(true)
        options.setAnrReportInDebug(true)
        options.maxAttachmentSize = MAX_CAPTURE_ATTACHMENT_BYTES
        options.logs.isEnabled = true
        options.beforeSend = SentryOptions.BeforeSendCallback { event, hint -> beforeSend(event, hint, consent) }
        options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ -> scrubBreadcrumb(breadcrumb) }
        options.logs.setBeforeSend { log -> scrubLog(log) }
    }

    private fun beforeSend(event: SentryEvent, hint: Hint, consent: Long): SentryEvent? {
        if (!settings.remoteDiagnostics) return null
        // The ANR the SDK reads on the next launch can be older than the consent that turned reporting on.
        if (isCrashOrExitInfo(event) && event.timestamp.time < consent) return null
        if (hint.getAs(HINT_CONTEXT, DiagnosticErrorContext::class.java) == DiagnosticErrorContext.UPLOAD) {
            // SyncError.detail carries up to 200 characters of the server's response body; only the kind may leave.
            event.exceptions?.forEach { it.value = UPLOAD_FAILED }
            event.message?.let {
                it.formatted = null
                it.message = null
            }
        }
        scrubEvent(event)
        return event.takeIf { quota.allow(it, clock()) }
    }

    private fun isCrashOrExitInfo(event: SentryEvent): Boolean =
        event.isCrashed || event.exceptions?.any { it.mechanism?.type in EXIT_INFO_MECHANISMS } == true

    private fun scrubEvent(event: SentryEvent) {
        event.message?.let { message ->
            message.formatted = message.formatted?.let(::scrub)
            message.message = message.message?.let(::scrub)
        }
        event.exceptions?.forEach { exception -> exception.value = exception.value?.let(::scrub) }
    }

    private fun scrubBreadcrumb(breadcrumb: Breadcrumb): Breadcrumb {
        breadcrumb.message = breadcrumb.message?.let(::scrub)
        breadcrumb.data.entries.forEach { (key, value) ->
            if (value is String) breadcrumb.setData(key, scrub(value))
        }
        return breadcrumb
    }

    private fun scrubLog(log: SentryLogEvent): SentryLogEvent {
        log.setBody(scrub(log.body))
        log.attributes?.let { attributes ->
            attributes.entries.forEach { (key, attribute) ->
                val value = attribute.value
                if (value is String) attributes[key] = SentryLogEventAttributeValue(attribute.type, scrub(value))
            }
        }
        return log
    }

    private fun breadcrumbFor(event: DiagnosticEvent): Breadcrumb = Breadcrumb().apply {
        category = event.category()
        message = event.note()
        level = if (levelFor(event) == SentryLogLevel.WARN) SentryLevel.WARNING else SentryLevel.INFO
    }

    private fun levelFor(event: DiagnosticEvent): SentryLogLevel = when (event) {
        DiagnosticEvent.ScaleNotFound,
        DiagnosticEvent.ScaleConnectionFailed,
        DiagnosticEvent.ScaleLinkLost,
        DiagnosticEvent.BrewInterrupted,
        is DiagnosticEvent.UploadFailed,
        -> SentryLogLevel.WARN

        else -> SentryLogLevel.INFO
    }

    private fun attributesFor(event: DiagnosticEvent): SentryAttributes {
        val attributes = SentryAttributes.of(SentryAttribute.stringAttribute("event", event.note()))
        when (event) {
            is DiagnosticEvent.BrewPhaseChanged -> {
                attributes.add(SentryAttribute.stringAttribute("brew.mode", event.mode.note))
                attributes.add(SentryAttribute.stringAttribute("brew.phase", event.phase.note))
            }
            is DiagnosticEvent.UploadFailed -> {
                attributes.add(SentryAttribute.stringAttribute("sync.kind", event.kind.name.lowercase(Locale.ROOT)))
            }
            else -> Unit
        }
        return attributes
    }

    private fun eventFor(throwable: Throwable, context: DiagnosticErrorContext): SentryEvent = SentryEvent().apply {
        level = SentryLevel.ERROR
        setTag("context", context.name.lowercase(Locale.ROOT))
        exceptions = exceptionsOf(throwable)
    }

    /** The cause chain oldest first, like the SDK's exception factory, with in-app frames marked. */
    private fun exceptionsOf(throwable: Throwable): List<SentryException> {
        val chain = ArrayList<SentryException>()
        val seen = HashSet<Throwable>()
        var cause: Throwable? = throwable
        while (cause != null && seen.add(cause)) {
            val current = cause
            chain += SentryException().apply {
                type = current.javaClass.name
                value = current.message
                mechanism = Mechanism().apply {
                    type = "generic"
                    setHandled(true)
                }
                stacktrace = SentryStackTrace(
                    current.stackTrace.map { frame ->
                        SentryStackFrame().apply {
                            module = frame.className
                            filename = frame.fileName
                            function = frame.methodName
                            lineno = frame.lineNumber.takeIf { it >= 0 }
                            setInApp(frame.className.startsWith(APP_PACKAGE))
                        }
                    },
                )
            }
            cause = current.cause
        }
        return chain.asReversed()
    }

    /** Removes URLs, host names, IPv4 addresses and `localhost` from free text; the placeholder keeps the shape. */
    private fun scrub(text: String): String = text
        .replace(URL_PATTERN, SERVER_PLACEHOLDER)
        .replace(IPV4_PATTERN, SERVER_PLACEHOLDER)
        .replace(LOCALHOST_PATTERN, SERVER_PLACEHOLDER)
        .replace(HOST_PATTERN, SERVER_PLACEHOLDER)

    private fun json(entity: JsonSerializable): String {
        val writer = StringWriter()
        JsonSerializer(SentryOptions()).serialize(entity, writer)
        return writer.toString()
    }

    // Test seams: the tests live in the same module and must not import io.sentry themselves.
    internal fun isActiveForTest(): Boolean = active

    internal fun isSdkEnabledForTest(): Boolean = Sentry.isEnabled()

    internal fun cacheDirForTest(): File = cacheDir

    internal fun resetForTest() {
        closeAndWipe()
        consentMs = 0L
        quota.reset(clock())
    }

    internal fun serializeBreadcrumbForTest(event: DiagnosticEvent): String =
        json(scrubBreadcrumb(breadcrumbFor(event)))

    internal fun serializeLogForTest(event: DiagnosticEvent): String =
        json(scrubLog(logForTest(event)))

    internal fun serializeErrorForTest(throwable: Throwable, context: DiagnosticErrorContext): String? {
        val hint = Hint()
        hint.set(HINT_CONTEXT, context)
        val event = beforeSend(eventFor(throwable, context), hint, settings.remoteDiagnosticsConsentMs) ?: return null
        return json(event)
    }

    internal fun wouldSendCrashAtForTest(timestampMs: Long): Boolean {
        val event = SentryEvent(Date(timestampMs))
        event.exceptions = listOf(
            SentryException().apply {
                type = "java.lang.RuntimeException"
                value = "crash"
                mechanism = Mechanism().apply {
                    type = "generic"
                    setHandled(false)
                }
            },
        )
        return beforeSend(event, Hint(), settings.remoteDiagnosticsConsentMs) != null
    }

    private fun logForTest(event: DiagnosticEvent): SentryLogEvent =
        SentryLogEvent(SentryId(), 0.0, event.note(), levelFor(event)).apply {
            attributes = attributesFor(event).attributes.mapValues { (_, attribute) ->
                SentryLogEventAttributeValue.fromAttribute(attribute)
            }
        }

    companion object {
        private const val HINT_CONTEXT = "diagnostic_context"
        private const val CACHE_DIR_NAME = "sentry"
        private const val APP_PACKAGE = "io.github.mateuxlucax.caparao"
        private const val UPLOAD_FAILED = "upload failed"
        private const val MAX_CAPTURE_ATTACHMENT_BYTES = 16L * 1024 * 1024
        private const val SEND_TIMEOUT_MS = 30_000L
        private const val SERVER_PLACEHOLDER = "<server>"
        private val EXIT_INFO_MECHANISMS = setOf("AppExitInfo", "HistoricalAppExitInfo", "ANR")

        private val URL_PATTERN = Regex("""(?i)\b(?:https?|ftp)://[^\s"'<>]+""")
        private val IPV4_PATTERN = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?\b""")
        private val LOCALHOST_PATTERN = Regex("""(?i)\blocalhost(?::\d{1,5})?\b""")
        private val HOST_PATTERN = Regex("""(?i)\b(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z]{2,24}(?::\d{1,5})?\b(?!\.[A-Za-z_])""")

        /** Closes the process-wide SDK between tests; safe when it was never started. */
        internal fun shutdownForTest() {
            runCatching { Sentry.close() }
        }
    }
}

/**
 * At most [maxPerSession] events per run and [maxPerDay] per day, each fingerprint once. Conservative defaults until
 * #331 confirms the project's quotas, so a crash loop cannot burn the monthly plan.
 */
internal class EventQuota(
    private val maxPerSession: Int = 20,
    private val maxPerDay: Int = 100,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val seen = HashSet<String>()
    private var sessionCount = 0
    private var day = -1L
    private var dayCount = 0

    @Synchronized
    fun reset(nowMs: Long = clock()) {
        seen.clear()
        sessionCount = 0
        day = dayOf(nowMs)
        dayCount = 0
    }

    @Synchronized
    fun allow(event: SentryEvent, nowMs: Long = clock()): Boolean {
        val today = dayOf(nowMs)
        if (today != day) {
            day = today
            dayCount = 0
        }
        if (dayCount >= maxPerDay || sessionCount >= maxPerSession) return false
        if (!seen.add(fingerprint(event))) return false
        sessionCount++
        dayCount++
        return true
    }

    private fun dayOf(ms: Long): Long = ms / MILLIS_PER_DAY

    private fun fingerprint(event: SentryEvent): String {
        event.fingerprints?.takeIf { it.isNotEmpty() }?.let { return it.joinToString("|") }
        val exception = event.exceptions?.lastOrNull()
        if (exception != null) {
            val frame = exception.stacktrace?.frames?.firstOrNull()
            val site = frame?.let { "${it.module}.${it.function}:${it.lineno}" }.orEmpty()
            return "${exception.type}|$site"
        }
        return event.message?.formatted ?: event.message?.message ?: "event"
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
