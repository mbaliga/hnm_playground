package dev.hnm.workbench.core.design

import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.ControlPoint
import dev.hnm.workbench.core.ir.CurveParam
import dev.hnm.workbench.core.ir.Envelope
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.Interpolation
import dev.hnm.workbench.core.ir.ParameterCurve
import dev.hnm.workbench.core.ir.Seconds
import dev.hnm.workbench.core.ir.Transient
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stage 1 of the visual/procedural authoring direction (see docs/AUTHORING-INTERFACES.md): a named,
 * physically-grounded vocabulary of *motion primitives* that generate a single value-over-time curve.
 * Because an animation curve **is** an envelope, that one curve is both a visual motion (for an
 * on-screen preview) and a haptic envelope — the "one signal, two modalities" promise.
 *
 * Each primitive is backed by deterministic spring-mass-damper / shaping math (no perceptual guesswork:
 * the curve is the envelope), with parameters that map to feel as the design literature describes —
 * stiffness → onset/decay rate, damping → number of felt oscillations, initial velocity → amplitude.
 *
 * Oscillatory primitives render as transients at the motion's peaks (felt as discrete taps, and so
 * playable on any actuator, including basic on/off ones); sustained "swell" primitives render as a
 * [Continuous] event whose intensity follows the curve.
 */
enum class MotionPrimitive(val displayName: String, val description: String) {
    BREATH("Breath", "a slow swell in and back out"),
    STIR("Stir", "a soft, rolling oscillation that builds then eases"),
    REFORM("Reform", "rises and resolves into a held shape"),
    REACH("Reach", "extends out and arrives"),
    ERUPT("Erupt", "a sharp burst that rings out"),
    COALESCE("Coalesce", "scattered taps accelerating into one"),
    GIVE("Give", "a soft yielding bump, like pressing into foam"),
    SETTLE("Settle", "a bouncing impact decaying to rest"),
}

/** A single felt peak of a motion: when, how strong (0..1), and how crisp (0..1). */
data class MotionPeak(val time: Seconds, val magnitude: Double, val sharpness: Double)

/**
 * The four spring-mass-damper parameters behind an oscillatory motion primitive. Exposed so Stage-3
 * parameter navigation ([ParameterNavigator]) can interpolate between two primitives in this small
 * continuous space — naturalHz/damping/x0/v0 are exactly the "latent vector" of an oscillatory feel.
 */
data class SpringParams(val naturalHz: Double, val damping: Double, val x0: Double, val v0: Double)

/**
 * A sampled value-over-time motion curve. [samples] are signed (a spring swings either side of its
 * target); the rectified magnitude is what drives haptic intensity. Sampled densely so it can be both
 * drawn (animation/envelope preview) and analysed for peaks.
 */
class MotionCurve(val sampleRate: Int, val samples: DoubleArray) {
    val durationSeconds: Seconds get() = samples.size.toDouble() / sampleRate

    fun valueAt(time: Seconds): Double {
        if (samples.isEmpty()) return 0.0
        val x = time * sampleRate
        val i = x.toInt()
        if (i < 0) return samples.first()
        if (i >= samples.size - 1) return samples.last()
        val frac = x - i
        return samples[i] + (samples[i + 1] - samples[i]) * frac
    }

    /** Local maxima of the rectified curve above [minMagnitude], as fractions of the peak magnitude. */
    fun peaks(minMagnitude: Double = 0.06): List<Pair<Seconds, Double>> {
        if (samples.isEmpty()) return emptyList()
        val out = mutableListOf<Pair<Seconds, Double>>()
        val mag = DoubleArray(samples.size) { abs(samples[it]) }
        for (i in samples.indices) {
            val prev = if (i == 0) Double.NEGATIVE_INFINITY else mag[i - 1]
            val next = if (i == samples.size - 1) Double.NEGATIVE_INFINITY else mag[i + 1]
            if (mag[i] >= prev && mag[i] > next && mag[i] >= minMagnitude) {
                out += (i.toDouble() / sampleRate) to mag[i]
            }
        }
        return out
    }
}

object MotionPrimitives {

    private const val SAMPLE_RATE = 1000

