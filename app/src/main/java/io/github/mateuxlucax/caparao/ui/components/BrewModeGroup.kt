package io.github.mateuxlucax.caparao.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import io.github.mateuxlucax.caparao.model.BrewMode

/** The brew type, espresso or filter, as a connected button group (docs/adr/0025-material-3-expressive.md). */
@Composable
fun BrewModeGroup(selected: BrewMode, onSelect: (BrewMode) -> Unit, modifier: Modifier = Modifier, icons: Boolean = false) {
    val last = BrewMode.entries.lastIndex
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        BrewMode.entries.forEachIndexed { index, mode ->
            ToggleButton(
                checked = selected == mode,
                onCheckedChange = { onSelect(mode) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    last -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                if (icons) {
                    Icon(painterResource(mode.icon), contentDescription = null, Modifier.size(ToggleButtonDefaults.IconSize))
                    Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                }
                Text(stringResource(mode.label))
            }
        }
    }
}
