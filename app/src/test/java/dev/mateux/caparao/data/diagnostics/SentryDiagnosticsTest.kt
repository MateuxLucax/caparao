package dev.mateux.caparao.data.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.mateux.caparao.data.exporter.ExporterClient
import dev.mateux.caparao.data.exporter.LocalServer
import dev.mateux.caparao.data.local.SettingsStore
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.SyncError
import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.hints.SubmissionResult
import io.sentry.protocol.SentryId
import io.sentry.util.HintUtils
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

/** #333: consent, scrubbing and quota behavior of the Sentry implementation. */
@RunWith(RobolectricTestRunner::class)
class SentryDiagnosticsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = SettingsStore(SettingsStore.open(context))
    private val dsn = "https://public@o0.ingest.sentry.io/0"
    private var now = 1_800_000_000_000L

    @Before
    fun setUp() {
        SentryDiagnostics.shutdownForTest()
    }

    @After
    fun tearDown() {
        SentryDiagnostics.shutdownForTest()
        settings.remoteDiagnostics = false
    }

    private fun diagnostics(
        dsn: String = this.dsn,
        quota: EventQuota = EventQuota(clock = { now }),
        captureEvent: (SentryEvent, Hint) -> SentryId = { _, _ -> SentryId.EMPTY_ID },
        flush: (Long) -> Unit = {},
    ) = SentryDiagnostics(
        context = context,
        settings = settings,
        dsn = dsn,
        environment = "debug",
        release = "caparao@1.0.0+1",
        scope = null,
        clock = { now },
        quota = quota,
        captureEvent = captureEvent,
        flush = flush,
    )

    @Test
    fun startsOnlyWithConsentAndDsn() {
        val off = diagnostics()
        off.resetForTest()
        assertFalse("off by default", off.isActiveForTest())
        assertFalse("no SDK without the opt-in", off.isSdkEnabledForTest())
        assertFalse("nothing is written to the cache", off.cacheDirForTest().exists())

        settings.remoteDiagnostics = true
        val noDsn = diagnostics(dsn = "")
        assertFalse("no DSN means no SDK", noDsn.isActiveForTest())
        assertFalse(noDsn.isSdkEnabledForTest())
        assertFalse(noDsn.cacheDirForTest().exists())

        val on = diagnostics()
        assertTrue("opt-in plus DSN starts the SDK", on.isActiveForTest())
        assertTrue(on.isSdkEnabledForTest())
        on.resetForTest()
        assertFalse(on.isSdkEnabledForTest())
    }

    @Test
    fun turningConsentOffClosesTheSdkAndEmptiesItsCache() {
        settings.remoteDiagnostics = true
        val sut = diagnostics()
        assertTrue(sut.isActiveForTest())

        val probe = File(sut.cacheDirForTest(), "probe").apply {
            parentFile?.mkdirs()
            writeText("pending")
        }
        assertTrue(probe.exists())

        settings.remoteDiagnostics = false
        sut.refreshConsent()

        assertFalse(sut.isActiveForTest())
        assertFalse(sut.isSdkEnabledForTest())
        assertFalse("the on-disk cache is gone", probe.exists())
    }

    @Test
    fun crashBeforeConsentIsDropped() {
        settings.remoteDiagnostics = true
        val consent = settings.remoteDiagnosticsConsentMs
        val sut = diagnostics(dsn = "")

        assertFalse("an ANR read on the next launch can predate consent", sut.wouldSendCrashAtForTest(consent - 1))
        assertTrue(sut.wouldSendCrashAtForTest(consent + 1))
    }

    @Test
    fun uploadFailuresCarryNeitherTokenHostNorResponseDetail() {
        settings.remoteDiagnostics = true
        val token = "server-token"
        val note = "lemon candy"
        val coffee = "Fazenda Boa Vista"
        val recipe = "V60 4:6"
        LocalServer().use { server ->
            server.status(400)
            server.replyBody = """{"error":"$note|$coffee|$recipe","host":"${server.url}"}"""
            val client = ExporterClient(connectTimeoutMs = 1_000, readTimeoutMs = 1_000)
            val failure = try {
                runBlocking { client.upload(server.url, token, brew(), emptyList()) }
                fail("expected a rejection")
                throw IllegalStateException()
            } catch (e: Exception) {
                e
            }

            val json = diagnostics().serializeErrorForTest(failure, DiagnosticErrorContext.UPLOAD)!!
            assertTrue(json, json.contains("upload failed"))
            listOf(token, "127.0.0.1", note, coffee, recipe).forEach { forbidden ->
                assertFalse("$forbidden must not leave the phone: $json", json.contains(forbidden))
            }
        }
    }

    @Test
    fun urlsAndHostsAreScrubbedFromOtherErrors() {
        settings.remoteDiagnostics = true
        val json = diagnostics().serializeErrorForTest(
            IOException("cannot reach http://coffee.example.com:8099/api and 192.168.1.10:8099 and cafe.example.org."),
            DiagnosticErrorContext.SCALE,
        )!!

        assertTrue(json, json.contains("<server>"))
        assertFalse(json, json.contains("coffee.example.com"))
        assertFalse(json, json.contains("192.168.1.10"))
        assertFalse(json, json.contains("cafe.example.org"))
    }

    @Test
    fun eventsBecomeBreadcrumbsAndLogsWithoutFreeText() {
        val sut = diagnostics()
        val forbidden = listOf("lemon candy", "Fazenda Boa Vista", "V60 4:6", "example.com")
        for (event in events()) {
            val breadcrumb = sut.serializeBreadcrumbForTest(event)
            val log = sut.serializeLogForTest(event)
            assertTrue("breadcrumb keeps the fixed token: $breadcrumb", breadcrumb.contains(event.note()))
            assertTrue("log keeps the fixed token: $log", log.contains(event.note()))
            forbidden.forEach { text ->
                assertFalse("$text must not be in a breadcrumb: $breadcrumb", breadcrumb.contains(text))
                assertFalse("$text must not be in a log: $log", log.contains(text))
            }
        }
    }

    @Test
    fun quotaDeduplicatesByFingerprintAndCapsTheSession() {
        settings.remoteDiagnostics = true
        val quota = EventQuota(maxPerSession = 2, maxPerDay = 10, clock = { now })
        val sut = diagnostics(dsn = "", quota = quota)

        val first = IOException("first")
        assertNotNull(sut.serializeErrorForTest(first, DiagnosticErrorContext.SCALE))
        assertNull("the same crash is not sent twice", sut.serializeErrorForTest(first, DiagnosticErrorContext.SCALE))
        assertNotNull(sut.serializeErrorForTest(IOException("second"), DiagnosticErrorContext.SCALE))
        assertNull(
            "the session cap stops the crash loop",
            sut.serializeErrorForTest(IllegalStateException("third"), DiagnosticErrorContext.SCALE),
        )
    }

    @Test
    fun capturesUseTransportAcknowledgementAndBypassTheCrashQuota() {
        settings.remoteDiagnostics = true
        var sentEvent: SentryEvent? = null
        var flushTimeout = 0L
        lateinit var sut: SentryDiagnostics
        sut = diagnostics(
            quota = EventQuota(maxPerSession = 1, maxPerDay = 1, clock = { now }),
            captureEvent = { event, hint ->
                sentEvent = event
                assertNotNull("capture reaches beforeSend outside the crash quota", sut.beforeSendForTest(event, hint))
                assertTrue(HintUtils.getSentrySdkHint(hint) is SubmissionResult)
                (HintUtils.getSentrySdkHint(hint) as SubmissionResult).setResult(true)
                SentryId("1d0f15a9-b3a1-4bb7-9884-334d48e760e2")
            },
            flush = { timeout -> flushTimeout = timeout },
        )
        val file = File.createTempFile("capture", ".jsonl").apply { writeText("{}\n") }
        try {
            repeat(2) { assertTrue("each capture needs a transport acknowledgement", sut.sendCapture(file, true, "1.0", "filter")) }
            assertEquals(SentryLevel.INFO, sentEvent?.level)
            assertEquals(listOf("capture"), sentEvent?.fingerprints)
            assertNull("capture event must not carry a user/IP address", sentEvent?.user)
            assertTrue("flush uses the send timeout", flushTimeout > 0)

            val failedFlush = diagnostics(
                captureEvent = { _, hint ->
                    (HintUtils.getSentrySdkHint(hint) as SubmissionResult).setResult(false)
                    SentryId("1d0f15a9-b3a1-4bb7-9884-334d48e760e2")
                },
            )
            assertFalse(failedFlush.sendCapture(file, complete = true, appVersion = "1.0", mode = "filter"))
        } finally {
            file.delete()
        }
    }

    private fun brew() = Brew(
        "0f8e1c2a-5b7d-4c3e-9a10-2f6d8b4e7c91",
        BrewMode.ESPRESSO,
        now,
        28_600,
        18.0,
        36.4,
        6_200,
        1.3,
        2.1,
        interrupted = false,
    )

    private fun events(): List<DiagnosticEvent> = listOf(
        DiagnosticEvent.AppForeground,
        DiagnosticEvent.AppBackground,
        DiagnosticEvent.ScaleStarted,
        DiagnosticEvent.ScanStarted,
        DiagnosticEvent.ScaleNotFound,
        DiagnosticEvent.ScaleConnecting,
        DiagnosticEvent.ScaleConnected,
        DiagnosticEvent.ScaleConnectionFailed,
        DiagnosticEvent.ScaleLinkLost,
        DiagnosticEvent.ScaleStopped,
        DiagnosticEvent.BrewScreenOpened,
        DiagnosticEvent.BrewScreenClosed,
        DiagnosticEvent.BrewArmed,
        DiagnosticEvent.BrewStopRequested,
        DiagnosticEvent.BrewLiftEnded,
        DiagnosticEvent.BrewInterrupted,
        DiagnosticEvent.BrewResultReady,
        DiagnosticEvent.UploadSucceeded,
        DiagnosticEvent.UploadFailed(SyncError.Kind.SERVER),
        DiagnosticEvent.UploadRetried,
        DiagnosticEvent.SessionStarted,
        DiagnosticEvent.BrewPhaseChanged(DiagnosticBrewMode.FILTER, DiagnosticBrewPhase.BREWING),
    )
}
