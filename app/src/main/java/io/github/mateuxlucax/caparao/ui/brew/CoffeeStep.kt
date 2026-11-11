package io.github.mateuxlucax.caparao.ui.brew

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.ui.components.wholeGrams
import io.github.mateuxlucax.caparao.ui.theme.Motion
import io.github.mateuxlucax.caparao.ui.theme.animationsOff

/**
 * Which coffee the brew uses, before weighing the beans (#291): each open bag with what is left and how long since the
 * roast, or No bag. A bag with less than the dose left says so, without blocking. Next goes on to the beans; the scale
 * is needed only from there, so the step says when it is still connecting.
 */
@Composable
fun CoffeeStep(state: BrewUiState, onSelect: (String?) -> Unit, onNext: () -> Unit, onRetryScale: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val now = remember { System.currentTimeMillis() }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GuideHeader(GuideStage.COFFEE)
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(top = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.bags.forEach { bag ->
                val details = buildList {
                    add(stringResource(R.string.coffee_left, wholeGrams(bag.leftGrams, locale), wholeGrams(bag.weightGrams, locale)))
                    bag.daysSinceRoast(now)?.let { days -> add(pluralStringResource(R.plurals.coffee_roasted_days, days.toInt(), days.toInt())) }
                }
                val short = state.dose?.let { bag.leftGrams < it } == true
                CoffeeChoice(
                    name = bag.name,
                    selected = state.bagId == bag.id,
                    onClick = { onSelect(bag.id) },
                    details = details,
                    warning = if (short) stringResource(R.string.coffee_below_dose) else null,
                    testTag = "coffee-${bag.id}",
                )
            }
            CoffeeChoice(name = stringResource(R.string.no_bag), selected = state.bagId == null, onClick = { onSelect(null) }, testTag = "coffee-none")
        }
        ScaleConnection(state, onRetryScale, Modifier.padding(bottom = 8.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.testTag("coffee-step-next").fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
            shapes = ButtonDefaults.shapes(),
            enabled = !state.waitingForScale,
        ) {
            Text(stringResource(R.string.next), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** One choice of the coffee step: a radio row on a card, tinted when it is the one picked. */
@Composable
private fun CoffeeChoice(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    details: List<String> = emptyList(),
    warning: String? = null,
) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        Motion.standardOrSnap<Color>(animationsOff()),
        label = "coffeeChoice",
    )
    Card(
        Modifier.fillMaxWidth().testTag(testTag).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            details.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            warning?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        }
    }
}
