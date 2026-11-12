package io.github.mateuxlucax.caparao.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.data.local.SyncStatus
import io.github.mateuxlucax.caparao.data.network.LocalNetwork
import io.github.mateuxlucax.caparao.data.network.ServerUrl
import io.github.mateuxlucax.caparao.ui.server.ConnectionTest
import io.github.mateuxlucax.caparao.ui.server.TestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Which button started a host check, so it can show [ServerUiState.checking] while it runs. */
enum class ServerCheck { Save, Test }

data class ServerUiState(
    /** A URL and token are saved: uploads are on. */
    val configured: Boolean = false,
    val testResult: TestResult? = null,
    /** Saved since the fields last changed. */
    val justSaved: Boolean = false,
    /** The button whose host check is running, if any. */
    val checking: ServerCheck? = null,
    /** Plain http to a host that isn't local: refused until the URL is edited. */
    val urlRefused: Boolean = false,
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
    /** LocalNetwork.isLocal: resolves the host, so it runs on IO. */
    private val localNetwork: suspend (String) -> Boolean = { withContext(Dispatchers.IO) { LocalNetwork.isLocal(it) } },
) : ViewModel() {
    val syncStatus: StateFlow<SyncStatus> = settings.syncStatus.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        settings.currentSyncStatus(),
    )
    private val connectionTest = ConnectionTest(viewModelScope, check)
    private val justSaved = MutableStateFlow(false)
    private val checking = MutableStateFlow<ServerCheck?>(null)
    private val urlRefused = MutableStateFlow(false)

    /** Bumped by [edited], so a check that was running for the old URL can't refuse the new one. */
    private var edits = 0

    /** The URL a check approved for the next save or test; cleared when a field is edited. */
    private var approvedUrl: String? = null

    val uiState: StateFlow<ServerUiState> =
        combine(settings.syncStatus, connectionTest.result, justSaved, checking, urlRefused) { sync, test, saved, check, refused ->
            ServerUiState(sync.serverConfigured, test, saved, check, refused)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerUiState(settings.serverConfigured))

    /** Saved values, to fill the form when it opens. The token goes only into the field. */
    val savedUrl: String get() = settings.serverUrl
    val savedToken: String get() = settings.token

    /** A valid http(s) URL and a token. */
    fun canSave(url: String, token: String) = ServerUrl.isValid(url) && token.isNotBlank()

    /**
     * The first step of Save and Test: https passes, plain http only when its host is local ([LocalNetwork.isLocal],
     * on IO). Shows [action] as checking while the host resolves and marks the URL refused when it isn't local; false
     * means nothing may be saved or sent. A host that doesn't resolve in time counts as not local.
     */
    suspend fun checkHost(url: String, action: ServerCheck): Boolean {
        val trimmed = url.trim()
        if (!ServerUrl.isValid(trimmed) || checking.value != null) return false
        val edit = edits
        checking.value = action
        urlRefused.value = false
        return try {
            val allowed = !ServerUrl.isCleartext(trimmed) ||
                withTimeoutOrNull(CHECK_TIMEOUT_MS) { localNetwork(trimmed) } == true
            if (edit == edits) {
                if (allowed) approvedUrl = trimmed else urlRefused.value = true
            }
            allowed
        } finally {
            checking.value = null
        }
    }

    /** Saves the server and sends every brew it doesn't have yet; false (nothing saved) until [checkHost] approved it. */
    fun save(url: String, token: String): Boolean {
        if (!canSave(url, token) || approvedUrl != url.trim()) return false
        approvedUrl = null
        settings.saveServer(url, token)
        justSaved.value = true
        viewModelScope.launch { resendUnsynced() }
        return true
    }

    /** Turns uploads off and cancels the pending ones. */
    fun remove() {
        edits++
        approvedUrl = null
        urlRefused.value = false
        settings.removeServer()
        serverRemoved()
        connectionTest.clear()
        justSaved.value = false
    }

    /** A field changed: the test result, "saved" and the host check were about the old values. */
    fun edited() {
        edits++
        approvedUrl = null
        urlRefused.value = false
        connectionTest.clear()
        justSaved.value = false
    }

    /** True when the server at [url] is on the local network and access isn't granted (Android 17+). */
    suspend fun isLocalNetworkBlocked(url: String): Boolean = localNetworkBlocked(url.trim())

    /** Shows the test as running while the screen settles local network access. */
    fun markTestRunning() = connectionTest.markRunning()

    /** Starts the test; false (nothing sent) until [checkHost] approved [url]. */
    fun testConnection(url: String, token: String, localNetworkAllowed: Boolean): Boolean {
        if (approvedUrl != url.trim()) return false
        approvedUrl = null
        connectionTest.run(url, token, localNetworkAllowed)
        return true
    }

    fun clearTest() = connectionTest.clear()

    private companion object {
        /** A name that takes longer than this to resolve is treated as not local. */
        const val CHECK_TIMEOUT_MS = 5_000L
    }
}
