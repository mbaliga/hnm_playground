package dev.hnm.workbench.core.design

import dev.hnm.workbench.core.ir.AudioTrack
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.ControlPoint
import dev.hnm.workbench.core.ir.CurveParam
import dev.hnm.workbench.core.ir.Envelope
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticEvent
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.Interpolation
import dev.hnm.workbench.core.ir.OscEvent
import dev.hnm.workbench.core.ir.ParameterCurve
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.ir.Waveform
import kotlin.math.sin

/** The five procedural splash visuals. Each launch picks one; its [SplashScene] carries the matching feel. */
enum class SplashVisual { RIPPLE, BLOOM, SWEEP, SPARK, LATTICE }

/**
 * One launch's splash: a visual style plus the single [HapticAudioPattern] that drives *both* the sound
 * and the haptics, and the [beats] (haptic event times) the visual animates against. Because the felt
 * taps, the audio, and the drawn animation all come from this one timeline, they are coincident by
 * construction — not three things hand-synced.
 *
 * [paletteMix] (0 = violet/radium-leaning, 1 = cold-cyan-leaning) and [voice] (which [MaterialPreset]
 * colored this launch's audible/felt timbre, via [ModalSynth]'s real fundamental/ring-time model) are
 * both deterministic in [seed] — splash v2's "point in a motif space" rather than one of four fixed
 * clips (UX brief §4.1).
 */
data class SplashScene(
    val title: String,
    val visual: SplashVisual,
    val pattern: HapticAudioPattern,
    val beats: List<Double>,
    val durationSeconds: Double,
    val seed: Int,
    val paletteMix: Double = 0.0,
    val voice: MaterialPreset = MaterialPreset.GLASS,
)

/**
 * Procedural splash motifs. [generate] is deterministic in [seed] so a launch's visual, sound, haptics,
 * palette lean, and material voice always match; vary the seed (e.g. a launch counter or wall-clock) for
 * a different intro each time. Patterns are short brand "stings" (~1.6–2.0 s) authored from the same IR
 * everything else uses, so they render to audio + the real actuator with no special-casing.
 */
object SplashMotifs {

    private val VISUALS = SplashVisual.entries
    private val VOICES = MaterialPreset.entries

    fun generate(seed: Int): SplashScene {
        val paletteMix = hash01(seed * 7 + 3)
        val voice = VOICES[hash01(seed * 13 + 5).let { (it * VOICES.size).toInt() }.coerceIn(0, VOICES.size - 1)]
        val base = when (VISUALS[seed.mod(VISUALS.size)]) {
            SplashVisual.RIPPLE -> ripple(seed, voice)
            SplashVisual.BLOOM -> bloom(seed, voice)
            SplashVisual.SWEEP -> sweep(seed, voice)
            SplashVisual.SPARK -> spark(seed, voice)
            SplashVisual.LATTICE -> lattice(seed, voice)
        }
        return base.copy(paletteMix = paletteMix, voice = voice)
    }

    /** A short list of distinct scenes (one per visual) — handy for previews/tests. */
    fun all(): List<SplashScene> = VISUALS.indices.map { generate(it) }

    // --- RIPPLE: three rising taps blooming into a soft swell -----------------
    private fun ripple(seed: Int, voice: MaterialPreset): SplashScene {
        val f0 = voiceHz(voice)
        val beats = listOf(0.0, 0.34, 0.66)
        val haptics = beats.mapIndexed { i, t ->
            Transient(time = t, intensity = 0.55 + 0.18 * i, sharpness = 0.45 + 0.22 * i)
        } + Continuous(
            time = 0.9, duration = 0.7, intensity = 0.5, sharpness = 0.3,
            envelope = Envelope(attack = 0.12, sustain = 1.0, release = voiceRelease(voice)),
        )
        val audio = beats.mapIndexed { i, t ->
            osc(t, 0.18, Waveform.SINE, f0 * 0.6 * (i + 1), gain = 0.5)
        } + osc(0.9, 0.7, Waveform.TRIANGLE, f0, gain = 0.35, attack = 0.12, release = voiceRelease(voice))
        return scene("Ripple", SplashVisual.RIPPLE, haptics, audio, beats, dur = 1.9, seed)
    }

