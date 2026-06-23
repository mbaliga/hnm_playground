package dev.hnm.workbench.core.design

import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.ControlPoint
import dev.hnm.workbench.core.ir.CurveParam
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.Interpolation
import dev.hnm.workbench.core.ir.ParameterCurve
import dev.hnm.workbench.core.ir.Seconds
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max

/**
 * Stage 2 of the visual/procedural authoring direction: scrubbable procedural texture fields
 * (Perlin/Worley/fBm/Value noise) with a velocity-driven playback law.
 *
 * Each field is a 1-D haptic signal source. Sampling it at position p gives a value in [0,1] that
 * maps to instantaneous HAPTIC_INTENSITY. The velocity parameter controls how fast the "finger" moves
 * across the field — higher velocity → more spatial ground per second → higher temporal frequency in
 * the resulting haptic stream. This is the phone-LRA analogue of the scrubbing-across-a-surface
 * interaction model used in surface-haptics research (HaTT, TPaD, TanvasTouch).
 *
 * Roughness maps to spatial frequency exponentially (1 Hz at 0, 64 Hz at 1) because the perceptual
 * roughness dimension is approximately log-scaled — consistent with the spatial-frequency→vibration-
 * frequency correspondence established in the multisensory texture literature (Bensmaïa & Hollins).
 */
enum class TextureFieldType(val displayName: String, val description: String) {
    PERLIN("Perlin", "smooth rolling turbulence"),
    WORLEY("Worley", "cellular spikes — gravel-like"),
    FBM("fBm", "fractal layers of natural detail"),
    VALUE("Value", "blocky stochastic texture"),
}

/**
 * A procedural texture field description. Instances are value objects — all generation is
 * stateless and deterministic given [seed].
 *
 * @param roughness 0..1 → spatial frequency via exponential mapping (1–64 cycles/unit)
 * @param density   Worley only: 0..1 → feature-point density within each cell (0 = sparse, 1 = dense)
 * @param octaves   fBm only: number of Perlin octaves to stack (more → finer multi-scale detail)
 * @param seed      repeatable noise seed
 */
data class TextureField(
    val type: TextureFieldType,
    val roughness: Double = 0.5,
    val density: Double = 0.5,
    val octaves: Int = 4,
    val seed: Int = 0,
) {
    val displayName: String
        get() = "${type.displayName} · ${(roughness * 100).toInt()}%"
}

object TextureFields {

    private const val CURVE_SAMPLE_RATE = 1000

    // Exponential roughness→frequency: 1 Hz at r=0, 64 Hz at r=1.
    private fun spatialFreq(roughness: Double): Double = exp(roughness * ln(64.0))

    // Rougher fields feel crisper (high sharpness) on the phone actuator.
    internal fun sharpness(roughness: Double): Double = (0.2 + roughness * 0.75).coerceIn(0.0, 1.0)

    /**
     * Sample the field at [position] (arbitrary spatial units) → haptic intensity in [0,1].
     * Calling this repeatedly at positions `t * velocity` simulates scrubbing at [velocity] units/sec.
     */
    fun sample(field: TextureField, position: Double): Double {
        val x = position * spatialFreq(field.roughness)
        return when (field.type) {
            TextureFieldType.PERLIN -> perlin(x, field.seed)
            TextureFieldType.WORLEY -> worley(x, field.density, field.seed)
            TextureFieldType.FBM -> fbm(x, field.octaves, field.seed)
            TextureFieldType.VALUE -> valueNoise(x, field.seed)
        }
    }

    /**
     * Simulate scrubbing the field for [duration] seconds at [velocity] spatial units/sec.
     * Returns a [MotionCurve] with values in [0,1] (suitable for sparklines and intensity curves).
     */
    fun curve(field: TextureField, duration: Double = 0.5, velocity: Double = 1.0): MotionCurve {
        val n = max(2, (duration * CURVE_SAMPLE_RATE).toInt())
        val samples = DoubleArray(n) { i ->
            val t = duration * i / (n - 1)
            sample(field, t * velocity)
        }
        return MotionCurve(CURVE_SAMPLE_RATE, samples)
    }

    /**
     * Convert a texture field scrub into a playable [HapticAudioPattern].
     * The pattern is a single [Continuous] event whose HAPTIC_INTENSITY breakpoint curve follows
     * the texture field sampled at [velocity] units/sec for [duration] seconds.
     */
    fun toPattern(
        field: TextureField,
        duration: Double = 0.5,
        velocity: Double = 1.0,
    ): HapticAudioPattern {
        val c = curve(field, duration, velocity)
        val points = 80
        val cps = (0 until points).map { k ->
            val t = duration * k / (points - 1)
            ControlPoint(t, c.valueAt(t))
        }
        val intensityCurve = ParameterCurve(CurveParam.HAPTIC_INTENSITY, cps, Interpolation.SMOOTH)
        val event = Continuous(
            time = 0.0,
            duration = duration,
            intensity = 1.0,
            sharpness = sharpness(field.roughness),
        )
        val track = HapticTrack(id = "h1", events = listOf(event), curves = listOf(intensityCurve))
        return HapticAudioPattern(name = field.displayName, tracks = listOf(track))
    }

