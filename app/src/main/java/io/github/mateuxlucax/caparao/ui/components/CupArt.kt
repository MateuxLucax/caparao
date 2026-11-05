package io.github.mateuxlucax.caparao.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.min

/**
 * The app's cup and scale, drawn as solid filled shapes with no outlines, the way Material 3 Expressive draws its
 * pictures (#109). Shared by the welcome animation, the brew stages and the guided pour.
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

    /** A flat server with its water, sized to the 80 × 52 viewBox inside [box]. */
    fun DrawScope.drawServer(
        box: Rect,
        body: Color,
        handle: Color,
        water: Color,
        targetMark: Color,
        fill: Float,
    ) {
        val scale = min(box.width / SERVER_VIEW_WIDTH, box.height / SERVER_VIEW_HEIGHT)
        val left = box.left + (box.width - SERVER_VIEW_WIDTH * scale) / 2f - SERVER_VIEW_LEFT * scale
        val top = box.top + (box.height - SERVER_VIEW_HEIGHT * scale) / 2f - SERVER_VIEW_TOP * scale
        val bodyPath = Path().apply {
            moveTo(46f, 60.5f)
            lineTo(74f, 60.5f)
            lineTo(74f, 64f)
            quadraticTo(74f, 66.5f, 75.5f, 68.5f)
            lineTo(84.5f, 81f)
            quadraticTo(87f, 84.5f, 87f, 88f)
            lineTo(87f, 90f)
            quadraticTo(87f, 96.5f, 80.5f, 96.5f)
            lineTo(39.5f, 96.5f)
            quadraticTo(33f, 96.5f, 33f, 90f)
            lineTo(33f, 88f)
            quadraticTo(33f, 84.5f, 35.5f, 81f)
            lineTo(44.5f, 68.5f)
            quadraticTo(46f, 66.5f, 46f, 64f)
            close()
        }
        val fillFraction = fill.coerceIn(0f, 1.1f)
        val waterTop = SERVER_BOTTOM - (SERVER_BOTTOM - SERVER_FILL_TOP) * SERVER_TARGET_FILL * fillFraction
        val targetY = SERVER_BOTTOM - (SERVER_BOTTOM - SERVER_FILL_TOP) * SERVER_TARGET_FILL

        translate(left, top) {
            scale(scale, pivot = Offset.Zero) {
                val handlePath = Path().apply {
                    moveTo(74f, 69.5f)
                    lineTo(89f, 69.5f)
                    quadraticTo(96f, 69.5f, 96f, 76.5f)
                    lineTo(96f, 80f)
                    quadraticTo(96f, 87f, 89f, 87f)
                    lineTo(83f, 87f)
                }
                drawPath(handlePath, handle, style = Stroke(width = 4.5f, cap = StrokeCap.Round))
                drawPath(bodyPath, body)
                clipPath(bodyPath) {
                    if (fillFraction > 0f) {
                        drawRect(water, Offset(33f, waterTop), Size(54f, SERVER_BOTTOM - waterTop))
                    }
                    drawLine(
                        targetMark.copy(alpha = 0.75f),
                        Offset(58f, targetY),
                        Offset(86f, targetY),
                        strokeWidth = 1.1f,
                        cap = StrokeCap.Round,
                    )
                }
                drawRoundRect(
                    color = body,
                    topLeft = Offset(42f, 57f),
                    size = Size(36f, 4.5f),
                    cornerRadius = CornerRadius(2.25f),
                )
            }
        }
    }

    /** The scale: a solid pill with its LED in the middle, [led] its alpha. */
    fun DrawScope.drawScale(box: Rect, color: Color, ledColor: Color, led: Float) {
        drawRoundRect(color, box.topLeft, box.size, CornerRadius(box.height / 2))
        drawCircle(ledColor.copy(alpha = led.coerceIn(0f, 1f)), radius = box.height * 0.22f, center = box.center)
    }

    private const val SERVER_VIEW_LEFT = 26f
    private const val SERVER_VIEW_TOP = 50f
    private const val SERVER_VIEW_WIDTH = 80f
    private const val SERVER_VIEW_HEIGHT = 52f
    private const val SERVER_FILL_TOP = 61f
    private const val SERVER_BOTTOM = 96.5f
    private const val SERVER_TARGET_FILL = 0.75f
}
