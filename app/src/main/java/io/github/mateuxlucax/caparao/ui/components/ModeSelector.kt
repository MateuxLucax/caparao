package io.github.mateuxlucax.caparao.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The largest label the selectors use, and the floor that still reads on a 320 dp phone at 2× text. */
internal val ModeLabelMaxSize = 11.sp
internal val ModeLabelMinSize = 7.sp
private val ModeLabelStep = 0.5.sp

/**
 * The largest size at which every [labels] entry fits one line in [maxWidth], shared by the whole row so the buttons
 * keep one label size. Compact screens shrink the labels instead of wrapping them or bringing back a scroller.
 */
@Composable
internal fun rememberModeLabelSize(labels: List<String>, maxWidth: Dp, style: TextStyle): TextUnit {
    if (!maxWidth.value.isFinite()) return ModeLabelMaxSize
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widthPx = with(density) { maxWidth.toPx() }
    return remember(labels, widthPx, style, measurer) {
        var size = ModeLabelMaxSize
        while (size > ModeLabelMinSize) {
            val widest = labels.maxOf { label ->
                measurer.measure(
                    AnnotatedString(label),
                    style.copy(fontSize = size),
                    maxLines = 1,
                    softWrap = false,
                ).size.width
            }
            if (widest <= widthPx) break
            size = (size.value - ModeLabelStep.value).sp
        }
        size
    }
}

/**
 * One mode in a selector row (#125): an icon above a short, single-line label, a full capsule when selected. The spoken
 * name stays the full method, so TalkBack never has to guess what "Press" or "Manual" mean.
 */
@Composable
internal fun ModeToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    @DrawableRes icon: Int,
    @StringRes label: Int,
    @StringRes spokenName: Int,
    labelSize: TextUnit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shapes: ToggleButtonShapes,
) {
    val spoken = stringResource(spokenName)
    ToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.semantics {
            role = Role.RadioButton
            selected = checked
            contentDescription = spoken
        },
        enabled = enabled,
        shapes = shapes,
        contentPadding = PaddingValues(horizontal = 1.dp, vertical = 8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(painterResource(icon), contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(label),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = if (labelSize < ModeLabelMinSize) labelSize else ModeLabelMinSize,
                    maxFontSize = labelSize,
                    stepSize = ModeLabelStep,
                ),
            )
        }
    }
}
