package io.github.mateuxlucax.caparao.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.data.network.ServerUrl
import io.github.mateuxlucax.caparao.model.SyncError
import io.github.mateuxlucax.caparao.ui.AppActions
import io.github.mateuxlucax.caparao.ui.format.message
import io.github.mateuxlucax.caparao.ui.settings.ServerViewModel
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
        )
        LocalNetworkNotice(url, viewModel::isLocalNetworkBlocked, actions)
        OutlinedButton(
            onClick = { scope.launchConnectionTest(viewModel, url, token, localNetworkAccess) },
            enabled = viewModel.canSave(url, token),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.test_connection)) }
        TestResultLine(state.testResult, actions.openAppSettings)
    }
}

@Composable
private fun ServerFields(url: String, onUrlChange: (String) -> Unit, token: String, onTokenChange: (String) -> Unit) {
    var showToken by rememberSaveable { mutableStateOf(false) }
    val problem = when {
        url.isNotBlank() && !ServerUrl.isValid(url) -> stringResource(R.string.error_invalid_url)
        ServerUrl.isCleartextToPublicHost(url) -> stringResource(R.string.url_cleartext_warning)
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
    )
    OutlinedTextField(
        value = token,
        onValueChange = onTokenChange,
        label = { Text(stringResource(R.string.server_token)) },
        singleLine = true,
        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        trailingIcon = {
            TextButton(onClick = { showToken = !showToken }) {
                Text(stringResource(if (showToken) R.string.hide_token else R.string.show_token))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A check or error icon (a spinner while [ok] is null and [busy]) next to [text], with an optional detail below. */
@Composable
fun StatusLine(ok: Boolean?, text: String, detail: String? = null, busy: Boolean = false) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            ok == true -> Icon(painterResource(R.drawable.ic_check_circle), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            else -> Icon(painterResource(R.drawable.ic_error), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (!detail.isNullOrBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** [error] as a status line: its message and the technical detail. */
@Composable
fun SyncErrorLine(error: SyncError) = StatusLine(false, stringResource(error.kind.message), error.detail)

@Composable
private fun TestResultLine(result: TestResult?, onOpenSettings: () -> Unit) {
    if (result == null) return
    val error = result.error
    when {
        result.ok == null -> StatusLine(null, stringResource(R.string.test_running), busy = true)
        result.ok -> StatusLine(true, stringResource(R.string.test_ok))
        error?.kind == SyncError.Kind.LOCAL_NETWORK_PERMISSION -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusLine(false, stringResource(R.string.local_network_denied))
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.open_app_settings)) }
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
 * Starts "Test connection" with [url] and [token]. Local network access is settled here first, asking for the
 * permission if needed, and the ViewModel gets only the answer, so no ViewModel coroutine holds the activity.
 */
private fun CoroutineScope.launchConnectionTest(
    viewModel: ServerViewModel,
    url: String,
    token: String,
    localNetworkAccess: suspend (String) -> Boolean,
) {
    viewModel.markTestRunning()
    launch {
        var started = false
        try {
            val allowed = localNetworkAccess(url.trim())
            viewModel.testConnection(url, token, allowed)
            started = true
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
        OutlinedButton(onClick = { scope.launch { actions.requestLocalNetwork(); checks++ } }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.local_network_allow))
        }
    }
}
