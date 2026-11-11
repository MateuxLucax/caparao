package io.github.mateuxlucax.caparao

/** Installs the replay and benchmark hook before any app container can be built. */
class DebugCaparaoApplication : CaparaoApplication() {
    override val debugHooks: DebugHooks = DebugScaleHooks()
}
