package io.github.mateuxlucax.caparao.watch

/** Message paths and capabilities on the Wearable Data Layer. Messages carry no payload except [SNAPSHOT] and [ALERT]. */
object WatchPaths {
    /** Phone to watch: a [WatchSnapshot]. */
    const val SNAPSHOT = "/caparao/snapshot"

    /** Phone to watch: vibrate for a [WatchAlert]. */
    const val ALERT = "/caparao/alert"

    /** Watch to phone: tare the scale. */
    const val TARE = "/caparao/tare"

    /** Watch to phone: start or pause the scale's timer, as Home's button does. */
    const val TIMER_TOGGLE = "/caparao/timer/toggle"

    /** Watch to phone: reset the scale's timer. */
    const val TIMER_RESET = "/caparao/timer/reset"

    /** Declared by the watch app (res/values/wear.xml): where the phone sends snapshots. */
    const val WATCH_CAPABILITY = "caparao_watch"

    /** Declared by the phone app: where the watch sends commands. */
    const val PHONE_CAPABILITY = "caparao_phone"

    /** Opens the watch app (RemoteActivityHelper from the phone, intent filter on the watch). */
    const val OPEN_URI = "caparao://watch"
}