    /** Spring parameters for each oscillatory primitive — the continuous space Stage 3 navigates. */
    val SPRINGS: Map<MotionPrimitive, SpringParams> = mapOf(
        MotionPrimitive.STIR to SpringParams(naturalHz = 5.0, damping = 0.14, x0 = 0.0, v0 = 1.0),
        MotionPrimitive.REACH to SpringParams(naturalHz = 8.0, damping = 0.32, x0 = 0.0, v0 = 1.0),
        MotionPrimitive.ERUPT to SpringParams(naturalHz = 18.0, damping = 0.11, x0 = 0.0, v0 = 2.2),
        MotionPrimitive.GIVE to SpringParams(naturalHz = 7.0, damping = 0.55, x0 = 1.0, v0 = 0.0),
        MotionPrimitive.SETTLE to SpringParams(naturalHz = 14.0, damping = 0.17, x0 = 1.0, v0 = 0.0),
    )

    /** The spring parameters for [p], or null if it isn't an oscillatory spring primitive. */
    fun springParamsFor(p: MotionPrimitive): SpringParams? = SPRINGS[p]

    /** The base tap sharpness for a primitive (exposed for Stage-3 interpolation). */
    fun sharpnessFor(p: MotionPrimitive): Double = baseSharpness(p)

    /** The signed motion curve for a primitive (for animation/envelope preview). */
    fun curve(primitive: MotionPrimitive): MotionCurve = when (primitive) {
        MotionPrimitive.BREATH -> swell(durationSeconds = 0.9, attackFraction = 0.45)
        MotionPrimitive.REFORM -> swell(durationSeconds = 0.6, attackFraction = 0.25)
        MotionPrimitive.COALESCE -> coalesceCurve()
        else -> springCurve(SPRINGS.getValue(primitive))
    }

    /** The felt peaks for a primitive, with per-primitive sharpness. */
    fun peaks(primitive: MotionPrimitive): List<MotionPeak> {
        val sharpness = baseSharpness(primitive)
        if (primitive == MotionPrimitive.COALESCE) {
            // Accelerating taps that grow crisper and stronger as they converge.
            val count = 6
            val total = 0.5
            return (0 until count).map { k ->
                val u = k.toDouble() / (count - 1)
                val frac = 1.0 - (1.0 - u).pow(1.7) // intervals shrink toward the end -> taps converge
                MotionPeak(
                    time = total * frac,
                    magnitude = 0.3 + 0.7 * (k.toDouble() / (count - 1)),
                    sharpness = (0.35 + 0.5 * frac).coerceIn(0.0, 1.0),
                )
            }
        }
        return curve(primitive).peaks().map { (t, m) -> MotionPeak(t, m.coerceIn(0.0, 1.0), sharpness) }
    }

    /** Render a primitive to a playable pattern (transient bursts, or a swell as a continuous). */
    fun toPattern(primitive: MotionPrimitive): HapticAudioPattern {
        val track = if (isSwell(primitive)) swellTrack(primitive) else burstTrack(primitive)
        return HapticAudioPattern(name = primitive.displayName, tracks = listOf(track))
    }

    /** All eight primitives as patterns — the starter palette for the editor/library. */
    fun all(): List<HapticAudioPattern> = MotionPrimitive.entries.map { toPattern(it) }

    // --- rendering -----------------------------------------------------------

    private fun isSwell(p: MotionPrimitive) = p == MotionPrimitive.BREATH || p == MotionPrimitive.REFORM

    private fun burstTrack(primitive: MotionPrimitive): HapticTrack {
        val events = peaks(primitive).map { Transient(it.time, (it.magnitude * 0.95).coerceIn(0.0, 1.0), it.sharpness) }
        return HapticTrack(id = "h1", events = events, curves = listOf(intensityCurve(curve(primitive))))
    }

    private fun swellTrack(primitive: MotionPrimitive): HapticTrack {
        val c = curve(primitive)
        val continuous = Continuous(
            time = 0.0,
            duration = c.durationSeconds,
            intensity = 1.0,
            sharpness = baseSharpness(primitive),
            envelope = Envelope(), // flat; the intensity curve carries the swell shape
        )
        return HapticTrack(id = "h1", events = listOf(continuous), curves = listOf(intensityCurve(c)))
    }

    /** Downsample the rectified, normalized curve into a HAPTIC_INTENSITY breakpoint curve. */
    private fun intensityCurve(c: MotionCurve, points: Int = 40): ParameterCurve {
        val peak = c.samples.maxOfOrNull { abs(it) }?.takeIf { it > 1e-9 } ?: 1.0
        val pts = (0 until points).map { k ->
            val t = c.durationSeconds * k / (points - 1)
            ControlPoint(time = t, value = (abs(c.valueAt(t)) / peak).coerceIn(0.0, 1.0))
        }
        return ParameterCurve(CurveParam.HAPTIC_INTENSITY, pts, Interpolation.SMOOTH)
    }

