package io.github.mateuxlucax.caparao.ui.settings

import io.github.mateuxlucax.caparao.data.exporter.HttpStatusException
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.model.SyncError
import io.github.mateuxlucax.caparao.model.SyncError.Kind
import io.github.mateuxlucax.caparao.ui.FakePreferences
import io.github.mateuxlucax.caparao.ui.MainDispatcherRule
import io.github.mateuxlucax.caparao.ui.components.TestResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ServerViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val settings = SettingsStore(FakePreferences())
    private val checked = mutableListOf<Pair<String, String>>()
    private var answer: CompletableDeferred<Unit>? = null
    private var checkFails: Exception? = null
    private var resent = 0
    private var removed = 0

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
    fun savingStoresTrimmedValuesAndResendsUnsyncedBrews() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertTrue(viewModel.save(" https://coffee.example.com/ ", " dev-token "))
        assertEquals("https://coffee.example.com/", settings.serverUrl)
        assertEquals("dev-token", settings.token)
        assertEquals(1, resent)
        assertTrue(viewModel.uiState.value.configured)
        assertTrue(viewModel.uiState.value.justSaved)

        viewModel.edited()
        assertFalse(viewModel.uiState.value.justSaved)
    }

    @Test
    fun removeTurnsUploadsOffAndCancelsThem() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.save("https://coffee.example.com", "dev-token")
        viewModel.remove()
        assertFalse(settings.serverConfigured)
        assertEquals(1, removed)
        assertFalse(viewModel.uiState.value.configured)
    }

    @Test
    fun testConnectionRunsThenSucceeds() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        answer = CompletableDeferred()
        viewModel.testConnection(" https://coffee.example.com ", "dev-token", localNetworkAllowed = true)
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
        viewModel.testConnection("https://coffee.example.com", "wrong", localNetworkAllowed = true)
        assertEquals(TestResult(false, SyncError(Kind.UNAUTHORIZED)), viewModel.uiState.value.testResult)
    }

    @Test
    fun testConnectionFailsWithoutARequestWhenTheLocalNetworkIsBlocked() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.testConnection("http://192.168.0.10:8080", "dev-token", localNetworkAllowed = false)
        assertEquals(Kind.LOCAL_NETWORK_PERMISSION, viewModel.uiState.value.testResult?.error?.kind)
        viewModel.testConnection("nope", "dev-token", localNetworkAllowed = true)
        assertEquals(Kind.INVALID_URL, viewModel.uiState.value.testResult?.error?.kind)
        assertEquals(emptyList<Pair<String, String>>(), checked)
    }

    @Test
    fun editingClearsTheTestResult() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.testConnection("https://coffee.example.com", "dev-token", localNetworkAllowed = true)
        viewModel.edited()
        assertNull(viewModel.uiState.value.testResult)
    }
}
