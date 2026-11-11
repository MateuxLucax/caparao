package io.github.mateuxlucax.caparao.data.diagnostics

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Records every call for assertions; byte arrays are copied so a later mutation cannot change what was reported. */
class FakeDiagnostics : Diagnostics {
    val events = CopyOnWriteArrayList<DiagnosticEvent>()
    val notifications = CopyOnWriteArrayList<ByteArray>()
    val commands = CopyOnWriteArrayList<ByteArray>()
    val errors = CopyOnWriteArrayList<Pair<Throwable, DiagnosticErrorContext>>()
    val disconnects = AtomicInteger()

    override fun event(event: DiagnosticEvent) {
        events += event
    }

    override fun notification(bytes: ByteArray, classification: DiagnosticFrameClassification) {
        notifications += bytes.copyOf()
    }

    override fun command(bytes: ByteArray) {
        commands += bytes.copyOf()
    }

    override fun disconnect() {
        disconnects.incrementAndGet()
    }

    override fun error(throwable: Throwable, context: DiagnosticErrorContext) {
        errors += throwable to context
    }
}
