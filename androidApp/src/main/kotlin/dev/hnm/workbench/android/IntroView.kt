package dev.hnm.workbench.android

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.PatternSerialization
import kotlin.math.max

/**
 * The first-launch HAfx intro (10 s). Two halves, one timeline:
 *  - Picture: a silent, pre-rendered video of the web player's frames (assets/intro/hafx-intro.mp4).
 *    Same drawing code as the website, so the cells look identical everywhere.
 *  - Sound + touch: the intro IR (assets/intro/intro.hafx.json), played natively through the existing
 *    renderer, so the actuator gets real amplitude/primitive scheduling for this device.
 *
 * Sync: the IR starts on MEDIA_INFO_VIDEO_RENDERING_START (the first frame actually on screen),
 * not on prepare(), so decoder warm-up can't open a gap between what you see and what you feel.
 * Tap to skip: [onSkip] lets the host cancel the vibrator and stop audio.
 */
class IntroView(
    context: Context,
    var onStart: () -> Unit = {},
    var onSkip: () -> Unit = {},
    var onDone: () -> Unit = {},
) : FrameLayout(context), TextureView.SurfaceTextureListener {

    private val texture = TextureView(context)
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var started = false
    private var finished = false

    init {
        setBackgroundColor(Color.BLACK)
        addView(texture, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        texture.surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
        val mp = MediaPlayer()
        player = mp
        try {
            context.assets.openFd(VIDEO_ASSET).use { afd -> mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) }
            surface = Surface(st).also { mp.setSurface(it) }
            mp.setVolume(0f, 0f)
            mp.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) begin()
                false
            }
            mp.setOnPreparedListener { cropToFill(it.videoWidth, it.videoHeight); it.start() }
            mp.setOnCompletionListener { finish() }
            mp.setOnErrorListener { _, _, _ -> finish(); true }
            mp.prepareAsync()
        } catch (t: Throwable) {
            finish()
        }
    }

    private fun begin() {
        if (started || finished) return
        started = true
        onStart()
    }

    /** Center-crop the 9:16 render onto whatever the screen is (no letterbox on tall or wide phones). */
    private fun cropToFill(vw: Int, vh: Int) {
        if (vw <= 0 || vh <= 0 || width == 0 || height == 0) return
        val s = max(width / vw.toFloat(), height / vh.toFloat())
        val m = Matrix()
        m.setScale(vw * s / width, vh * s / height, width / 2f, height / 2f)
        texture.setTransform(m)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (started) onSkip()
            finish()
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun finish() {
        if (finished) return
        finished = true
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        surface?.release()
        surface = null
        onDone()
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
        player?.let { cropToFill(it.videoWidth, it.videoHeight) }
    }

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { finish(); return true }
    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit

    companion object {
        const val VIDEO_ASSET = "intro/hafx-intro.mp4"
        const val SCORE_ASSET = "intro/intro.hafx.json"
        private const val PREFS = "hafx"
        private const val SEEN = "intro_seen_v1"

        fun loadScore(context: Context): HapticAudioPattern =
            context.assets.open(SCORE_ASSET).bufferedReader().use { PatternSerialization.decode(it.readText()) }

        fun shouldShow(context: Context): Boolean =
            !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(SEEN, false)

        fun markSeen(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(SEEN, true).apply()
        }
    }
}
