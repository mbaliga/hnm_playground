package dev.hnm.workbench.android

import android.app.Activity
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.graphics.drawable.shapes.RoundRectShape
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.hnm.workbench.core.design.MaterialPreset
import dev.hnm.workbench.core.design.ModalSynth
import dev.hnm.workbench.core.design.MotionPrimitive
import dev.hnm.workbench.core.design.MotionPrimitives
import dev.hnm.workbench.core.design.ParameterNavigator
import dev.hnm.workbench.core.design.RhythmCapture
import dev.hnm.workbench.core.design.Tap
import dev.hnm.workbench.core.design.TextureField
import dev.hnm.workbench.core.design.TextureFieldType
import dev.hnm.workbench.core.design.TextureFields
import dev.hnm.workbench.core.design.Variations
import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.Envelope
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.core.playback.HapticCapabilities

// ---- HTML color constants (ported from --css-vars) ----
private val C_SCREEN   = Color.parseColor("#0B0B0B")
private val C_INK      = Color.parseColor("#EFEEEC")
private val C_INK_DIM  = Color.parseColor("#C8C7C4")
private val C_BAR      = Color.parseColor("#E9E9E6")
private val C_RED      = Color.parseColor("#E22C24")
private val C_HOUSING  = Color.parseColor("#E9E8E5")
private val C_HOUSE_EDGE = Color.parseColor("#D6D5D1")
private val C_FRAME    = Color.parseColor("#ECEBE8")
private val C_GRILLE   = Color.parseColor("#C3C2BF")
private val C_ICON     = Color.parseColor("#A9A8A5")
private val C_DOME_HI  = Color.parseColor("#FFFFFF")
private val C_DOME_LO  = Color.parseColor("#DEDCD8")
private val C_BATT_BG  = Color.parseColor("#0E0E0E")

