package dev.hnm.workbench.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.TypedValue
import android.view.View

/**
 * Speaker grille view — dot grid matching the HTML:
 *   background-image: radial-gradient(var(--grille) 1px, transparent 1.25px)
 *   background-size: 6.5px 6.5px; background-position: center
 */
class GrilleView(context: Context) : View(context) {

    var dotColor: Int = Color.parseColor("#C3C2BF")

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = dotColor

        val spacing = dp(6.5f)
        val dotR = dp(0.7f)
        var y = spacing / 2f
        while (y < height) {
            var x = spacing / 2f
            while (x < width) {
                canvas.drawCircle(x, y, dotR, paint)
                x += spacing
            }
            y += spacing
        }
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
}
