package dev.hnm.workbench.android

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.hnm.workbench.core.design.RhythmCapture
import dev.hnm.workbench.core.design.Tap
import dev.hnm.workbench.core.design.Variations
import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.Envelope
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.core.playback.HapticCapabilities

/**
 * The on-device player. It lists the built-in pattern, generated variations, a captured rhythm and a
 * sustained-buzz demo, and plays each on the phone's real actuator + speaker via the same `core`
 * IR/renderer used everywhere else. A self-test and on-screen diagnostics make it clear what the
 * device reported and what's actually being sent to the vibrator.
 */
class MainActivity : Activity() {

    private val renderer = DefaultPatternRenderer()
    private val audio = AudioPlayer()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var vibrator: Vibrator
    private var capabilities: HapticCapabilities = HapticCapabilities.LRA_FULL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vibrator = AndroidHaptics.vibrator(this)
        capabilities = AndroidHaptics.probe(vibrator)

        val patterns = buildList {
            add("Confirm" to BuiltInPatterns.CONFIRM)
            add("Strong buzz (400 ms)" to strongBuzz())
            Variations.family(BuiltInPatterns.CONFIRM, count = 3).forEachIndexed { i, p ->
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
        root.addView(caption(diagnostics()))
        root.addView(spacer(dp(10)))

        // Self-test: an unmistakable buzz to confirm the actuator works independent of any pattern.
        root.addView(
            Button(this).apply {
                text = "▶  Vibration self-test (strong)"
                setOnClickListener {
                    AndroidHaptics.selfTest(vibrator, handler) { toast(it) }
                }
            },
        )
        root.addView(caption("If you can't feel the self-test, check Settings → Sound & vibration → vibration intensity, and that the phone isn't in a mode that mutes haptics."))
        root.addView(spacer(dp(12)))

        patterns.forEach { (name, pattern) -> root.addView(patternRow(name, pattern)) }

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    override fun onDestroy() {
        audio.release()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun play(pattern: HapticAudioPattern) {
        // Render both paths first, then trigger together so they stay coincident.
        val commands = renderer.scheduleHaptics(pattern, capabilities)
        val stream = renderer.renderAudio(pattern, SAMPLE_RATE)
        AndroidHaptics.playPattern(vibrator, pattern, commands, handler) { toast(it) }
        runCatching { audio.play(stream) }.onFailure { toast("audio failed: ${it.message}") }
    }

    private fun strongBuzz(): HapticAudioPattern =
        HapticAudioPattern(
            name = "Strong buzz",
            tracks = listOf(
                HapticTrack(
                    id = "h1",
                    events = listOf(
                        Continuous(
                            time = 0.0,
                            duration = 0.4,
                            intensity = 1.0,
                            sharpness = 0.5,
                            envelope = Envelope(attack = 0.02, sustain = 1.0, release = 0.05),
                        ),
                    ),
                ),
            ),
        )

    private fun capturedRhythm(): HapticAudioPattern =
        RhythmCapture.fromTaps(
            listOf(Tap(0.0, 0.9), Tap(0.12, 0.6), Tap(0.24, 0.6), Tap(0.5, 1.0)),
            name = "Captured rhythm",
        )

    private fun diagnostics(): String {
        val c = capabilities
        val prims = if (c.supportedPrimitives.isEmpty()) "none" else c.supportedPrimitives.joinToString(",")
        val effects = AndroidHaptics.supportedEffects(vibrator)
        val eff = if (effects.isEmpty()) "none reported (predefined still play via fallback)" else effects.joinToString(",")
        return "build v0.3 · vibrator ${if (c.hasVibrator) "present" else "ABSENT"}\n" +
            "actuator: ${AndroidHaptics.actuatorLabel(c)}\n" +
            "amplitude ${if (c.hasAmplitudeControl) "yes" else "no"} · primitives: $prims\n" +
            "predefined effects: $eff"
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // --- tiny view helpers (no XML / appcompat to keep the build minimal) ---

    private fun patternRow(name: String, pattern: HapticAudioPattern): LinearLayout {
        val schedule = AndroidHaptics.renderingSummary(pattern, renderer.scheduleHaptics(pattern, capabilities))
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    addView(
                        TextView(this@MainActivity).apply {
                            text = name
                            setTextColor(Color.parseColor("#E6E8EC"))
                            textSize = 16f
                        },
                    )
                    addView(
                        TextView(this@MainActivity).apply {
                            text = schedule
                            setTextColor(Color.parseColor("#8B90A0"))
                            textSize = 11f
                        },
                    )
                },
            )
            addView(
                Button(this@MainActivity).apply {
                    text = "Play"
                    setOnClickListener { play(pattern) }
                },
            )
        }
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