/**
 * On-device player with the full recorder aesthetic:
 *   • Dark screen area (#0b0b0b) for pattern list / waveform
 *   • Light housing area with dome-style Play buttons
 *   • Battery badge + speaker grille in the chin
 *   • Red (#e22c24) active accent
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
            BuiltInPatterns.ALL.forEach { add(it.name to it) }
            add("Strong buzz (400 ms)" to strongBuzz())
            Variations.family(BuiltInPatterns.CONFIRM, count = 3).forEachIndexed { i, p ->
                add("Confirm · variation ${i + 1}" to p)
            }
            add("Captured rhythm" to capturedRhythm())
            MotionPrimitive.entries.forEach { p ->
                add("Motion · ${p.displayName}" to MotionPrimitives.toPattern(p))
            }
            texturePatterns().forEach { add(it) }
            navigatorPatterns().forEach { add(it) }
            MaterialPreset.entries.forEach { add("Material · ${it.displayName}" to ModalSynth.toPattern(it.material)) }
        }

        // ---- outer device shell (warm gray frame) ----
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(C_FRAME)
            // Rounded corners via outline (API 21+)
        }

        // ---- screen area (dark) ----
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(C_SCREEN)
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        // Status bar row
        screen.addView(statusBar())
        screen.addView(spacer(dp(12)))
        screen.addView(title("Haptics + Audio Player"))
        screen.addView(caption(diagnostics()))
        screen.addView(spacer(dp(10)))

        // Self-test button in screen area (outlined style)
        screen.addView(outlineButton("▶  Vibration self-test (strong)") {
            AndroidHaptics.selfTest(vibrator, handler) { toast(it) }
        })
        screen.addView(caption("Check Settings → Sound & vibration if you can't feel this."))
        screen.addView(spacer(dp(12)))

        // Pattern list rows
        patterns.forEach { (name, pattern) -> screen.addView(patternRow(name, pattern)) }

        shell.addView(screen, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // ---- chin row (battery + grille) ----
        shell.addView(chinRow(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // ---- housing / controls area (light) ----
        val housing = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(C_HOUSING)
            setPadding(dp(18), dp(16), dp(18), dp(22))
        }
        shell.addView(housing, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val scroll = ScrollView(this).apply {
            addView(shell)
            setBackgroundColor(C_FRAME)
        }
        setContentView(scroll)
    }

    override fun onDestroy() {
        audio.release()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun play(pattern: HapticAudioPattern) {
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

    private fun texturePatterns(): List<Pair<String, HapticAudioPattern>> {
        val roughnesses = listOf(0.2 to "smooth", 0.5 to "mid", 0.85 to "rough")
        val velocities = listOf(0.5 to "slow", 1.5 to "fast")
        return buildList {
            for (type in TextureFieldType.entries) {
                for ((r, rLabel) in roughnesses) {
                    val field = TextureField(type = type, roughness = r)
                    add("Texture · ${type.displayName} · $rLabel" to TextureFields.toPattern(field))
                }
            }
            val midPerlin = TextureField(type = TextureFieldType.PERLIN, roughness = 0.5)
            for ((v, vLabel) in velocities) {
                add("Texture · Perlin mid · $vLabel scrub" to TextureFields.toPattern(midPerlin, velocity = v))
            }
        }
    }

    private fun navigatorPatterns(): List<Pair<String, HapticAudioPattern>> = buildList {
        ParameterNavigator.textureFamilyPatterns(
            TextureField(type = TextureFieldType.PERLIN, roughness = 0.05),
            TextureField(type = TextureFieldType.PERLIN, roughness = 0.95),
            count = 5,
        ).forEachIndexed { i, p -> add("Navigate · texture ${i + 1}/5" to p) }
        ParameterNavigator.motionFamilyPatterns(MotionPrimitive.STIR, MotionPrimitive.SETTLE, count = 5)
            .forEachIndexed { i, p -> add("Navigate · motion ${i + 1}/5" to p) }
    }

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
        return "build v0.11 · vibrator ${if (c.hasVibrator) "present" else "ABSENT"}\n" +
            "actuator: ${AndroidHaptics.actuatorLabel(c)}\n" +
            "amplitude ${if (c.hasAmplitudeControl) "yes" else "no"} · primitives: $prims\n" +
            "predefined effects: $eff"
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // ---- view helpers matching the HTML components ----

    /** Status bar: red dot + app name + pattern count, on the dark screen. */
    private fun statusBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                View(this@MainActivity).apply {
                    // Red recording dot
                    background = ShapeDrawable(OvalShape()).also { s ->
                        s.paint.color = C_RED
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).also {
                        it.marginEnd = dp(8)
                    }
                }
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = "HAPTICS WORKBENCH"
                    setTextColor(C_INK_DIM)
                    textSize = 12f
                    letterSpacing = 0.12f
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                }
            )
        }
    }

    /**
     * Chin row: battery badge (black pill) + speaker grille (dot pattern).
     * Matches HTML .chinrow.
     */
    private fun chinRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(C_SCREEN) // still on screen in chin
            setPadding(dp(18), dp(14), dp(18), dp(14))

            // Battery badge
            addView(
                TextView(this@MainActivity).apply {
                    text = "▪ 89%"
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                    background = GradientDrawable().also { gd ->
                        gd.setColor(C_BATT_BG)
                        gd.cornerRadius = dp(11).toFloat()
                    }
                    setPadding(dp(12), dp(7), dp(12), dp(7))
                },
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also {
                    it.marginEnd = dp(14)
                },
            )

            // Speaker grille: a View with a dot-pattern background drawn via Canvas
            addView(
                GrilleView(this@MainActivity).apply {
                    dotColor = C_GRILLE
                    background = GradientDrawable().also { gd ->
                        gd.setColor(Color.TRANSPARENT)
                        gd.cornerRadius = dp(8).toFloat()
                    }
                },
                LinearLayout.LayoutParams(0, dp(34), 1f),
            )
        }
    }

    /** Pattern row: dark screen style — name + schedule, then a dome Play button. */
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
                            setTextColor(C_INK)
                            textSize = 15f
                        },
                    )
                    addView(
                        TextView(this@MainActivity).apply {
                            text = schedule
                            setTextColor(C_INK_DIM)
                            textSize = 11f
                        },
                    )
                },
            )

            // Dome-style Play button
            addView(domeButton("Play") { play(pattern) })
        }
    }

    /**
     * Dome button: matches the HTML .key + .dome style.
     * Light gray outer key → radial-gradient white dome → red glyph/text on top.
     */
    private fun domeButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setTextColor(C_ICON)
            textSize = 13f
            background = GradientDrawable().also { gd ->
                gd.gradientType = GradientDrawable.LINEAR_GRADIENT
                gd.orientation = GradientDrawable.Orientation.TOP_BOTTOM
                gd.colors = intArrayOf(
                    Color.parseColor("#EFEEEB"),
                    Color.parseColor("#E4E3DF"),
                )
                gd.cornerRadius = dp(14).toFloat()
                gd.setStroke(dp(1), C_HOUSE_EDGE)
            }
            elevation = dp(2).toFloat()
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { onClick() }
        }
    }

    /** Self-test outlined button: shows in the dark screen area. */
    private fun outlineButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setTextColor(C_INK)
            textSize = 13f
            background = GradientDrawable().also { gd ->
                gd.setColor(Color.TRANSPARENT)
                gd.cornerRadius = dp(10).toFloat()
                gd.setStroke(dp(1), C_INK_DIM)
            }
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { onClick() }
        }
    }

    private fun title(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(C_INK)
        textSize = 22f
        setTypeface(typeface, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }

    private fun caption(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(C_INK_DIM)
        textSize = 12f
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun spacer(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, height)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
