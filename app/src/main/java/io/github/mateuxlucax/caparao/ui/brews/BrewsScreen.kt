package io.github.mateuxlucax.caparao.ui.brews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.ui.components.EmptyState

/** Saved brews grouped by mode, with the last sync on top. Empty until brews are recorded (stage 3). */
@Composable
fun BrewsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.last_sync), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.server_not_configured),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EmptyState(
            R.drawable.ic_receipt_long,
            stringResource(R.string.brews_empty_title),
            stringResource(R.string.brews_empty_text),
            Modifier.padding(top = 48.dp),
        )
    }
}