    // --- BLOOM: a swell that grows a waveform from the centre, capped by an accent ---
    private fun bloom(seed: Int, voice: MaterialPreset): SplashScene {
        val f0 = voiceHz(voice)
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
            osc(0.0, 1.2, Waveform.SINE, f0 * 0.5, gain = 0.4, attack = 0.35, release = 0.3),
            osc(0.0, 1.2, Waveform.SINE, f0 * 0.75, gain = 0.25, attack = 0.4, release = 0.3),
            osc(1.45, 0.3, Waveform.TRIANGLE, f0 * 1.5, gain = 0.5, attack = 0.005, release = voiceRelease(voice)),
        )
        val pattern = HapticAudioPattern(
            name = "Splash · Bloom",
            tracks = listOf(haptic, AudioTrack(id = "a1", events = audio)),
        )
        return SplashScene("Bloom", SplashVisual.BLOOM, pattern, beats, durationSeconds = 2.0, seed)
    }

    // --- SWEEP: a playhead sweep ticking across, then a landing click ---------
    private fun sweep(seed: Int, voice: MaterialPreset): SplashScene {
        val f0 = voiceHz(voice)
        val ticks = (0..5).map { it * 0.22 }
        val landing = 1.4
        val beats = ticks + landing
        val haptics = ticks.map { t -> Transient(time = t, intensity = 0.4, sharpness = 0.9) } +
            Transient(time = landing, intensity = 0.95, sharpness = 0.6)
        val audio = ticks.map { t -> osc(t, 0.05, Waveform.SQUARE, f0 * 3, gain = 0.25, attack = 0.002, release = 0.03) } +
            osc(landing, 0.25, Waveform.TRIANGLE, f0 * 0.9, gain = 0.5, attack = 0.004, release = voiceRelease(voice))
        return scene("Sweep", SplashVisual.SWEEP, haptics, audio, beats, dur = 1.9, seed)
    }

    // --- SPARK: a strike that bursts into decaying sparks ---------------------
    private fun spark(seed: Int, voice: MaterialPreset): SplashScene {
        val f0 = voiceHz(voice)
        val beats = listOf(0.2, 0.55, 0.92)
        val haptics = listOf(
            Transient(time = 0.2, intensity = 1.0, sharpness = 0.8),
            Transient(time = 0.55, intensity = 0.6, sharpness = 0.55),
            Transient(time = 0.92, intensity = 0.4, sharpness = 0.4),
        )
        val audio = listOf(
            osc(0.2, 0.3, Waveform.TRIANGLE, f0 * 1.4, gain = 0.55, attack = 0.002, release = voiceRelease(voice)),
            osc(0.55, 0.25, Waveform.SINE, f0 * 0.9, gain = 0.35, attack = 0.004, release = 0.2),
            osc(0.92, 0.4, Waveform.SINE, f0 * 0.65, gain = 0.3, attack = 0.01, release = 0.35),
        )
        return scene("Spark", SplashVisual.SPARK, haptics, audio, beats, dur = 1.8, seed)
    }

    // --- LATTICE: energy propagating across the dot-grid substrate itself -----
    private fun lattice(seed: Int, voice: MaterialPreset): SplashScene {
        val f0 = voiceHz(voice)
        // A short burst walk (4 cells) across an imagined grid row, then a settle on the last cell.
        val beats = listOf(0.0, 0.16, 0.32, 0.48, 0.85)
        val haptics = beats.dropLast(1).mapIndexed { i, t ->
            Transient(time = t, intensity = 0.5 + 0.1 * i, sharpness = 0.75)
        } + Continuous(
            time = 0.85, duration = 0.55, intensity = 0.45, sharpness = 0.35,
            envelope = Envelope(attack = 0.05, sustain = 0.7, release = voiceRelease(voice)),
        )
        val audio = beats.dropLast(1).mapIndexed { i, t ->
            osc(t, 0.06, Waveform.SQUARE, f0 * (1.0 + 0.2 * i), gain = 0.3, attack = 0.001, release = 0.04)
        } + osc(0.85, 0.5, Waveform.SINE, f0 * 0.7, gain = 0.35, attack = 0.05, release = voiceRelease(voice))
        return scene("Lattice", SplashVisual.LATTICE, haptics, audio, beats, dur = 1.7, seed)
    }

    // --- helpers --------------------------------------------------------------

    private fun scene(
        title: String,
        visual: SplashVisual,
        haptics: List<HapticEvent>,
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

    /** A splash-scaled voice frequency — the real material fundamental, folded into an audible splash range. */
    private fun voiceHz(voice: MaterialPreset): Double {
        val fundamental = ModalSynth.fundamentalHz(voice.material)
        // fundamentalHz ranges ~120..1400 Hz; fold into a pleasant ~150..420 Hz splash-tone range.
        return 150.0 + (fundamental / 1400.0).coerceIn(0.0, 1.0) * 270.0
    }

    /** A splash-scaled release time — the real material ring time, capped so it never overruns the splash. */
    private fun voiceRelease(voice: MaterialPreset): Double =
        (ModalSynth.ringSeconds(voice.material) * 0.18).coerceIn(0.05, 0.45)

    /** Deterministic [0,1) hash for procedural variation — same construction as [SplashGeometry]'s. */
    private fun hash01(i: Int): Double {
        val x = sin(i * 12.9898) * 43758.5453
        return x - kotlin.math.floor(x)
    }
}
