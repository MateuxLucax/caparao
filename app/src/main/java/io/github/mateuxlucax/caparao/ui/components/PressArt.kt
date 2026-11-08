package io.github.mateuxlucax.caparao.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.caparao.ui.components.CupArt.drawScale
import kotlin.math.min

/**
 * The French press: a flat object in the portafilter's style, a glass beaker in a metal frame with feet, a C handle and,
 * from the steep on, a lid with a knob and a plunger whose plate is the mesh (#280). It fills with the real weight (its
 * color tells the distance from the target, no line, as the filter server), darkens while it steeps, rises off the
 * scale on removal and then lowers its plunger (#226).
 */
object PressArt {
    /**
     * Coffee as it steeps, in the theme's [primary]: a light tint at the first water, a deep shade at the deadline
     * (#302). [progress] is 0..1 of the steep.
     */
    fun steepColor(progress: Float, primary: Color): Color =
        lerp(lerp(primary, Color.White, 0.3f), lerp(primary, Color.Black, 0.45f), progress.coerceIn(0f, 1f))

    /**
     * The press in [box], drawn in its 80 × 90 viewBox. [metal] is the frame, lid, rod and mesh; [glass] the beaker,
     * [coffee] the water filled to [fill] (0..1.1 of the target). The lid and plunger show only when [lid] is set, and
     * [plunger] 0 raised to 1 lowered moves the mesh from the neck into the coffee.
     */
    fun DrawScope.drawPress(
        box: Rect,
        metal: Color,
        glass: Color,
        coffee: Color,
        fill: Float,
        lid: Boolean,
        plunger: Float,
    ) {
        val scale = min(box.width / VIEW_WIDTH, box.height / VIEW_HEIGHT)
        val left = box.left + (box.width - VIEW_WIDTH * scale) / 2f
        val top = box.top + (box.height - VIEW_HEIGHT * scale) / 2f
        translate(left, top) {
            scale(scale, pivot = Offset.Zero) {
                val beaker = beakerPath()
                drawPath(handlePath(), metal, style = Stroke(width = 4.5f, cap = StrokeCap.Round))
                drawPath(beaker, glass)
                val fillFraction = fill.coerceIn(0f, 1.1f)
                if (fillFraction > 0f) {
                    val waterTop = GLASS_BOTTOM - (GLASS_BOTTOM - GLASS_TOP) * WATER_TARGET * fillFraction
                    clipPath(beaker) {
                        drawRect(coffee, Offset(GLASS_LEFT, waterTop), Size(GLASS_RIGHT - GLASS_LEFT, GLASS_BOTTOM - waterTop))
                    }
                }
                drawRoundRect(metal, Offset(FRAME_LEFT, COLLAR_TOP), Size(FRAME_WIDTH, BAR_HEIGHT), CornerRadius(BAR_RADIUS))
                drawRoundRect(metal, Offset(FRAME_LEFT, BASE_TOP), Size(FRAME_WIDTH, BAR_HEIGHT), CornerRadius(BAR_RADIUS))
                drawRoundRect(metal, Offset(FOOT_LEFT, FOOT_TOP), Size(FOOT_WIDTH, FOOT_HEIGHT), CornerRadius(BAR_RADIUS))
                drawRoundRect(metal, Offset(FOOT_RIGHT, FOOT_TOP), Size(FOOT_WIDTH, FOOT_HEIGHT), CornerRadius(BAR_RADIUS))
                if (lid) {
                    val t = plunger.coerceIn(0f, 1f)
                    val plate = PLATE_RAISED + (PLATE_LOWERED - PLATE_RAISED) * t
                    drawRoundRect(metal, Offset(LID_LEFT, LID_TOP), Size(LID_WIDTH, BAR_HEIGHT), CornerRadius(BAR_RADIUS))
                    drawLine(metal, Offset(CENTER_X, KNOB_Y), Offset(CENTER_X, plate), strokeWidth = 2.5f, cap = StrokeCap.Round)
                    drawCircle(metal, radius = KNOB_RADIUS, center = Offset(CENTER_X, KNOB_Y))
                    drawRoundRect(
                        metal,
                        Offset(CENTER_X - MESH_HALF, plate - MESH_HEIGHT / 2),
                        Size(MESH_HALF * 2, MESH_HEIGHT),
                        CornerRadius(MESH_HEIGHT / 2),
                    )
                }
            }
        }
    }

