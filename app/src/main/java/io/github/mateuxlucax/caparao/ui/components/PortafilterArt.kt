package io.github.mateuxlucax.caparao.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

/** Static, flat-filled side view used by the espresso portafilter preparation step. */
object PortafilterArt {
    /** Draws the portafilter at 68% of the available width, centered, using [metal] and [handle] as fill colors. */
    fun DrawScope.drawPortafilter(metal: Color, handle: Color) {
        val artWidth = 95f
        val artHeight = 37.5f
        val unit = size.width * 0.68f / artWidth
        val left = (size.width - artWidth * unit) / 2f - 3f * unit
        val top = (size.height - artHeight * unit) / 2f - 34f * unit

        translate(left, top) {
            scale(unit, pivot = Offset.Zero) {
                drawRoundRect(metal, Offset(3f, 35.7f), Size(54f, 3.6f), CornerRadius(1.8f))
                drawRoundRect(metal, Offset(8f, 34f), Size(44f, 7f), CornerRadius(3.5f))
                drawPath(
                    Path().apply {
                        moveTo(11f, 40f)
                        lineTo(49f, 40f)
                        lineTo(46.5f, 52f)
                        quadraticTo(45.8f, 56f, 42f, 56f)
                        lineTo(18f, 56f)
                        quadraticTo(14.2f, 56f, 13.5f, 52f)
                        close()
                    },
                    metal,
                )
                drawRoundRect(metal, Offset(25.5f, 55f), Size(9f, 6f), CornerRadius(1f))
                drawPath(
                    Path().apply {
                        moveTo(16f, 70f)
                        lineTo(16f, 65f)
                        quadraticTo(16f, 60.5f, 20.5f, 60.5f)
                        lineTo(39.5f, 60.5f)
                        quadraticTo(44f, 60.5f, 44f, 65f)
                        lineTo(44f, 70f)
                        quadraticTo(44f, 71.5f, 42.5f, 71.5f)
                        lineTo(40f, 71.5f)
                        quadraticTo(38.5f, 71.5f, 38.5f, 70f)
                        lineTo(38.5f, 66f)
                        lineTo(21.5f, 66f)
                        lineTo(21.5f, 70f)
                        quadraticTo(21.5f, 71.5f, 20f, 71.5f)
                        lineTo(17.5f, 71.5f)
                        quadraticTo(16f, 71.5f, 16f, 70f)
                        close()
                    },
                    metal,
                )
                drawRoundRect(metal, Offset(44f, 41.5f), Size(16f, 7.5f), CornerRadius(1f))
                drawPath(
                    Path().apply {
                        moveTo(58f, 40.6f)
                        lineTo(92f, 38.2f)
                        quadraticTo(98f, 37.8f, 98f, 43.5f)
                        lineTo(98f, 47.5f)
                        quadraticTo(98f, 53f, 92f, 52.6f)
                        lineTo(58f, 50f)
                        quadraticTo(56f, 49.8f, 56f, 48f)
                        lineTo(56f, 42.6f)
                        quadraticTo(56f, 40.8f, 58f, 40.6f)
                        close()
                    },
                    handle,
                )
            }
        }
    }
}