    /** One representative field per type with default parameters — the starter texture palette. */
    fun all(): List<TextureField> = TextureFieldType.entries.map { TextureField(type = it) }

    // -------------------------------------------------------------------------
    // Noise generators — all return values in [0, 1]
    // -------------------------------------------------------------------------

    // Quintic fade — C² continuity (Perlin's improved 2002 formula).
    private fun fade(t: Double): Double = t * t * t * (t * (t * 6 - 15) + 10)

    private fun lerp(a: Double, b: Double, t: Double) = a + t * (b - a)

    // Integer mixing hash (no floating-point dependencies → identical results on all JVMs).
    private fun ihash(n: Int, seed: Int): Int {
        var h = n * 1664525 + seed * 1013904223
        h = h xor (h ushr 16)
        h *= 0x45d9f3b
        h = h xor (h ushr 16)
        return h
    }

    // Pseudo-random in [0, 1].
    private fun rnd(ix: Int, seed: Int): Double {
        val h = ihash(ix, seed) and 0x7FFFFFFF
        return h.toDouble() / 0x7FFFFFFF
    }

    // Gradient ±1 at each lattice point.
    private fun grad(ix: Int, seed: Int): Double =
        if (ihash(ix, seed) and 1 == 0) 1.0 else -1.0

    /**
     * 1-D Perlin gradient noise → [0, 1].
     * Raw range ≈ [-0.7, 0.7] (tight when only two gradients contribute); remapped symmetrically.
     */
    private fun perlin(x: Double, seed: Int): Double {
        val ix = floor(x).toInt()
        val fx = x - ix
        val t = fade(fx)
        val g0 = grad(ix, seed) * fx
        val g1 = grad(ix + 1, seed) * (fx - 1.0)
        val raw = lerp(g0, g1, t) // ≈ [-0.7, 0.7]
        return (raw / 0.7 * 0.5 + 0.5).coerceIn(0.0, 1.0)
    }

    /**
     * 1-D value noise → [0, 1].
     * Smoother appearance than Perlin (no gradient discontinuities), useful for broad envelope shapes.
     */
    private fun valueNoise(x: Double, seed: Int): Double {
        val ix = floor(x).toInt()
        val fx = x - ix
        val t = fade(fx)
        return lerp(rnd(ix, seed), rnd(ix + 1, seed), t)
    }

    /**
     * 1-D Worley (cellular / Voronoi) noise → [0, 1].
     * Feature points are scattered randomly within each unit cell; the intensity is highest near
     * each point (inverted distance), giving a spiky, gravel-like character.
     * [density] (0..1) shifts how many cells the search window spans — denser = more spikes.
     */
    private fun worley(x: Double, density: Double, seed: Int): Double {
        // Map density 0..1 → scale 1..8 (cells per unit).
        val scale = 1.0 + density * 7.0
        val xs = x * scale
        val ix = floor(xs).toInt()
        var minDist = Double.MAX_VALUE
        for (k in (ix - 2)..(ix + 2)) {
            val fp = k + rnd(k, seed) // feature-point position in scaled space
            val d = abs(xs - fp)
            if (d < minDist) minDist = d
        }
        // minDist ∈ [0, ~1]. Invert so near feature point = 1 (high intensity).
        return (1.0 - minDist.coerceIn(0.0, 1.0))
    }

    /**
     * 1-D fractal Brownian motion (fBm) → [0, 1].
     * Sums [octaves] Perlin octaves with lacunarity=2, gain=0.5. More octaves → richer multi-scale
     * detail. Each octave uses a per-octave seed offset to prevent phase alignment between layers.
     */
    private fun fbm(x: Double, octaves: Int, seed: Int): Double {
        var value = 0.0
        var amplitude = 0.5
        var frequency = 1.0
        var norm = 0.0
        repeat(octaves) { i ->
            // perlin() returns [0,1]; re-centre to [-1,1] for proper signed fBm mixing.
            value += (perlin(x * frequency, seed + i * 1237) * 2.0 - 1.0) * amplitude
            norm += amplitude
            frequency *= 2.0
            amplitude *= 0.5
        }
        return (value / norm * 0.5 + 0.5).coerceIn(0.0, 1.0)
    }
}
