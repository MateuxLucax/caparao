package io.github.mateuxlucax.caparao.model

/**
 * Why an upload (or a connection test) failed: a stable [kind] plus technical [detail]. The UI turns the kind into
 * text (ui/format/SyncErrorText.kt); exceptions are mapped to kinds in sync/SyncErrors.kt.
 */
data class SyncError(val kind: Kind, val detail: String? = null) {

    /** [retry] says whether trying again later can help (the response table in contract/README.md). */
    enum class Kind(val retry: Boolean) {
        /** 401: the token is wrong. */
        UNAUTHORIZED(false),
        /** Any other 4xx: the server refused this request (bad body, too large, wrong path). */
        REJECTED(false),
        /** 3xx: wrong URL, or http where the server wants https. Never followed, so the token stays put. */
        REDIRECTED(false),
        /** 5xx. */
        SERVER(true),
        /** Unreachable, refused or timed out. */
        NETWORK(true),
        INVALID_URL(false),
        /** Android 17+ blocks the local server until the user allows local network access (docs/adr/0017). */
        LOCAL_NETWORK_PERMISSION(false),
        OTHER(true),
    }
}

/** A problem the user must fix in the app (URL, permission); retrying won't help. */
class SetupException(val kind: SyncError.Kind) : Exception(kind.name)
