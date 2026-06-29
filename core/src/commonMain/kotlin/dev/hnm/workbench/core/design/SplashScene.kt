package dev.hnm.workbench.core.design

import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.ControlPoint
import dev.hnm.workbench.core.ir.CurveParam
import dev.hnm.workbench.core.ir.Envelope
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.Interpolation
import dev.hnm.workbench.core.ir.OscEvent
import dev.hnm.workbench.core.ir.AudioTrack
import dev.hnm.workbench.core.ir.ParameterCurve
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.ir.Waveform

/** The four procedural splash visuals. Each launch picks one; its [SplashScene] carries the matching feel. */
enum class SplashVisual { RIPPLE, BLOOM, SWEEP, SPARK }

/**
 * One launch's splash: a visual style plus the single [HapticAudioPattern] that drives *both* the sound
 * and the haptics, and the [beats] (haptic event times) the visual animates against. Because the felt
 * taps, the audio, and the drawn animation all come from this one timeline, they are coincident by
 * construction — not three things hand-synced.
 */
data class SplashScene(
    val title: String,
    val visual: SplashVisual,
    val pattern: HapticAudioPattern,
    val beats: List<Double>,
    val durationSeconds: Double,
    val seed: Int,
)

/**
 * Procedural splash motifs. [generate] is deterministic in [seed] so a launch's visual, sound and
 * haptics always match; vary the seed (e.g. a launch counter or wall-clock) for a different intro each
 * time. Patterns are short brand "stings" (~1.6–2.2 s) authored from the same IR everything else uses,
 * so they render to audio + the real actuator with no special-casing.
 */
object SplashMotifs {

    private val VISUALS = SplashVisual.entries

    fun generate(seed: Int): SplashScene = when (VISUALS[seed.mod(VISUALS.size)]) {
        SplashVisual.RIPPLE -> ripple(seed)
        SplashVisual.BLOOM -> bloom(seed)
        SplashVisual.SWEEP -> sweep(seed)
        SplashVisual.SPARK -> spark(seed)
    }

    /** A short list of distinct scenes (one per visual) — handy for previews/tests. */
    fun all(): List<SplashScene> = VISUALS.indices.map { generate(it) }

    // --- RIPPLE: three rising taps blooming into a soft swell -----------------
    private fun ripple(seed: Int): SplashScene {
        val beats = listOf(0.0, 0.34, 0.66)
        val haptics = beats.mapIndexed { i, t ->
            Transient(time = t, intensity = 0.55 + 0.18 * i, sharpness = 0.45 + 0.22 * i)
        } + Continuous(
            time = 0.9, duration = 0.7, intensity = 0.5, sharpness = 0.3,
            envelope = Envelope(attack = 0.12, sustain = 1.0, release = 0.45),
        )
        val audio = beats.mapIndexed { i, t ->
            osc(t, 0.18, Waveform.SINE, 120.0 * (i + 1), gain = 0.5)
        } + osc(0.9, 0.7, Waveform.TRIANGLE, 220.0, gain = 0.35, attack = 0.12, release = 0.4)
        return scene("Ripple", SplashVisual.RIPPLE, haptics, audio, beats, dur = 1.9, seed)
    }

