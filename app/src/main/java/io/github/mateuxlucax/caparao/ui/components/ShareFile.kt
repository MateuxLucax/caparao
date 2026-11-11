package io.github.mateuxlucax.caparao.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.mateuxlucax.caparao.R

/**
 * Opens the share sheet for [uri] once, then calls [onShared]. Plain values keep this component below the data layer:
 * the caller passes the exported file's URI and MIME type.
 */
@Composable
fun ShareFile(uri: Uri?, mimeType: String?, onShared: () -> Unit, title: Int = R.string.share_export) {
    val context = LocalContext.current
    val chooserTitle = stringResource(title)
    LaunchedEffect(uri) {
        val shared = uri ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, shared)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle))
        onShared()
    }
}
