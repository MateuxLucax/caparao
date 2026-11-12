package dev.mateux.caparao.model

import kotlinx.serialization.Serializable

/** How a brew is recorded: filter (free pour-over), automatic espresso (#90), or French press (#81). */
@Serializable
enum class BrewMode { FILTER, ESPRESSO, FRENCH_PRESS }
