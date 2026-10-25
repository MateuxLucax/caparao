package io.github.mateuxlucax.caparao.ui.format

import androidx.annotation.StringRes
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.SyncError.Kind

/** Message shown for this kind of error; the technical detail, when there is one, is shown below it. */
@get:StringRes
val Kind.message: Int
    get() = when (this) {
        Kind.UNAUTHORIZED -> R.string.error_unauthorized
        Kind.REJECTED -> R.string.error_rejected
        Kind.REDIRECTED -> R.string.error_redirected
        Kind.SERVER -> R.string.error_server
        Kind.NETWORK -> R.string.error_network
        Kind.INVALID_URL -> R.string.error_invalid_url
        Kind.LOCAL_NETWORK_PERMISSION -> R.string.error_local_network
        Kind.OTHER -> R.string.error_other
    }
