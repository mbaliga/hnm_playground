package dev.hnm.workbench.core.design

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure, platform-agnostic geometry for the splash visuals. Both the Android Canvas and the Compose
 * DrawScope call these so the animation math lives in one tested place; each platform only translates
 * the returned normalized shapes (centre-relative, unit-ish scale) into its own draw calls.
 *
 * All inputs/outputs are normalized: positions are centre-relative in roughly [-1, 1], radii/heights in
 * [0, 1], alphas in [0, 1]. A renderer multiplies by the actual pixel scale.
 */
object SplashGeometry {

    /** Expanding ring from a beat. [radius] 0..1, [alpha] 0..1. */
    data class Ring(val radius: Float, val alpha: Float)

    /** A spark particle, centre-relative unit coordinates. */
    data class Spark(val x: Float, val y: Float, val alpha: Float, val size: Float)

    private const val RING_LIFE = 1.15
    private const val SPARK_LIFE = 0.9
    private const val SPARKS_PER_BEAT = 7

    /** Global fade-in/out envelope so the splash eases in and out instead of cutting. */
    fun masterAlpha(tSec: Double, durSec: Double): Float {
        if (durSec <= 0.0) return 1f
        val fadeIn = (tSec / 0.25).coerceIn(0.0, 1.0)
        val fadeOut = ((durSec - tSec) / 0.4).coerceIn(0.0, 1.0)
        return (fadeIn * fadeOut).toFloat()
    }

    /** RIPPLE: one expanding ring per past beat. */
    fun ripple(tSec: Double, beats: List<Double>): List<Ring> = beats.mapNotNull { b ->
        val age = tSec - b
        if (age < 0.0 || age > RING_LIFE) return@mapNotNull null
        val p = age / RING_LIFE
        Ring(radius = p.toFloat(), alpha = (1.0 - p).toFloat())
    }

    /**
     * BLOOM: [bars] mirrored heights growing from the centre. A smooth bump envelope over the whole
     * duration, modulated by seeded per-bar noise and boosted briefly near each beat.
     */
    fun bloom(tSec: Double, durSec: Double, beats: List<Double>, bars: Int, seed: Int): FloatArray {
        val env = bump(tSec / durSec.coerceAtLeast(1e-6))
        val boost = beats.sumOf { b ->
            val age = tSec - b
            if (age in 0.0..0.25) (1.0 - age / 0.25) * 0.4 else 0.0
        }
        return FloatArray(bars) { i ->
            // Symmetric around centre so the waveform blooms outward.
            val d = kotlin.math.abs(i - (bars - 1) / 2.0) / ((bars - 1) / 2.0).coerceAtLeast(1e-6)
            val taper = (1.0 - d * 0.7)
            val n = 0.45 + 0.55 * hash(i * 2 + 1, seed)
            ((env * taper * n) + boost * taper).coerceIn(0.0, 1.0).toFloat()
        }
    }

    /** SWEEP: playhead progress 0..1 across the duration. */
    fun sweepProgress(tSec: Double, durSec: Double): Float =
        (tSec / durSec.coerceAtLeast(1e-6)).coerceIn(0.0, 1.0).toFloat()

    /** SWEEP: per-tick brightness — flares when the playhead crosses each beat, then dims. */
    fun sweepTicks(tSec: Double, durSec: Double, beats: List<Double>): List<Pair<Float, Float>> =
        beats.map { b ->
            val x = (b / durSec.coerceAtLeast(1e-6)).coerceIn(0.0, 1.0).toFloat()
            val age = tSec - b
            val bright = if (age < 0) 0.25f else (1.0 - (age / 0.5).coerceIn(0.0, 1.0)).toFloat() * 0.75f + 0.25f
            x to bright
        }

    /** SPARK: decaying particles bursting from the centre on each beat. */
    fun sparks(tSec: Double, beats: List<Double>, seed: Int): List<Spark> {
        val out = ArrayList<Spark>()
        beats.forEachIndexed { bi, b ->
            val age = tSec - b
            if (age < 0.0 || age > SPARK_LIFE) return@forEachIndexed
            val p = age / SPARK_LIFE
            val alpha = (1.0 - p).toFloat()
            for (k in 0 until SPARKS_PER_BEAT) {
                val ang = 2.0 * PI * hash(bi * 31 + k, seed)
                val speed = 0.5 + 0.5 * hash(bi * 17 + k * 3, seed)
                val r = (p * speed)
                out += Spark(
                    x = (cos(ang) * r).toFloat(),
                    y = (sin(ang) * r).toFloat(),
                    alpha = alpha,
                    size = (0.12 * (1.0 - p) + 0.03).toFloat(),
                )
            }
        }
        return out
    }

    // --- helpers --------------------------------------------------------------

    /** Smooth 0→1→0 bump over x∈[0,1]. */
    private fun bump(x: Double): Double {
        val c = x.coerceIn(0.0, 1.0)
        return sin(PI * c).let { it * it } // sin^2 — eases in and out
    }

    /** Deterministic [0,1) hash for procedural variation. */
    private fun hash(i: Int, seed: Int): Double {
        val x = sin((i * 12.9898 + seed * 78.233) ) * 43758.5453
        return x - kotlin.math.floor(x)
    }
}
