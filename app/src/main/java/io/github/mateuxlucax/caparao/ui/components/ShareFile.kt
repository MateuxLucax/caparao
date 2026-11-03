package io.github.mateuxlucax.caparao.ui.components

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.data.export.ExportedFile

/** Opens the share sheet for [file] once, then calls [onShared]. */
@Composable
fun ShareFile(file: ExportedFile?, onShared: () -> Unit, title: Int = R.string.share_export) {
    val context = LocalContext.current
    val chooserTitle = stringResource(title)
    LaunchedEffect(file) {
        val exported = file ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).apply {
            type = exported.mimeType
            putExtra(Intent.EXTRA_STREAM, exported.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle))
        onShared()
    }
}
