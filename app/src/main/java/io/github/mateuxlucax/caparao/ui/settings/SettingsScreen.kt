package io.github.mateuxlucax.caparao.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.ui.AppActions
import io.github.mateuxlucax.caparao.ui.components.BodyText
import io.github.mateuxlucax.caparao.ui.components.SectionCard
import io.github.mateuxlucax.caparao.ui.components.ServerForm
import io.github.mateuxlucax.caparao.ui.components.StatusLine
import io.github.mateuxlucax.caparao.ui.components.rememberLocalNetworkAccess
import kotlinx.coroutines.launch

/** Server, permissions and scale, in the wireframe's order. */
@Composable
fun SettingsScreen(server: ServerViewModel, actions: AppActions, versionName: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(stringResource(R.string.section_server)) { ServerSection(server, actions) }
        SectionCard(stringResource(R.string.section_permissions)) { BodyText(stringResource(R.string.section_permissions_text)) }
        SectionCard(stringResource(R.string.section_scale)) { BodyText(stringResource(R.string.section_scale_text)) }
        Text(
            stringResource(R.string.version, versionName),
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The editable server: the shared form, Save and Remove server. */
@Composable
private fun ServerSection(viewModel: ServerViewModel, actions: AppActions) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf(viewModel.savedUrl) }
    var token by rememberSaveable { mutableStateOf(viewModel.savedToken) }
    val scope = rememberCoroutineScope()
    val localNetworkAccess = rememberLocalNetworkAccess(viewModel::isLocalNetworkBlocked, actions)
    val changed = url.trim() != viewModel.savedUrl || token.trim() != viewModel.savedToken
    BodyText(stringResource(R.string.section_server_text))
    ServerForm(viewModel, url, { url = it }, token, { token = it }, actions)
    if (state.justSaved) StatusLine(true, stringResource(R.string.server_saved))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        if (state.configured) {
            TextButton(onClick = {
                viewModel.remove()
                url = ""
                token = ""
            }) { Text(stringResource(R.string.remove_server)) }
        } else {
            Spacer(Modifier.width(1.dp))
        }
        Button(
            onClick = {
                // Ask for local network access now, while the user is here; the worker can't ask.
                scope.launch {
                    localNetworkAccess(url.trim())
                    viewModel.save(url, token)
                }
            },
            enabled = changed && viewModel.canSave(url, token),
        ) { Text(stringResource(R.string.save)) }
    }
}
