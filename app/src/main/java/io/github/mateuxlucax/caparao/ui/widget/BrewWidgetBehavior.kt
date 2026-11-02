package io.github.mateuxlucax.caparao.ui.widget

import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.ui.components.icon
import io.github.mateuxlucax.caparao.ui.launcher.LauncherShortcut
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The widget alternates Espresso and Filter; any future mode (including French press) suggests Espresso. */
internal fun suggestedModeAfter(latestMode: BrewMode): BrewMode =
    if (latestMode == BrewMode.ESPRESSO) BrewMode.FILTER else BrewMode.ESPRESSO

/** The action icon matches the suggested mode; the empty widget opens the chooser and has no method icon. */
internal fun actionIconForWidget(latestMode: BrewMode?): Int? =
    latestMode?.let(::suggestedModeAfter)?.icon

/** The widget's action always uses the same action MainActivity routes for launcher shortcuts. */
internal fun shortcutForWidget(latestMode: BrewMode?): LauncherShortcut =
    latestMode?.let { LauncherShortcut.forMode(suggestedModeAfter(it)) } ?: LauncherShortcut.WIDGET_HOME

internal sealed interface BrewAge {
    data object Today : BrewAge
    data object Yesterday : BrewAge
    data class DaysAgo(val days: Int) : BrewAge
}

/** A coarse local-calendar age for a saved brew's end time; the widget makes no minute-level promise. */
internal fun brewAge(endAtMs: Long, nowMs: Long, zone: ZoneId): BrewAge {
    val endDate = Instant.ofEpochMilli(endAtMs).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(endDate, today)
    return when {
        days <= 0 -> BrewAge.Today
        days == 1L -> BrewAge.Yesterday
        else -> BrewAge.DaysAgo(days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }
}

/** The widget treats a brew as ending at its saved start plus its recorded duration. */
internal fun brewAge(brew: Brew, nowMs: Long, zone: ZoneId): BrewAge =
    brewAge(brew.startedAt + brew.durationMs, nowMs, zone)
