package io.github.mateuxlucax.caparao.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath

/**
 * A Material shape behind [content] that springs into the shape of [index] (docs/adr/0025-material-3-expressive.md):
 * from a circle when it first shows, from the previous shape when [index] changes. With animations off, it just is.
 */
@Composable
fun MorphingShape(
    index: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val still = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    var from by remember { mutableStateOf(if (still) shapeAt(index) else MaterialShapes.Circle) }
    var to by remember { mutableStateOf(shapeAt(index)) }
    val progress = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(index) {
        val next = shapeAt(index)
        if (next != to) {
            from = to
            to = next
            progress.snapTo(0f)
        }
        if (still) progress.snapTo(1f) else progress.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    }
    val morph = remember(from, to) { Morph(from.normalized(), to.normalized()) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val path = morph.toPath(progress.value).asComposePath()
            scale(size.width, size.height, pivot = Offset.Zero) { drawPath(path, color) }
        }
        content()
    }
}

private val Shapes = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Sunny,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.SoftBurst,
    MaterialShapes.Gem,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Pentagon,
)

private fun shapeAt(index: Int) = Shapes[index.mod(Shapes.size)]
