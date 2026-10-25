package io.github.mateuxlucax.caparao.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.network.ServerUrl
import io.github.mateuxlucax.caparao.ui.components.ConnectionTest
import io.github.mateuxlucax.caparao.ui.components.TestResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ServerUiState(
    /** A URL and token are saved: uploads are on. */
    val configured: Boolean = false,
    val testResult: TestResult? = null,
    /** Saved since the fields last changed. */
    val justSaved: Boolean = false,
)

/**
 * The server form, used by the onboarding server step and the Settings tab (one instance each). The fields are UI
 * element state, kept by the screen and passed in; this holds what is saved and the connection test.
 */
class ServerViewModel(
    private val settings: SettingsStore,
    /** ExporterClient.check. */
    check: suspend (url: String, token: String) -> Unit,
    /** Enqueues every unsynced brew (UploadScheduler.resendUnsynced). */
    private val resendUnsynced: suspend () -> Unit,
    /** Cancels pending uploads (UploadScheduler.serverRemoved). */
    private val serverRemoved: () -> Unit,
    /** Whether Android 17 local network protection would block connecting to a URL. */
    private val localNetworkBlocked: suspend (String) -> Boolean = { false },
) : ViewModel() {
    private val connectionTest = ConnectionTest(viewModelScope, check)
    private val justSaved = MutableStateFlow(false)

    val uiState: StateFlow<ServerUiState> =
        combine(settings.syncStatus, connectionTest.result, justSaved) { sync, test, saved ->
            ServerUiState(sync.serverConfigured, test, saved)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerUiState(settings.serverConfigured))

    /** Saved values, to fill the form when it opens. The token goes only into the field. */
    val savedUrl: String get() = settings.serverUrl
    val savedToken: String get() = settings.token

    /** A valid http(s) URL and a token. */
    fun canSave(url: String, token: String) = ServerUrl.isValid(url) && token.isNotBlank()

    /** Saves the server and sends every brew it doesn't have yet; false (nothing saved) when the fields are not valid. */
    fun save(url: String, token: String): Boolean {
        if (!canSave(url, token)) return false
        settings.saveServer(url, token)
        justSaved.value = true
        viewModelScope.launch { resendUnsynced() }
        return true
    }

    /** Turns uploads off and cancels the pending ones. */
    fun remove() {
        settings.removeServer()
        serverRemoved()
        connectionTest.clear()
        justSaved.value = false
    }

    /** A field changed: the test result and "saved" were about the old values. */
    fun edited() {
        connectionTest.clear()
        justSaved.value = false
    }

    /** True when the server at [url] is on the local network and access isn't granted (Android 17+). */
    suspend fun isLocalNetworkBlocked(url: String): Boolean = localNetworkBlocked(url.trim())

    /** Shows the test as running while the screen settles local network access. */
    fun markTestRunning() = connectionTest.markRunning()

    fun testConnection(url: String, token: String, localNetworkAllowed: Boolean) = connectionTest.run(url, token, localNetworkAllowed)

    fun clearTest() = connectionTest.clear()
}