    // --- BLOOM: a swell that grows a waveform from the centre, capped by an accent ---
    private fun bloom(seed: Int): SplashScene {
        // Beats are the haptic event times the visual animates against (swell onset + landing accent).
        val beats = listOf(0.0, 1.45)
        val swell = Continuous(
            time = 0.0, duration = 1.2, intensity = 0.7, sharpness = 0.4,
            envelope = Envelope(attack = 0.35, decay = 0.2, sustain = 0.8, release = 0.3),
        )
        val accent = Transient(time = 1.45, intensity = 0.95, sharpness = 0.85)
        val intensityCurve = ParameterCurve(
            parameter = CurveParam.HAPTIC_INTENSITY,
            points = listOf(ControlPoint(0.0, 0.1), ControlPoint(1.0, 1.0), ControlPoint(1.2, 0.6)),
            interpolation = Interpolation.SMOOTH,
        )
        val haptic = HapticTrack(id = "h1", events = listOf(swell, accent), curves = listOf(intensityCurve))
        val audio = listOf(
            osc(0.0, 1.2, Waveform.SINE, 110.0, gain = 0.4, attack = 0.35, release = 0.3),
            osc(0.0, 1.2, Waveform.SINE, 165.0, gain = 0.25, attack = 0.4, release = 0.3),
            osc(1.45, 0.3, Waveform.TRIANGLE, 330.0, gain = 0.5, attack = 0.005, release = 0.2),
        )
        val pattern = HapticAudioPattern(
            name = "Splash · Bloom",
            tracks = listOf(haptic, AudioTrack(id = "a1", events = audio)),
        )
        return SplashScene("Bloom", SplashVisual.BLOOM, pattern, beats, durationSeconds = 2.0, seed)
    }

    // --- SWEEP: a playhead sweep ticking across, then a landing click ---------
    private fun sweep(seed: Int): SplashScene {
        val ticks = (0..5).map { it * 0.22 }
        val landing = 1.4
        val beats = ticks + landing
        val haptics = ticks.map { t -> Transient(time = t, intensity = 0.4, sharpness = 0.9) } +
            Transient(time = landing, intensity = 0.95, sharpness = 0.6)
        val audio = ticks.map { t -> osc(t, 0.05, Waveform.SQUARE, 660.0, gain = 0.25, attack = 0.002, release = 0.03) } +
            osc(landing, 0.25, Waveform.TRIANGLE, 196.0, gain = 0.5, attack = 0.004, release = 0.18)
        return scene("Sweep", SplashVisual.SWEEP, haptics, audio, beats, dur = 1.9, seed)
    }

    // --- SPARK: a strike that bursts into decaying sparks ---------------------
    private fun spark(seed: Int): SplashScene {
        val beats = listOf(0.2, 0.55, 0.92)
        val haptics = listOf(
            Transient(time = 0.2, intensity = 1.0, sharpness = 0.8),
            Transient(time = 0.55, intensity = 0.6, sharpness = 0.55),
            Transient(time = 0.92, intensity = 0.4, sharpness = 0.4),
        )
        val audio = listOf(
            osc(0.2, 0.3, Waveform.TRIANGLE, 300.0, gain = 0.55, attack = 0.002, release = 0.22),
            osc(0.55, 0.25, Waveform.SINE, 200.0, gain = 0.35, attack = 0.004, release = 0.2),
            osc(0.92, 0.4, Waveform.SINE, 140.0, gain = 0.3, attack = 0.01, release = 0.35),
        )
        return scene("Spark", SplashVisual.SPARK, haptics, audio, beats, dur = 1.8, seed)
    }

    // --- helpers --------------------------------------------------------------

    private fun scene(
        title: String,
        visual: SplashVisual,
        haptics: List<dev.hnm.workbench.core.ir.HapticEvent>,
        audio: List<OscEvent>,
        beats: List<Double>,
        dur: Double,
        seed: Int,
    ): SplashScene {
        val pattern = HapticAudioPattern(
            name = "Splash · $title",
            tracks = listOf(
                HapticTrack(id = "h1", events = haptics.sortedBy { it.time }),
                AudioTrack(id = "a1", events = audio),
            ),
        )
        return SplashScene(title, visual, pattern, beats.sorted(), dur, seed)
    }

    private fun osc(
        time: Double,
        duration: Double,
        waveform: Waveform,
        frequencyHz: Double,
        gain: Double,
        attack: Double = 0.005,
        release: Double = 0.05,
    ) = OscEvent(
        time = time,
        duration = duration,
        waveform = waveform,
        frequencyHz = frequencyHz,
        gain = gain,
        envelope = Envelope(attack = attack, release = release),
    )
}