    private fun beakerPath(): Path = Path().apply {
        addRoundRect(
            RoundRect(
                GLASS_LEFT,
                GLASS_TOP,
                GLASS_RIGHT,
                GLASS_BOTTOM,
                CornerRadius.Zero,
                CornerRadius.Zero,
                CornerRadius(GLASS_CORNER),
                CornerRadius(GLASS_CORNER),
            ),
        )
    }

    /** The C handle on the right of the beaker, starting inside it so the glass covers its join. */
    private fun handlePath(): Path = Path().apply {
        moveTo(GLASS_RIGHT - 2f, HANDLE_TOP)
        lineTo(HANDLE_RIGHT - 4f, HANDLE_TOP)
        quadraticTo(HANDLE_RIGHT, HANDLE_TOP, HANDLE_RIGHT, HANDLE_TOP + 4f)
        lineTo(HANDLE_RIGHT, HANDLE_BOTTOM - 4f)
        quadraticTo(HANDLE_RIGHT, HANDLE_BOTTOM, HANDLE_RIGHT - 4f, HANDLE_BOTTOM)
        lineTo(GLASS_RIGHT - 2f, HANDLE_BOTTOM)
    }

    private const val VIEW_WIDTH = 80f
    private const val VIEW_HEIGHT = 90f
    private const val CENTER_X = 36f
    private const val KNOB_Y = 5f
    private const val KNOB_RADIUS = 3.5f
    private const val LID_LEFT = 14f
    private const val LID_TOP = 18f
    private const val LID_WIDTH = 44f
    private const val FRAME_LEFT = 16f
    private const val FRAME_WIDTH = 40f
    private const val COLLAR_TOP = 23f
    private const val BASE_TOP = 79.5f
    private const val BAR_HEIGHT = 3.5f
    private const val BAR_RADIUS = 1.75f
    private const val FOOT_LEFT = 20f
    private const val FOOT_RIGHT = 49f
    private const val FOOT_TOP = 83f
    private const val FOOT_WIDTH = 3f
    private const val FOOT_HEIGHT = 5f
    private const val GLASS_LEFT = 18f
    private const val GLASS_RIGHT = 54f
    private const val GLASS_TOP = 26.5f
    private const val GLASS_BOTTOM = 80f
    private const val GLASS_CORNER = 5f
    private const val WATER_TARGET = 0.75f
    private const val HANDLE_RIGHT = 66f
    private const val HANDLE_TOP = 30f
    private const val HANDLE_BOTTOM = 74f
    private const val PLATE_RAISED = 33f
    private const val PLATE_LOWERED = 74f
    private const val MESH_HALF = 13f
    private const val MESH_HEIGHT = 2.5f
}

/**
 * The press on the scale. [fill] is 0..1.1 of the target, [coffee] its color, [plunger] 0 raised to 1 lowered, [lid]
 * whether the lid is on (from the steep on) and [lift] 0 on the scale to 1 off it. Tags: `guided-cup` on the frame,
 * `guided-server` on the press canvas.
 */
@Composable
fun PressOnScale(
    fill: Float,
    coffee: Color,
    modifier: Modifier = Modifier,
    plunger: Float = 0f,
    lid: Boolean = false,
    lift: Float = 0f,
    size: Dp = 160.dp,
    showScale: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.width(size).height(size).testTag("guided-cup")) {
        Canvas(Modifier.fillMaxSize().testTag("guided-server")) {
            val canvas = this.size
            val rise = size.toPx() * 0.16f * lift
            val room = size.toPx() * if (showScale) 0.12f else 0f
            if (showScale) {
                drawScale(
                    Rect(canvas.width * 0.1f, canvas.height - room, canvas.width * 0.9f, canvas.height),
                    colors.secondaryContainer,
                    colors.onSecondaryContainer,
                    0.5f,
                )
            }
            with(PressArt) {
                drawPress(
                    Rect(0f, -rise, canvas.width, canvas.height - room - rise),
                    metal = colors.outline,
                    glass = colors.surfaceContainerHighest,
                    coffee = coffee,
                    fill = fill,
                    lid = lid,
                    plunger = plunger,
                )
            }
        }
    }
}
