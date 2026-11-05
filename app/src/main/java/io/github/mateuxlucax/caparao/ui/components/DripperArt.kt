package io.github.mateuxlucax.caparao.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

/** A V60 dripper and server, assembled from flat layers as the filter checklist advances. */
object DripperArt {
    enum class Part { SERVER, HANDLE, PLATE, CONE, FLANGE }

    val parts = listOf(Part.SERVER, Part.HANDLE, Part.PLATE, Part.CONE, Part.FLANGE)

    /** The first [checkedCount] parts, independent of which checklist rows were checked. */
    fun visibleParts(checkedCount: Int): List<Part> = parts.take(checkedCount.coerceIn(0, parts.size))

    /** Draws a 120×120 side view, with the whole object as a ghost and its checked parts assembled on top. */
    fun DrawScope.drawDripper(reveal: List<Float>, primary: Color, handle: Color) {
        require(reveal.size == parts.size)
        scale(size.width / ART_SIZE, size.height / ART_SIZE, pivot = Offset.Zero) {
            translate(ART_OFFSET_X, ART_OFFSET_Y) {
                scale(ART_SCALE, pivot = Offset.Zero) {
                    val ghost = primary.copy(alpha = GHOST_ALPHA)
                    listOf(Part.FLANGE, Part.CONE, Part.PLATE, Part.SERVER, Part.HANDLE).forEach { part ->
                        drawPart(part, ghost, ghost)
                    }
                    parts.forEachIndexed { index, part ->
                        val amount = reveal[index].coerceIn(0f, 1f)
                        if (amount > 0f) {
                            translate(top = -(1f - amount) * PART_SLIDE) {
                                drawPart(part, primary.copy(alpha = amount), handle.copy(alpha = amount))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun DrawScope.drawPart(part: Part, primary: Color, handle: Color) {
        when (part) {
            Part.SERVER -> drawPath(server, primary)
            Part.HANDLE -> drawPath(handlePath, handle, style = Stroke(width = HANDLE_STROKE, cap = StrokeCap.Round))
            Part.PLATE -> drawRoundRect(primary, Offset(PLATE_LEFT, PLATE_TOP), Size(PLATE_WIDTH, PLATE_HEIGHT), CornerRadius(PLATE_RADIUS))
            Part.CONE -> drawPath(cone, primary)
            Part.FLANGE -> drawRoundRect(primary, Offset(FLANGE_LEFT, FLANGE_TOP), Size(FLANGE_WIDTH, FLANGE_HEIGHT), CornerRadius(FLANGE_RADIUS))
        }
    }

    private const val ART_SIZE = 120f
    private const val ART_OFFSET_X = 3.5f
    private const val ART_OFFSET_Y = 2.5f
    private const val ART_SCALE = 0.9f
    private const val GHOST_ALPHA = 0.14f
    private const val PART_SLIDE = 7f
    private const val HANDLE_STROKE = 4.5f
    private const val PLATE_LEFT = 42f
    private const val PLATE_TOP = 56f
    private const val PLATE_WIDTH = 36f
    private const val PLATE_HEIGHT = 4.5f
    private const val PLATE_RADIUS = 2.25f
    private const val FLANGE_LEFT = 28.5f
    private const val FLANGE_TOP = 29.5f
    private const val FLANGE_WIDTH = 63f
    private const val FLANGE_HEIGHT = 5f
    private const val FLANGE_RADIUS = 2.5f

    private val server by lazy {
        Path().apply {
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
    }

    private val handlePath by lazy {
        Path().apply {
            moveTo(79.5f, 69.5f)
            lineTo(89f, 69.5f)
            quadraticTo(96f, 69.5f, 96f, 76.5f)
            lineTo(96f, 80f)
            quadraticTo(96f, 87f, 89f, 87f)
            lineTo(87f, 87f)
        }
    }

    private val cone by lazy {
        Path().apply {
            moveTo(33f, 34.5f)
            lineTo(87f, 34.5f)
            lineTo(70.5f, 54f)
            quadraticTo(69f, 56f, 66.5f, 56f)
            lineTo(53.5f, 56f)
            quadraticTo(51f, 56f, 49.5f, 54f)
            close()
        }
    }
}
