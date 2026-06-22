package dev.hnm.workbench.android

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Vibrator
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.hnm.workbench.core.design.RhythmCapture
import dev.hnm.workbench.core.design.Tap
import dev.hnm.workbench.core.design.Variations
import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.core.playback.HapticCapabilities

/**
 * The on-device player (M1/M6, "player now, editor later"). It lists the built-in pattern plus
 * generated variations and a captured rhythm, and plays each on the phone's real actuator + speaker
 * via the same `core` IR/renderer used everywhere else. Nothing here is Android-specific beyond the
 * Vibrator/AudioTrack glue — the design logic all lives in `core`.
 */
class MainActivity : Activity() {

    private val renderer = DefaultPatternRenderer()
    private val audio = AudioPlayer()
    private lateinit var vibrator: Vibrator
    private var capabilities: HapticCapabilities = HapticCapabilities.LRA_FULL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vibrator = AndroidHaptics.vibrator(this)
        capabilities = AndroidHaptics.probe(vibrator)

        val patterns = buildList {
            add("Confirm" to BuiltInPatterns.CONFIRM)
            Variations.family(BuiltInPatterns.CONFIRM, count = 4).forEachIndexed { i, p ->
                add("Confirm · variation ${i + 1}" to p)
            }
            add("Captured rhythm" to capturedRhythm())
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#14161B"))
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        root.addView(title("Haptics + Audio Player"))
        root.addView(caption("Tap a row to feel it on this device."))
        root.addView(caption(capabilitySummary()))
        root.addView(spacer(dp(12)))

        patterns.forEach { (name, pattern) -> root.addView(patternRow(name, pattern)) }

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    override fun onDestroy() {
        audio.release()
        super.onDestroy()
    }

    private fun play(pattern: HapticAudioPattern) {
        // Render both paths first, then trigger together so they stay coincident.
        val effect = AndroidHaptics.toEffect(renderer.scheduleHaptics(pattern, capabilities))
        val stream = renderer.renderAudio(pattern, SAMPLE_RATE)
        runCatching { effect?.let { vibrator.vibrate(it) } }
        runCatching { audio.play(stream) }
    }

    private fun capturedRhythm(): HapticAudioPattern =
        RhythmCapture.fromTaps(
            listOf(Tap(0.0, 0.9), Tap(0.12, 0.6), Tap(0.24, 0.6), Tap(0.5, 1.0)),
            name = "Captured rhythm",
        )

    private fun capabilitySummary(): String {
        val c = capabilities
        val prims = if (c.supportedPrimitives.isEmpty()) "none" else c.supportedPrimitives.size.toString()
        return "actuator ${c.actuatorType} · amplitude ${if (c.hasAmplitudeControl) "yes" else "no"} · " +
            "primitives $prims"
    }

    // --- tiny view helpers (no XML / appcompat to keep the build minimal) ---

    private fun patternRow(name: String, pattern: HapticAudioPattern): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            addView(
                TextView(this@MainActivity).apply {
                    text = name
                    setTextColor(Color.parseColor("#E6E8EC"))
                    textSize = 16f
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                },
            )
            addView(
                Button(this@MainActivity).apply {
                    text = "Play"
                    setOnClickListener { play(pattern) }
                },
            )
        }

    private fun title(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.parseColor("#FFFFFF"))
        textSize = 22f
        setTypeface(typeface, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }

    private fun caption(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.parseColor("#8B90A0"))
        textSize = 13f
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun spacer(height: Int) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, height)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
