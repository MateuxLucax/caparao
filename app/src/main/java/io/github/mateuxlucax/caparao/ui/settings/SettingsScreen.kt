package io.github.mateuxlucax.caparao.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.ui.components.BodyText
import io.github.mateuxlucax.caparao.ui.components.SectionCard

/** Server, permissions and scale, in the wireframe's order. The controls arrive with stages 2 and 5. */
@Composable
fun SettingsScreen(versionName: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(stringResource(R.string.section_server)) { BodyText(stringResource(R.string.section_server_text)) }
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
