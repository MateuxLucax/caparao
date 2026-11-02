package io.github.mateuxlucax.caparao.ui.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Draws a transparent, tintable seven-bar chart without allowing RemoteViews bitmap payloads to grow unbounded. */
internal object BrewWidgetChart {
    const val MAX_BITMAP_BYTES = 256 * 1024

    private const val BYTES_PER_PIXEL = 4
    private const val MAX_BITMAP_DIMENSION_PX = 4_096
    private const val LABEL_TEXT_SIZE_DP = 10f
    private const val BAR_GAP_DP = 2f
    private const val BAR_TOP_GAP_DP = 2f
    private const val BASELINE_GAP_DP = 2f

    fun render(
        counts: List<Int>,
        countLabels: List<String>,
        widthPx: Int,
        heightPx: Int,
        density: Float,
    ): Bitmap {
        require(counts.size == 7) { "The weekly widget chart needs seven day buckets" }
        require(countLabels.size == counts.size) { "Each chart bar needs a localized count label" }

        val sourceWidth = widthPx.coerceAtLeast(1).coerceAtMost(MAX_BITMAP_DIMENSION_PX)
        val sourceHeight = heightPx.coerceAtLeast(1).coerceAtMost(MAX_BITMAP_DIMENSION_PX)
        val maximumPixels = MAX_BITMAP_BYTES / BYTES_PER_PIXEL
        val downsample = min(1.0, sqrt(maximumPixels.toDouble() / (sourceWidth.toLong() * sourceHeight)))
        var bitmapWidth = max(1, (sourceWidth * downsample).roundToInt()).coerceAtMost(sourceWidth)
        var bitmapHeight = max(1, (sourceHeight * downsample).roundToInt()).coerceAtMost(sourceHeight)
        while (bitmapWidth.toLong() * bitmapHeight > maximumPixels) {
            if (bitmapWidth >= bitmapHeight) bitmapWidth-- else bitmapHeight--
        }

        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply {
            scale(bitmapWidth.toFloat() / sourceWidth, bitmapHeight.toFloat() / sourceHeight)
        }
        val densityScale = density.coerceAtLeast(0.1f)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = LABEL_TEXT_SIZE_DP * densityScale
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 72
            strokeWidth = densityScale
        }
        val slotWidth = sourceWidth / counts.size.toFloat()
        val maximumCount = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
        val baselineY = sourceHeight - BASELINE_GAP_DP * densityScale
        val labelBaseline = -labelPaint.fontMetrics.top
        val barTop = labelBaseline + labelPaint.fontMetrics.bottom + BAR_TOP_GAP_DP * densityScale
        val availableBarHeight = (baselineY - barTop).coerceAtLeast(densityScale)
        val barWidth = min(slotWidth * 0.62f, slotWidth - BAR_GAP_DP * densityScale).coerceAtLeast(densityScale)

        canvas.drawLine(0f, baselineY, sourceWidth.toFloat(), baselineY, baselinePaint)
        counts.forEachIndexed { index, count ->
            val centerX = (index + 0.5f) * slotWidth
            canvas.drawText(countLabels[index], centerX, labelBaseline, labelPaint)
            if (count > 0) {
                val barHeight = availableBarHeight * count / maximumCount
                val top = baselineY - barHeight
                val halfWidth = barWidth / 2
                canvas.drawRoundRect(
                    RectF(centerX - halfWidth, top, centerX + halfWidth, baselineY),
                    halfWidth,
                    halfWidth,
                    barPaint,
                )
            }
        }
        return bitmap
    }
}
