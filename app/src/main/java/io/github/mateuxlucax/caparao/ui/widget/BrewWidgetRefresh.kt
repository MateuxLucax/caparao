package io.github.mateuxlucax.caparao.ui.widget

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

/** Keeps each local-data read and its widget publish in order across provider receiver instances. */
internal object BrewWidgetRefresh {
    /** A broadcast that holds `goAsync()` for about 10 s is a background ANR, so finish well before it. */
    const val TIMEOUT_MS = 8_000L

    private val mutex = Mutex()
    private val rerunRequested = AtomicBoolean(false)

    enum class Outcome {
        /** The read and its publish ran; the widget now shows this snapshot. */
        COMPLETED,

        /** Another refresh owns the widget and folds this request into one extra read. */
        COALESCED,

        /** The read did not finish in time; nothing was published and the previous content stays. */
        TIMED_OUT,
    }

    /**
     * Runs [read] and [publish] under the process-wide lock, bounded by [TIMEOUT_MS].
     *
     * A request that arrives while another refresh holds the lock is dropped as [Outcome.COALESCED] and asks the owner
     * for one more read, so a burst of writes costs at most two reads instead of one read per write. The whole read and
     * publish loop shares one timeout, so a slow first database open cannot extend a broadcast past its budget.
     */
    suspend fun <T> run(
        read: suspend () -> T,
        publish: (T) -> Unit,
    ): Outcome {
        if (!mutex.tryLock()) {
            rerunRequested.set(true)
            return Outcome.COALESCED
        }
        try {
            val finished = withTimeoutOrNull(TIMEOUT_MS) {
                publish(read())
                if (rerunRequested.getAndSet(false)) publish(read())
                true
            }
            return if (finished == true) Outcome.COMPLETED else Outcome.TIMED_OUT
        } finally {
            rerunRequested.set(false)
            mutex.unlock()
        }
    }
}
