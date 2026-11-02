package io.github.mateuxlucax.caparao.ui.widget

import kotlinx.coroutines.sync.Mutex

/** Keeps each local-data read and its widget publish in order across provider receiver instances. */
internal object BrewWidgetRefresh {
    private val mutex = Mutex()

    suspend fun <T> run(read: suspend () -> T, publish: (T) -> Unit) {
        mutex.lock()
        try {
            publish(read())
        } finally {
            mutex.unlock()
        }
    }
}
