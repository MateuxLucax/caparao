package dev.mateux.caparao.ui.format

import androidx.annotation.StringRes
import dev.mateux.caparao.R
import dev.mateux.caparao.model.ScaleError
import dev.mateux.caparao.model.ScaleState

/** One line describing the connection, for the status row. */
@get:StringRes
val ScaleState.label: Int
    get() = when (this) {
        ScaleState.Idle -> R.string.scale_disconnected
        ScaleState.Searching -> R.string.scale_searching
        ScaleState.Connecting -> R.string.scale_connecting
        is ScaleState.Connected -> R.string.scale_connected
        is ScaleState.Failed -> error.title
    }

@get:StringRes
val ScaleError.title: Int
    get() = when (this) {
        ScaleError.BLUETOOTH_OFF -> R.string.error_bluetooth_off
        ScaleError.PERMISSION_MISSING -> R.string.error_permission_missing
        ScaleError.NOT_FOUND -> R.string.error_not_found
        ScaleError.PAIRED_ELSEWHERE -> R.string.error_paired_elsewhere
        ScaleError.CONNECTION_LOST -> R.string.error_connection_lost
    }

/** What to do about it. */
@get:StringRes
val ScaleError.hint: Int
    get() = when (this) {
        ScaleError.BLUETOOTH_OFF -> R.string.hint_bluetooth_off
        ScaleError.PERMISSION_MISSING -> R.string.hint_permission_missing
        ScaleError.NOT_FOUND -> R.string.hint_not_found
        ScaleError.PAIRED_ELSEWHERE -> R.string.hint_paired_elsewhere
        ScaleError.CONNECTION_LOST -> R.string.hint_connection_lost
    }
