package io.github.mateuxlucax.caparao.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.theme.Motion
import io.github.mateuxlucax.caparao.ui.theme.animatedFlex
import io.github.mateuxlucax.caparao.ui.theme.animationsOff

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/**
 * Yield, time, ratio, dose and flow of a saved brew, in a card (French press has no average flow). Shared with the brew detail. [countUp] counts the numbers
 * up from zero on the save screen (#198); with animations off they show at once.
 */
@Composable
fun BrewNumbers(brew: Brew, modifier: Modifier = Modifier, countUp: Boolean = false) {
    val locale = LocalConfiguration.current.locales[0]
    val g = stringResource(R.string.unit_grams)
    val flowUnit = stringResource(R.string.unit_grams_per_second)
    Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Metric(
                    stringResource(
                        when (brew.mode) {
                            BrewMode.ESPRESSO -> R.string.label_yield
                            BrewMode.FILTER, BrewMode.FRENCH_PRESS -> R.string.label_water
                        },
                    ),
                    countedText(brew.yieldGrams, countUp) { "${Format.grams(it, locale)} $g" },
                    Modifier.weight(1f),
                    large = true,
                )
                Metric(
                    stringResource(R.string.label_time),
                    countedText(brew.durationMs / 1000.0, countUp) { Format.timer(it.toInt()) },
                    Modifier.weight(1f),
                    large = true,
                )
            }
            Row(Modifier.fillMaxWidth()) {
                Metric(stringResource(R.string.label_dose), countedText(brew.doseGrams, countUp) { "${Format.grams(it, locale)} $g" }, Modifier.weight(1f))
                Metric(
                    stringResource(R.string.label_ratio),
                    countedText(brew.ratio, countUp) { stringResource(R.string.ratio_value, Format.ratio(it, locale)) },
                    Modifier.weight(1f),
                )
            }
            // French press steeps with no flow, so its average over the whole time says nothing (#224); the upload keeps the field.
            if (brew.mode != BrewMode.FRENCH_PRESS) {
                Row(Modifier.fillMaxWidth()) {
                    Metric(
                        stringResource(R.string.label_flow_avg),
                        countedText(brew.flowAvgGramsPerSecond, countUp) { "${Format.flow(it, locale)} $flowUnit" },
                        Modifier.weight(1f),
                    )
                    brew.timeToFirstDropMs?.let {
                        Metric(
                            stringResource(R.string.label_first_drop),
                            countedText(it / 1000.0, countUp) { Format.timer(it.toInt()) },
                            Modifier.weight(1f),
                        )
                    } ?: Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * A number that counts up from zero to [target] when [countUp] is set (#198). With animations off, or without the count,
 * it is the final value at once, so the information is on screen either way.
 */
@Composable
private fun countedText(target: Double, countUp: Boolean, format: @Composable (Double) -> String): String {
    val still = animationsOff()
    if (!countUp || still) return format(target)
    // The progress runs 0 to 1 and scales the Double target, so the count never goes through Float: the last text is exact.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(COUNT_UP_MS, easing = Motion.EmphasizedDecelerate))
    }
    return if (progress.value >= 1f) format(target) else format(target * progress.value)
}

private const val COUNT_UP_MS = 800

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier, large: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (large) {
            // The result opens light and narrow and springs to heavy and wide in Roboto Flex.
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { shown = true }
            Text(value, style = MaterialTheme.typography.displaySmall.merge(animatedFlex(if (shown) 760 else 200, if (shown) 115 else 70)), maxLines = 1)
        } else {
            Text(value, style = MaterialTheme.typography.titleLarge.merge(Tabular))
        }
    }
}
