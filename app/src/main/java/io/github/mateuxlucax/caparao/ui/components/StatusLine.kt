package io.github.mateuxlucax.caparao.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.SyncError
import io.github.mateuxlucax.caparao.ui.format.message

/** A check or error icon (a spinner while [ok] is null and [busy]) next to [text], with an optional detail below. */
@Composable
fun StatusLine(ok: Boolean?, text: String, detail: String? = null, busy: Boolean = false) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            busy -> LoadingIndicator(Modifier.size(20.dp))
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

