package dev.hnm.workbench.android

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import dev.hnm.workbench.android.widget.HnmWidgets
import dev.hnm.workbench.android.widget.WidgetIntents
import dev.hnm.workbench.core.dsp.PatternTiming
import dev.hnm.workbench.core.library.FeelEntry
import dev.hnm.workbench.core.library.FeelTestCatalog
import dev.hnm.workbench.core.widget.RecentlyFelt

/**
 * Plays one catalogue pattern for a home-screen widget, then gets out of the way. On Android 12+ a
 * touch-feedback vibration from an app in the background is dropped, so a pad can't play from a
 * broadcast; this small translucent activity puts the app in the foreground just long enough to play the
 * pattern (haptics and its sound, exactly as the gallery's Play does), shows its name, and finishes.
 * Not exported: only the app's own PendingIntents reach it, and it plays nothing but catalogue ids.
 */
class FeelActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val audio = AudioPlayer()
    private lateinit var chip: TextView
    private lateinit var root: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chip = TextView(this).apply {
            setTextColor(getColor(R.color.hnm_widget_ink))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            maxLines = 2
            background = GradientDrawable().apply {
                setColor(getColor(R.color.hnm_widget_surface))
                setStroke(dp(1), getColor(R.color.hnm_widget_stroke))
                cornerRadius = dp(12).toFloat()
            }
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        root = FrameLayout(this).apply {
            addView(
                chip,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(96)
                },
            )
            // A tap anywhere ends it early; the pattern is short either way.
            setOnClickListener { finish() }
            contentDescription = getString(R.string.feel_dismiss_cd)
        }
        setContentView(root)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        audio.release()
        super.onDestroy()
    }

    private fun handle(intent: Intent?) {
        handler.removeCallbacksAndMessages(null)
        val entry = entryFrom(intent)
        if (entry == null) {
            Toast.makeText(this, R.string.feel_unknown, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        chip.text = getString(R.string.feel_playing, entry.name)
        // The whole screen is the dismiss target, so TalkBack lands on it: say what is playing and what a tap does.
        root.contentDescription = getString(R.string.feel_playing_cd, entry.name)

        val vibrator = AndroidHaptics.vibrator(this)
        val player = AndroidPatternPlayer(
            vibrator = vibrator,
            capabilities = AndroidHaptics.probe(vibrator),
            handler = handler,
            audio = audio,
            onMessage = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() },
        )
        player.play(entry.pattern)
        HnmWidgets.recordFelt(this, entry.id)

        // Stay in the foreground until the whole pattern (and its sound) has played.
        val holdMs = (PatternTiming.durationSeconds(entry.pattern) * 1000).toLong() + TAIL_MS
        handler.postDelayed({ finish() }, holdMs.coerceIn(MIN_HOLD_MS, MAX_HOLD_MS))
    }

    private fun entryFrom(intent: Intent?): FeelEntry? {
        if (intent?.action != WidgetIntents.ACTION_FEEL) return null
        val id = intent.getStringExtra(WidgetIntents.EXTRA_PATTERN_ID) ?: return null
        if (!RecentlyFelt.isValidId(id)) return null
        return FeelTestCatalog.find(id)
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private companion object {
        const val TAIL_MS = 400L
        const val MIN_HOLD_MS = 700L
        const val MAX_HOLD_MS = 5_000L
    }
}
