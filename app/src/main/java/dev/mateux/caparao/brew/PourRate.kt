package dev.mateux.caparao.brew

/** The optional target band shown alongside live pour flow. */
object PourRate {
    /** The ideal flow band, with a quarter's tolerance either side. */
    fun band(target: Double) = target * 0.75..target * 1.25
}
