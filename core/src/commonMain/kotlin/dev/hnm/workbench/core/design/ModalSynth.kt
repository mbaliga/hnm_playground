package dev.hnm.workbench.core.design

import dev.hnm.workbench.core.ir.AudioTrack
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.ControlPoint
import dev.hnm.workbench.core.ir.CurveParam
import dev.hnm.workbench.core.ir.Envelope
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.Interpolation
import dev.hnm.workbench.core.ir.OscEvent
import dev.hnm.workbench.core.ir.ParameterCurve
import dev.hnm.workbench.core.ir.Seconds
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.ir.Waveform
import kotlin.math.exp
import kotlin.math.ln

/**
 * Stage 4 of the visual/procedural authoring direction (see docs/AUTHORING-INTERFACES.md): physics /
 * material-property authoring with sound + haptics unified through one model.
 *
 * The mature technique is **modal synthesis**: a struck object rings in a set of vibration modes, each
 * an exponentially-decaying sinusoid (frequency, decay rate, amplitude). The same modal signal is the
 * contact *sound* (audible sinusoids) and the contact *haptic* (its energy envelope on the actuator) —
 * the deepest justification for a combined editor (FoleyAutomatic, van den Doel et al., SIGGRAPH '01).
 *
 * Four material handles drive the modes, so one edit co-varies both channels:
 *  - **stiffness** → raises modal pitch (stiffer = higher fundamental),
 *  - **density**  → lowers pitch (heavier = lower),
 *  - **damping**  → shortens decay (more damping = shorter ring; metal long, rubber dead),
 *  - **brightness** → boosts higher modes' amplitude and lengthens the audible spectrum.
 *
 * Modal frequencies/decays are functions of stiffness and damping, so a stiffness slider directly
 * tunes pitch and a damping slider directly tunes ring-down — the natural-mapping promise, now in the
 * physics domain. The decay is approximated as a linear envelope per mode (the renderer's ADSR), which
 * is enough to make ring *time* — the perceptually dominant cue — co-vary with the material.
 */
data class Material(
    val name: String,
    val stiffness: Double = 0.5, // 0..1
    val density: Double = 0.5, // 0..1
    val damping: Double = 0.4, // 0..1
    val brightness: Double = 0.5, // 0..1
)

/** A named library of canonical materials — the starter palette for the editor / Android player. */
enum class MaterialPreset(val displayName: String, val material: Material) {
    WOOD("Wood", Material("Wood", stiffness = 0.5, density = 0.55, damping = 0.5, brightness = 0.4)),
    METAL("Metal", Material("Metal", stiffness = 0.9, density = 0.6, damping = 0.07, brightness = 0.85)),
    GLASS("Glass", Material("Glass", stiffness = 0.95, density = 0.4, damping = 0.12, brightness = 0.95)),
    RUBBER("Rubber", Material("Rubber", stiffness = 0.2, density = 0.7, damping = 0.92, brightness = 0.15)),
    CERAMIC("Ceramic", Material("Ceramic", stiffness = 0.8, density = 0.55, damping = 0.22, brightness = 0.7)),
}

/** One vibration mode: a decaying sinusoid. */
data class Mode(val freqHz: Double, val decaySeconds: Seconds, val amplitude: Double)

object ModalSynth {

    // Inharmonic ratios of a struck bar/plate (slightly stretched from the harmonic series).
    private val MODE_RATIOS = doubleArrayOf(1.0, 2.04, 3.18, 4.42, 5.78, 7.22)

    /** The fundamental (first-mode) frequency for a material, from stiffness (↑) and density (↓). */
    fun fundamentalHz(m: Material): Double {
        // ~120 Hz (soft & dense) → ~1400 Hz (stiff & light), exponential to match pitch perception.
        val x = (m.stiffness * 0.8 + (1.0 - m.density) * 0.4).coerceIn(0.0, 1.2)
        return 120.0 * exp(x / 1.2 * ln(1400.0 / 120.0))
    }

    /** The overall ring time (the dominant/first mode's decay), from damping. Metal long, rubber dead. */
    fun ringSeconds(m: Material): Seconds {
        // damping 0 → ~2.2 s, damping 1 → ~0.05 s, exponential.
        return 2.2 * exp(m.damping.coerceIn(0.0, 1.0) * ln(0.05 / 2.2))
    }

