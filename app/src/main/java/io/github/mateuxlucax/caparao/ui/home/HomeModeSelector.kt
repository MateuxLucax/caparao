package io.github.mateuxlucax.caparao.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.BrewMode

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

/** Four compact Home shortcuts; Manual is the scale screen, while the other three start brew modes. */
@Composable
internal fun HomeModeSelector(connected: Boolean, onBrew: (BrewMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 68.dp).height(IntrinsicSize.Max).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        homeModeOptions.forEachIndexed { index, option ->
            val spokenName = stringResource(option.spokenName)
            ToggleButton(
                checked = option.mode == null,
                onCheckedChange = { option.mode?.let { if (connected) onBrew(it) } },
                modifier = Modifier.weight(1f).fillMaxHeight().semantics {
                    role = Role.RadioButton
                    selected = option.mode == null
                    contentDescription = spokenName
                },
                enabled = option.mode == null || connected,
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    homeModeOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(option.icon), contentDescription = null, Modifier.size(20.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(option.label),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
