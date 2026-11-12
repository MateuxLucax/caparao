package dev.mateux.caparao.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mateux.caparao.R
import dev.mateux.caparao.data.network.ServerUrl
import dev.mateux.caparao.model.SyncError
import dev.mateux.caparao.ui.components.AppActions
import dev.mateux.caparao.ui.components.StatusLine
import dev.mateux.caparao.ui.components.SyncErrorLine
import dev.mateux.caparao.ui.format.message
import dev.mateux.caparao.ui.server.ServerCheck
import dev.mateux.caparao.ui.server.ServerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** An example only: real addresses never go into the code (the repository is public). */
private const val URL_PLACEHOLDER = "https://coffee.example.com"

/**
 * The server form shared by the onboarding server step and Settings: URL and token fields, the local network notice,
 * "Test connection" and its result. [url] and [token] are kept by the caller, which adds its own buttons below.
 */
@Composable
fun ServerForm(
    viewModel: ServerViewModel,
    url: String,
    onUrlChange: (String) -> Unit,
    token: String,
    onTokenChange: (String) -> Unit,
    actions: AppActions,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val localNetworkAccess = rememberLocalNetworkAccess(viewModel::isLocalNetworkBlocked, actions)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ServerFields(
            url, { onUrlChange(it); viewModel.edited() },
            token, { onTokenChange(it); viewModel.edited() },
            refused = state.urlRefused,
        )
        LocalNetworkNotice(url, viewModel::isLocalNetworkBlocked, actions)
        OutlinedButton(
            shapes = ButtonDefaults.shapes(),
            onClick = { scope.launchConnectionTest(viewModel, url, token, localNetworkAccess) },
            enabled = viewModel.canSave(url, token) && state.checking == null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (state.checking == ServerCheck.Test) R.string.server_checking else R.string.test_connection)) }
        TestResultLine(state.testResult, actions.openAppSettings)
    }
}

@Composable
private fun ServerFields(
    url: String,
    onUrlChange: (String) -> Unit,
    token: String,
    onTokenChange: (String) -> Unit,
    refused: Boolean,
) {
    var showToken by rememberSaveable { mutableStateOf(false) }
    val problem = when {
        url.isNotBlank() && !ServerUrl.isValid(url) -> stringResource(R.string.error_invalid_url)
        refused -> stringResource(R.string.url_http_not_local)
        else -> null
    }
    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text(stringResource(R.string.server_url)) },
        placeholder = { Text(URL_PLACEHOLDER) },
        isError = problem != null,
        supportingText = problem?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    )
    OutlinedTextField(
        value = token,
        onValueChange = onTokenChange,
        label = { Text(stringResource(R.string.server_token)) },
        singleLine = true,
        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        trailingIcon = {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = { showToken = !showToken }) {
                Text(stringResource(if (showToken) R.string.hide_token else R.string.show_token))
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
private fun TestResultLine(result: TestResult?, onOpenSettings: () -> Unit) {
    if (result == null) return
    val error = result.error
    when {
        result.ok == null -> StatusLine(null, stringResource(R.string.test_running), busy = true)
        result.ok -> StatusLine(true, stringResource(R.string.test_ok))
        error?.kind == SyncError.Kind.LOCAL_NETWORK_PERMISSION -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusLine(false, stringResource(R.string.local_network_denied))
            OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.open_app_settings)) }
        }
        error != null -> SyncErrorLine(error)
    }
}

/**
 * For the connection test and saving: true when the server at the URL is reachable, i.e. [isBlocked] says it isn't
 * blocked (not local, or access granted), or the user grants local network access now. Runs in the caller's
 * composition scope, the only place that may hold [actions] (bound to the activity).
 */
@Composable
fun rememberLocalNetworkAccess(isBlocked: suspend (String) -> Boolean, actions: AppActions): suspend (String) -> Boolean =
    remember<suspend (String) -> Boolean>(isBlocked, actions) {
        { url -> !isBlocked(url) || actions.requestLocalNetwork() }
    }

/**
 * Starts "Test connection" with [url] and [token]. The host is checked first, so plain http to a non-local server is
 * refused before the token is sent. Local network access is settled here next, asking for the permission if needed,
 * and the ViewModel gets only the answer, so no ViewModel coroutine holds the activity.
 */
private fun CoroutineScope.launchConnectionTest(
    viewModel: ServerViewModel,
    url: String,
    token: String,
    localNetworkAccess: suspend (String) -> Boolean,
) {
    launch {
        if (!viewModel.checkHost(url, ServerCheck.Test)) return@launch
        viewModel.markTestRunning()
        var started = false
        try {
            val allowed = localNetworkAccess(url.trim())
            started = viewModel.testConnection(url, token, allowed)
        } finally {
            // The screen went away (e.g. rotated) during the prompt: don't leave "running" behind.
            if (!started) viewModel.clearTest()
        }
    }
}

/** On Android 17+, explains that the local server at [url] needs the local network permission, until granted. */
@Composable
private fun LocalNetworkNotice(url: String, isBlocked: suspend (String) -> Boolean, actions: AppActions) {
    val scope = rememberCoroutineScope()
    // Bumped when the app resumes, e.g. after the permission dialog or the app's settings: check again.
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose {}
    }
    val blocked by produceState(false, url, checks) {
        if (!ServerUrl.isValid(url)) {
            value = false
            return@produceState
        }
        delay(500) // a pause in typing
        value = isBlocked(url)
    }
    if (!blocked) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusLine(null, stringResource(R.string.local_network_needed))
        OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { scope.launch { actions.requestLocalNetwork(); checks++ } }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.local_network_allow))
        }
    }
}
