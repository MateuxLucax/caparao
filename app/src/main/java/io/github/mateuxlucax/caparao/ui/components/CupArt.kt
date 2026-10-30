package io.github.mateuxlucax.caparao.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath

/**
 * The app's cup and scale, drawn as solid filled shapes with no outlines, the way Material 3 Expressive draws its
 * pictures (docs/adr/0025-material-3-expressive.md). Shared by the welcome animation, the brew stages and the guided pour.
 */
object CupArt {
    /** A cup in [box]: straight sides tapering to a round bottom. */
    fun cup(box: Rect): Path = Path().apply {
        val w = box.width
        val r = w * 0.22f
        moveTo(box.left, box.top)
        lineTo(box.right, box.top)
        lineTo(box.right - w * 0.08f, box.bottom - r)
        quadraticTo(box.right - w * 0.1f, box.bottom, box.right - w * 0.1f - r, box.bottom)
        lineTo(box.left + w * 0.1f + r, box.bottom)
        quadraticTo(box.left + w * 0.1f, box.bottom, box.left + w * 0.08f, box.bottom - r)
        close()
    }

    /** The cup's [body], with [coffee] filled up to [fill] (0..1 of its height). */
    fun DrawScope.drawCup(box: Rect, body: Color, coffee: Color, fill: Float) {
        val path = cup(box)
        drawPath(path, body)
        if (fill > 0f) {
            val surface = box.bottom - box.height * fill.coerceIn(0f, 1f)
            clipPath(path) { drawRect(coffee, Offset(box.left, surface), Size(box.width, box.bottom - surface)) }
        }
    }

    /** The scale: a solid pill with its LED in the middle, [led] its alpha. */
    fun DrawScope.drawScale(box: Rect, color: Color, ledColor: Color, led: Float) {
        drawRoundRect(color, box.topLeft, box.size, CornerRadius(box.height / 2))
        drawCircle(ledColor.copy(alpha = led.coerceIn(0f, 1f)), radius = box.height * 0.22f, center = box.center)
    }
}
