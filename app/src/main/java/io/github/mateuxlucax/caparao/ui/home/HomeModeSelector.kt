package io.github.mateuxlucax.caparao.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.ui.components.ModeToggleButton
import io.github.mateuxlucax.caparao.ui.components.rememberModeLabelSize

private data class HomeModeOption(
    val mode: BrewMode?,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    @StringRes val spokenName: Int,
)

private val homeModeOptions = listOf(
    HomeModeOption(null, R.drawable.ic_scale, R.string.mode_manual, R.string.home_mode_manual_accessibility),
    HomeModeOption(BrewMode.FILTER, R.drawable.ic_coffee_maker, R.string.mode_filter, R.string.mode_filter),
    HomeModeOption(BrewMode.ESPRESSO, R.drawable.ic_coffee, R.string.mode_espresso, R.string.mode_espresso),
    HomeModeOption(BrewMode.FRENCH_PRESS, R.drawable.ic_french_press, R.string.mode_french_press_short, R.string.mode_french_press),
)

/**
 * Four fixed Home shortcuts, one row of equal buttons: Manual is the scale screen, the other three start a brew mode.
 * Each is an icon above a one-line label and the selected one is a full capsule; the full method name stays spoken.
 */
@Composable
internal fun HomeModeSelector(connected: Boolean, onBrew: (BrewMode) -> Unit, modifier: Modifier = Modifier) {
    val labels = homeModeOptions.map { stringResource(it.label) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val textWidth = ((maxWidth - ButtonGroupDefaults.ConnectedSpaceBetween * (homeModeOptions.size - 1)) / homeModeOptions.size) - 2.dp
        val labelSize = rememberModeLabelSize(labels, textWidth, MaterialTheme.typography.labelSmall)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 68.dp).height(IntrinsicSize.Max).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            homeModeOptions.forEachIndexed { index, option ->
                ModeToggleButton(
                    checked = option.mode == null,
                    onCheckedChange = { option.mode?.let { if (connected) onBrew(it) } },
                    icon = option.icon,
                    label = option.label,
                    spokenName = option.spokenName,
                    labelSize = labelSize,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    enabled = option.mode == null || connected,
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes(checkedShape = CircleShape)
                        homeModeOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes(checkedShape = CircleShape)
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes(checkedShape = CircleShape)
                    },
                )
            }
        }
    }
}
