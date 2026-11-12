package dev.mateux.caparao.ui.components

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
import dev.mateux.caparao.model.BrewMode

/**
 * The brew type as one fixed, equal-height row of icon-and-label buttons (#109, #125). Only the three real brew modes
 * appear; Manual is the scale screen, not a brew mode. The selected segment is a full capsule, like Home's.
 */
@Composable
fun BrewModeGroup(selected: BrewMode, onSelect: (BrewMode) -> Unit, modifier: Modifier = Modifier) {
    val modes = BrewMode.entries
    val labels = modes.map { stringResource(it.shortLabel) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val textWidth = ((maxWidth - ButtonGroupDefaults.ConnectedSpaceBetween * (modes.size - 1)) / modes.size) - 2.dp
        val labelSize = rememberModeLabelSize(labels, textWidth, MaterialTheme.typography.labelSmall)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 68.dp).height(IntrinsicSize.Max).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            modes.forEachIndexed { index, mode ->
                ModeToggleButton(
                    checked = selected == mode,
                    onCheckedChange = { onSelect(mode) },
                    icon = mode.icon,
                    label = mode.shortLabel,
                    spokenName = mode.label,
                    labelSize = labelSize,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes(checkedShape = CircleShape)
                        modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes(checkedShape = CircleShape)
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes(checkedShape = CircleShape)
                    },
                )
            }
        }
    }
}
