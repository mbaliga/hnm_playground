package dev.hnm.workbench.android.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import dev.hnm.workbench.core.widget.PatternSketch
import kotlin.math.roundToInt

/**
 * Draws a [PatternSketch] as a white mask the widget's ImageView tints with its ink colour (so it follows
 * day and night with the rest of the widget). The two series differ in shape, not colour: intensity is
 * translucent bars with a solid top edge, sharpness a dashed line with a square mark where it starts.
 */
object ShapeBitmap {

    /** Wide enough for a 4-cell widget, capped so a home-screen update stays well under the bitmap limit. */
    fun forWidget(context: Context, sketch: PatternSketch, widthDp: Int = 250, heightDp: Int = 96): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (widthDp * density).roundToInt().coerceIn(64, MAX_WIDTH_PX)
        val h = (heightDp * density).roundToInt().coerceIn(24, MAX_HEIGHT_PX)
        return draw(sketch, w, h, density)
    }

    fun draw(sketch: PatternSketch, widthPx: Int, heightPx: Int, density: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = (1.5f * density).coerceAtLeast(1f)
        val inset = stroke * 2
        val top = inset
        val bottom = heightPx - stroke
        val span = bottom - top
        val n = sketch.columns.coerceAtLeast(1)
        val colW = widthPx.toFloat() / n

        val baseline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 90
            strokeWidth = stroke * 0.66f
        }
        canvas.drawLine(0f, bottom, widthPx.toFloat(), bottom, baseline)

        val fill = Paint().apply {
            color = Color.WHITE
            alpha = 110
            style = Paint.Style.FILL
        }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }
        val edgePath = Path()
        var open = false
        for (c in 0 until n) {
            val v = sketch.intensity.getOrElse(c) { 0.0 }.toFloat()
            val x0 = c * colW
            val x1 = x0 + colW
            if (v <= 0.001f) {
                open = false
                continue
            }
            val y = bottom - v * span
            canvas.drawRect(x0, y, x1, bottom, fill)
            if (!open) {
                edgePath.moveTo(x0, bottom)
                edgePath.lineTo(x0, y)
                open = true
            } else {
                edgePath.lineTo(x0, y)
            }
            edgePath.lineTo(x1, y)
            val next = sketch.intensity.getOrElse(c + 1) { 0.0 }
            if (next <= 0.001) edgePath.lineTo(x1, bottom)
        }
        canvas.drawPath(edgePath, edge)

        val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = stroke
            pathEffect = DashPathEffect(floatArrayOf(4f * density, 3f * density), 0f)
        }
        val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val markSize = stroke * 2.2f
        val sharpPath = Path()
        var drawing = false
        for (c in 0 until n) {
            val s = sketch.sharpness.getOrNull(c)
            if (s == null) {
                drawing = false
                continue
            }
            val x0 = c * colW
            val y = bottom - s.toFloat() * span
            if (!drawing) {
                canvas.drawRect(x0, y - markSize / 2, x0 + markSize, y + markSize / 2, mark)
                sharpPath.moveTo(x0, y)
                drawing = true
            } else {
                sharpPath.lineTo(x0, y)
            }
            sharpPath.lineTo(x0 + colW, y)
        }
        canvas.drawPath(sharpPath, dashed)
        return bitmap
    }

    private const val MAX_WIDTH_PX = 600
    private const val MAX_HEIGHT_PX = 240
}