    private fun baseSharpness(p: MotionPrimitive): Double = when (p) {
        MotionPrimitive.BREATH -> 0.15
        MotionPrimitive.STIR -> 0.3
        MotionPrimitive.REFORM -> 0.4
        MotionPrimitive.GIVE -> 0.35
        MotionPrimitive.REACH -> 0.6
        MotionPrimitive.COALESCE -> 0.5
        MotionPrimitive.SETTLE -> 0.7
        MotionPrimitive.ERUPT -> 0.95
    }

    // --- generators ----------------------------------------------------------

    /** The motion curve for an arbitrary set of spring parameters (used by Stage-3 interpolation). */
    fun springCurve(p: SpringParams): MotionCurve = spring(p.naturalHz, p.damping, p.x0, p.v0)

    /**
     * Build a playable burst pattern from arbitrary spring parameters — transients at the motion's
     * peaks plus the matching intensity curve. [sharpness] sets the crispness of every tap. This is
     * the rendering path for interpolated (non-named) motions produced by [ParameterNavigator].
     */
    fun springPattern(name: String, p: SpringParams, sharpness: Double): HapticAudioPattern {
        val c = springCurve(p)
        val events = c.peaks().map { (t, m) ->
            Transient(t, (m * 0.95).coerceIn(0.0, 1.0), sharpness.coerceIn(0.0, 1.0))
        }
        val track = HapticTrack(id = "h1", events = events, curves = listOf(intensityCurve(c)))
        return HapticAudioPattern(name = name, tracks = listOf(track))
    }

    /**
     * Closed-form damped spring-mass-damper (unit mass) released from displacement [x0] with initial
     * velocity [v0], settling toward 0. Normalized so the peak magnitude is 1.
     */
    private fun spring(naturalHz: Double, damping: Double, x0: Double, v0: Double): MotionCurve {
        val w0 = 2 * PI * naturalHz
        val zeta = damping.coerceIn(0.0, 0.999)
        // Settling time: when the decay envelope drops below 2%.
        val duration = (-ln(0.02) / (zeta * w0)).coerceIn(0.15, 1.5)
        val n = max(2, (duration * SAMPLE_RATE).toInt())
        val wd = w0 * sqrt(1 - zeta * zeta)
        val a = x0
        val b = (v0 + zeta * w0 * x0) / wd
        val raw = DoubleArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            exp(-zeta * w0 * t) * (a * cos(wd * t) + b * sin(wd * t))
        }
        return MotionCurve(SAMPLE_RATE, normalize(raw))
    }

    /**
     * A smooth one-shot swell (0 → 1 → 0). [attackFraction] sets how much of the duration is the rise,
     * so a breath can exhale slower than it inhales. Each half is a raised-cosine for soft ends.
     */
    private fun swell(durationSeconds: Seconds, attackFraction: Double): MotionCurve {
        val n = max(2, (durationSeconds * SAMPLE_RATE).toInt())
        val attack = attackFraction.coerceIn(0.05, 0.95)
        val raw = DoubleArray(n) { i ->
            val u = i.toDouble() / (n - 1) // 0..1
            if (u <= attack) {
                0.5 - 0.5 * cos(PI * (u / attack)) // 0 -> 1
            } else {
                0.5 + 0.5 * cos(PI * ((u - attack) / (1 - attack))) // 1 -> 0
            }
        }
        return MotionCurve(SAMPLE_RATE, raw)
    }

    /** A viz curve for COALESCE: narrow decaying-into-place bumps at the accelerating tap times. */
    private fun coalesceCurve(): MotionCurve {
        val peaks = peaks(MotionPrimitive.COALESCE)
        val duration = (peaks.maxOfOrNull { it.time } ?: 0.5) + 0.06
        val n = max(2, (duration * SAMPLE_RATE).toInt())
        val width = 0.018 // seconds, half-width of each bump
        val raw = DoubleArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            var v = 0.0
            for (p in peaks) {
                val d = abs(t - p.time)
                if (d < width) v = max(v, p.magnitude * (1 - d / width))
            }
            v
        }
        return MotionCurve(SAMPLE_RATE, raw)
    }

    private fun normalize(raw: DoubleArray): DoubleArray {
        val peak = raw.maxOfOrNull { abs(it) } ?: 0.0
        if (peak <= 1e-9) return raw
        val inv = 1.0 / peak
        return DoubleArray(raw.size) { (raw[it] * inv).coerceIn(-1.0, 1.0) }
    }
}
