package dev.hnm.workbench.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.TypedValue
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import dev.hnm.workbench.core.design.SplashGeometry
import dev.hnm.workbench.core.design.SplashScene
import dev.hnm.workbench.core.design.SplashVisual
import kotlin.math.min

/**
 * The procedural splash, drawn on a Canvas and synchronized to a [SplashScene] — the *same* pattern that
 * drives the haptics + audio, so the felt taps, the sound and the animation are coincident by
 * construction. A different motif (ripple / bloom / sweep / spark) shows each launch (seed-selected).
 *
 * Lifecycle: [begin] fires the haptics/audio via [onStart] and starts the frame loop; when the scene's
 * duration (plus a short tail) elapses — or the user taps to skip — [onDone] reveals the real content.
 */
class SplashView(
    context: Context,
    private val scene: SplashScene,
    var onStart: () -> Unit = {},
    var onDone: () -> Unit = {},
) : View(context) {

    // recorder2 palette
    private val cScreen = Color.parseColor("#141210")
    private val cRed = Color.parseColor("#E22C24")
    private val cInk = Color.parseColor("#DDDBD6")
    private val cBar = Color.parseColor("#D2CFC8")

    private val tail = 0.5
    private val totalDur = scene.durationSeconds + tail

    private var startNanos = 0L
    private var running = false
    private var finished = false

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cInk; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /** Trigger the coincident haptics+audio and start animating. */
    fun begin() {
        if (running) return
        running = true
        startNanos = System.nanoTime()
        onStart()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun finish() {
        if (finished) return
        finished = true
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        onDone()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) { finish(); return true }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(cScreen)
        val t = (System.nanoTime() - startNanos) / 1e9
        if (t >= totalDur) { finish(); return }

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h * 0.44f
        val scale = min(w, h) * 0.40f
        val master = SplashGeometry.masterAlpha(t, scene.durationSeconds)

        when (scene.visual) {
            SplashVisual.RIPPLE -> drawRipple(canvas, t, cx, cy, scale, master)
            SplashVisual.BLOOM -> drawBloom(canvas, t, cx, cy, w, h, master)
            SplashVisual.SWEEP -> drawSweep(canvas, t, cx, cy, w, master)
            SplashVisual.SPARK -> drawSpark(canvas, t, cx, cy, scale, master)
        }

        // Wordmark fades in under the visual.
        text.textSize = dp(15f)
        text.alpha = (master * 0.9f * 255).toInt().coerceIn(0, 255)
        canvas.drawText("HAPTICS WORKBENCH", cx, h * 0.82f, text)

        // Scanlines for the CRT feel.
        drawScanlines(canvas, w, h, master)
    }

    private fun drawRipple(c: Canvas, t: Double, cx: Float, cy: Float, scale: Float, master: Float) {
        stroke.color = cRed
        stroke.strokeWidth = dp(3f)
        for (ring in SplashGeometry.ripple(t, scene.beats)) {
            stroke.alpha = (ring.alpha * master * 255).toInt().coerceIn(0, 255)
            c.drawCircle(cx, cy, ring.radius * scale, stroke)
        }
        // a steady centre dot
        fill.color = cRed
        fill.alpha = (master * 255).toInt().coerceIn(0, 255)
        c.drawCircle(cx, cy, dp(6f), fill)
    }

    private fun drawBloom(c: Canvas, t: Double, cx: Float, cy: Float, w: Float, h: Float, master: Float) {
        val bars = 40
        val heights = SplashGeometry.bloom(t, scene.durationSeconds, scene.beats, bars, scene.seed)
        val band = w * 0.7f
        val left = cx - band / 2f
        val slot = band / bars
        val barW = slot * 0.55f
        val maxH = h * 0.22f
        for (i in 0 until bars) {
            val mid = (bars - 1) / 2.0
            val centerish = 1f - (kotlin.math.abs(i - mid) / mid).toFloat()
            fill.color = blend(cBar, cRed, centerish * 0.6f)
            fill.alpha = (master * 0.85f * 255).toInt().coerceIn(0, 255)
            val bh = heights[i] * maxH
            val x = left + i * slot
            c.drawRect(x, cy - bh, x + barW, cy + bh, fill)
        }
    }

    private fun drawSweep(c: Canvas, t: Double, cx: Float, cy: Float, w: Float, master: Float) {
        val left = w * 0.12f
        val right = w * 0.88f
        val span = right - left
        // ticks
        fill.color = cBar
        for ((x, bright) in SplashGeometry.sweepTicks(t, scene.durationSeconds, scene.beats)) {
            fill.alpha = (bright * master * 255).toInt().coerceIn(0, 255)
            val px = left + x * span
            c.drawRect(px - dp(1f), cy - dp(10f), px + dp(1f), cy + dp(10f), fill)
        }
        // red playhead + dot cap
        val p = SplashGeometry.sweepProgress(t, scene.durationSeconds)
        val px = left + p * span
        stroke.color = cRed
        stroke.strokeWidth = dp(2f)
        stroke.alpha = (master * 255).toInt().coerceIn(0, 255)
        c.drawLine(px, cy - dp(26f), px, cy + dp(22f), stroke)
        fill.color = cRed
        fill.alpha = (master * 255).toInt().coerceIn(0, 255)
        c.drawCircle(px, cy - dp(26f), dp(5f), fill)
    }

    private fun drawSpark(c: Canvas, t: Double, cx: Float, cy: Float, scale: Float, master: Float) {
        fill.color = cRed
        fill.alpha = (master * 255).toInt().coerceIn(0, 255)
        c.drawCircle(cx, cy, dp(7f), fill)
        for (s in SplashGeometry.sparks(t, scene.beats, scene.seed)) {
            fill.color = blend(cRed, cBar, 0.4f)
            fill.alpha = (s.alpha * master * 255).toInt().coerceIn(0, 255)
            c.drawCircle(cx + s.x * scale, cy + s.y * scale, s.size * scale, fill)
        }
    }

    private fun drawScanlines(c: Canvas, w: Float, h: Float, master: Float) {
        fill.color = Color.WHITE
        fill.alpha = (0.018f * master * 255).toInt().coerceIn(0, 255)
        var y = 0f
        while (y < h) {
            c.drawRect(0f, y, w, y + 1f, fill)
            y += 3f
        }
    }

    private fun blend(a: Int, b: Int, f: Float): Int {
        val ff = f.coerceIn(0f, 1f)
        val r = (Color.red(a) * (1 - ff) + Color.red(b) * ff).toInt()
        val g = (Color.green(a) * (1 - ff) + Color.green(b) * ff).toInt()
        val bl = (Color.blue(a) * (1 - ff) + Color.blue(b) * ff).toInt()
        return Color.rgb(r, g, bl)
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
}