    /**
     * The vibration modes for a material. Brighter materials sound through more modes with a slower
     * amplitude falloff; higher modes always decay faster than lower ones (physical).
     */
    fun modes(m: Material, maxModes: Int = MODE_RATIOS.size): List<Mode> {
        val f0 = fundamentalHz(m)
        val tau0 = ringSeconds(m)
        val falloff = 0.45 + m.brightness * 0.5 // 0.45 (dull) .. 0.95 (bright)
        val raw = (0 until maxModes.coerceIn(1, MODE_RATIOS.size)).map { i ->
            val freq = f0 * MODE_RATIOS[i]
            // Higher modes decay faster; brighter materials hold them a touch longer.
            val decay = (tau0 / (1.0 + i * (1.2 - m.brightness * 0.6))).coerceAtLeast(0.02)
            val amp = falloff.pow(i)
            Mode(freq, decay, amp)
        }
        // Keep only modes below Nyquist-ish audibility and with meaningful amplitude.
        val audible = raw.filter { it.freqHz < 18_000.0 && it.amplitude > 0.02 }
        val norm = audible.sumOf { it.amplitude }.takeIf { it > 1e-9 } ?: 1.0
        return audible.map { it.copy(amplitude = it.amplitude / norm) }
    }

    /**
     * Render a material strike to a pattern with both channels from the *same* modes:
     *  - audio: one decaying SINE [OscEvent] per mode (the contact sound),
     *  - haptics: an impact [Transient] plus a [Continuous] ring whose intensity follows the modal
     *    energy decay (the felt thud + ring-down).
     */
    fun toPattern(m: Material, name: String = m.name): HapticAudioPattern {
        val modes = modes(m)
        val ring = ringSeconds(m)

        val oscEvents = modes.map { mode ->
            OscEvent(
                time = 0.0,
                duration = 0.002 + mode.decaySeconds, // attack + linear decay tail
                waveform = Waveform.SINE,
                frequencyHz = mode.freqHz,
                gain = (mode.amplitude * 0.9).coerceIn(0.0, 1.0),
                envelope = Envelope(attack = 0.002, decay = mode.decaySeconds, sustain = 0.0, release = 0.0),
            )
        }
        val audioTrack = AudioTrack(id = "a1", events = oscEvents)

        // Felt channel: a crisp impact, then a ring-down that mirrors the material's decay.
        val sharpness = (0.2 + m.brightness * 0.7).coerceIn(0.0, 1.0)
        val impact = Transient(time = 0.0, intensity = 1.0, sharpness = sharpness)
        val hapticRing = ring.coerceIn(0.04, 1.2)
        val continuous = Continuous(
            time = 0.0,
            duration = hapticRing,
            intensity = 0.9,
            sharpness = sharpness,
            envelope = Envelope(),
        )
        val hapticTrack = HapticTrack(
            id = "h1",
            events = listOf(impact, continuous),
            curves = listOf(energyCurve(hapticRing)),
        )

        return HapticAudioPattern(name = name, tracks = listOf(hapticTrack, audioTrack))
    }

    /** All five preset materials as patterns — the starter palette. */
    fun all(): List<HapticAudioPattern> = MaterialPreset.entries.map { toPattern(it.material) }

    // --- helpers -------------------------------------------------------------

    /** An exponential ring-down intensity curve over [ringSeconds] (impact energy decaying to rest). */
    private fun energyCurve(ringSeconds: Seconds, points: Int = 40): ParameterCurve {
        val tau = (ringSeconds * 0.4).coerceAtLeast(0.01) // perceptual decay constant of the felt ring
        val pts = (0 until points).map { k ->
            val t = ringSeconds * k / (points - 1)
            ControlPoint(time = t, value = exp(-t / tau).coerceIn(0.0, 1.0))
        }
        return ParameterCurve(CurveParam.HAPTIC_INTENSITY, pts, Interpolation.SMOOTH)
    }

    private fun Double.pow(n: Int): Double {
        var r = 1.0
        repeat(n) { r *= this }
        return r
    }
}
