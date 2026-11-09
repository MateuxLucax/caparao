package io.github.mateuxlucax.caparao.ui.settings

import io.github.mateuxlucax.caparao.data.exporter.HttpStatusException
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.network.LocalNetwork
import io.github.mateuxlucax.caparao.model.SyncError
import io.github.mateuxlucax.caparao.model.SyncError.Kind
import io.github.mateuxlucax.caparao.ui.FakePreferences
import io.github.mateuxlucax.caparao.ui.MainDispatcherRule
import io.github.mateuxlucax.caparao.ui.components.TestResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

class ServerViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val settings = SettingsStore(FakePreferences())
    private val checked = mutableListOf<Pair<String, String>>()
    private var answer: CompletableDeferred<Unit>? = null
    private var checkFails: Exception? = null
    private var resent = 0
    private var removed = 0

    /** Gates the fake resolver, to inspect the checking state while it runs. */
    private var hostGate: CompletableDeferred<Unit>? = null
    private var addresses = listOf("192.168.0.10")
    private var unresolvable = false
    private val resolved = mutableListOf<String>()

    // Lazy: viewModelScope must be created after the rule sets the main dispatcher.
    private val viewModel by lazy {
        ServerViewModel(
            settings,
            check = { url, token ->
                checked += url to token
                answer?.await()
                checkFails?.let { throw it }
            },
            resendUnsynced = { resent++ },
            serverRemoved = { removed++ },
            localNetwork = { url ->
                hostGate?.await()
                LocalNetwork.isLocal(url) {
                    resolved += url
                    if (unresolvable) throw UnknownHostException(it)
                    addresses.map { address -> InetAddress.getByName(address) }.toTypedArray()
                }
            },
        )
    }

    @Test
    fun onlyAnHttpUrlWithATokenCanBeSaved() {
        assertTrue(viewModel.canSave("https://coffee.example.com", "dev-token"))
        assertTrue(viewModel.canSave("http://10.0.2.2:8080", "dev-token"))
        assertFalse(viewModel.canSave("coffee.example.com", "dev-token"))
        assertFalse(viewModel.canSave("ftp://coffee.example.com", "dev-token"))
        assertFalse(viewModel.canSave("https://coffee.example.com", "  "))
    }

    @Test
    fun invalidFieldsAreNotSaved() {
        assertFalse(viewModel.save("not a url", "dev-token"))
        assertFalse(settings.serverConfigured)
        assertEquals(0, resent)
    }

    @Test
    fun everyScenarioRowIsSavedOrRefused() = runTest {
        // The issue's table: from the URL to the saved state, through the same resolver the app uses.
        data class Row(
            val url: String,
            val addresses: List<String>,
            val unresolvable: Boolean,
            val saved: Boolean,
            val resolved: Boolean,
        )
        listOf(
            Row("http://192.168.1.10:8080", listOf("192.168.1.10"), false, saved = true, resolved = true),
            Row("http://nas.local", listOf("8.8.8.8"), false, saved = true, resolved = false),
            Row("http://nas:8080", listOf("192.168.1.20"), false, saved = true, resolved = true),
            Row("http://nas:8080", emptyList(), true, saved = false, resolved = true),
            Row("http://coffee.example.com", listOf("8.8.8.8"), false, saved = false, resolved = true),
            Row("https://coffee.example.com", emptyList(), true, saved = true, resolved = false),
        ).forEach { row ->
            settings.removeServer()
            addresses = row.addresses
            unresolvable = row.unresolvable
            resolved.clear()

            val checkedOk = viewModel.checkHost(row.url, ServerCheck.Save)
            val saved = viewModel.save(row.url, "dev-token")

            assertEquals(row.url, row.saved, checkedOk)
            assertEquals(row.url, row.saved, saved)
            assertEquals(row.url, row.saved, settings.serverConfigured)
            assertEquals(row.url, row.resolved, resolved.isNotEmpty())
        }
    }

    @Test
    fun savingStoresTrimmedValuesAndResendsUnsyncedBrews() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertTrue(viewModel.checkHost(" https://coffee.example.com/ ", ServerCheck.Save))
        assertTrue(viewModel.save(" https://coffee.example.com/ ", " dev-token "))
        assertEquals("https://coffee.example.com/", settings.serverUrl)
        assertEquals("dev-token", settings.token)
        assertEquals(1, resent)
        assertTrue(viewModel.uiState.value.configured)
        assertTrue(viewModel.uiState.value.justSaved)
        assertTrue("https never resolves", resolved.isEmpty())

        viewModel.edited()
        assertFalse(viewModel.uiState.value.justSaved)
    }

    @Test
    fun aServerIsNotSavedWithoutTheHostCheckForThatUrl() = runTest {
        assertFalse(viewModel.save("http://coffee.example.com", "dev-token"))
        assertTrue(viewModel.checkHost("https://coffee.example.com", ServerCheck.Save))
        assertFalse("a check for another URL never approves this one", viewModel.save("http://coffee.example.com", "dev-token"))
        assertFalse(settings.serverConfigured)
        assertEquals(0, resent)
    }

    @Test
    fun removeTurnsUploadsOffAndCancelsThem() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.checkHost("https://coffee.example.com", ServerCheck.Save)
        viewModel.save("https://coffee.example.com", "dev-token")
        viewModel.remove()
        assertFalse(settings.serverConfigured)
        assertEquals(1, removed)
        assertFalse(viewModel.uiState.value.configured)
    }

    @Test
    fun checkingShowsWhileTheHostResolves() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        hostGate = CompletableDeferred()
        val check = launch { assertTrue(viewModel.checkHost("http://nas:8080", ServerCheck.Save)) }
        runCurrent()
        assertEquals(ServerCheck.Save, viewModel.uiState.value.checking)

        hostGate!!.complete(Unit)
        check.join()
        assertNull(viewModel.uiState.value.checking)
    }

    @Test
    fun editingDuringTheCheckDropsItsAnswer() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        addresses = listOf("8.8.8.8")
        hostGate = CompletableDeferred()
        val check = launch { assertFalse(viewModel.checkHost("http://coffee.example.com", ServerCheck.Save)) }
        runCurrent()
        viewModel.edited()
        hostGate!!.complete(Unit)
        check.join()

        assertNull(viewModel.uiState.value.checking)
        assertFalse("the old URL's answer is dropped", viewModel.uiState.value.urlRefused)
        assertFalse(viewModel.save("http://coffee.example.com", "dev-token"))
    }

    @Test
    fun testConnectionRunsThenSucceeds() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        answer = CompletableDeferred()
        assertTrue(viewModel.checkHost(" https://coffee.example.com ", ServerCheck.Test))
        assertTrue(viewModel.testConnection(" https://coffee.example.com ", "dev-token", localNetworkAllowed = true))
        assertEquals(TestResult(null), viewModel.uiState.value.testResult)
        answer!!.complete(Unit)
        assertEquals(TestResult(true), viewModel.uiState.value.testResult)
        assertEquals(listOf("https://coffee.example.com" to "dev-token"), checked)
        assertFalse("testing never saves", settings.serverConfigured)
    }

    @Test
    fun testConnectionShowsAWrongToken() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        checkFails = HttpStatusException(401, "HTTP 401")
        viewModel.checkHost("https://coffee.example.com", ServerCheck.Test)
        viewModel.testConnection("https://coffee.example.com", "wrong", localNetworkAllowed = true)
        assertEquals(TestResult(false, SyncError(Kind.UNAUTHORIZED)), viewModel.uiState.value.testResult)
    }

    @Test
    fun testConnectionFailsWithoutARequestWhenTheLocalNetworkIsBlocked() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertTrue(viewModel.checkHost("http://192.168.0.10:8080", ServerCheck.Test))
        viewModel.testConnection("http://192.168.0.10:8080", "dev-token", localNetworkAllowed = false)
        assertEquals(Kind.LOCAL_NETWORK_PERMISSION, viewModel.uiState.value.testResult?.error?.kind)

        viewModel.edited()
        assertFalse(viewModel.checkHost("nope", ServerCheck.Test))
        assertFalse(viewModel.testConnection("nope", "dev-token", localNetworkAllowed = true))
        assertNull(viewModel.uiState.value.testResult)
        assertEquals(emptyList<Pair<String, String>>(), checked)
    }

    @Test
    fun aPublicHttpHostIsRefusedBeforeAnyRequest() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        addresses = listOf("8.8.8.8")
        assertFalse(viewModel.checkHost("http://coffee.example.com", ServerCheck.Test))
        assertTrue(viewModel.uiState.value.urlRefused)
        assertFalse(viewModel.testConnection("http://coffee.example.com", "dev-token", localNetworkAllowed = true))
        assertTrue(viewModel.uiState.value.urlRefused)
        assertEquals(emptyList<Pair<String, String>>(), checked)
        assertNull(viewModel.uiState.value.testResult)
    }

    @Test
    fun editingClearsTheTestResult() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.checkHost("https://coffee.example.com", ServerCheck.Test)
        viewModel.testConnection("https://coffee.example.com", "dev-token", localNetworkAllowed = true)
        viewModel.edited()
        assertNull(viewModel.uiState.value.testResult)
    }
}
