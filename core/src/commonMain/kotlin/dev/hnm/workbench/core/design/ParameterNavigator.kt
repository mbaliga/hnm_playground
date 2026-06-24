package dev.hnm.workbench.core.design

import dev.hnm.workbench.core.ir.HapticAudioPattern
import kotlin.math.roundToInt

/**
 * Stage 3 of the visual/procedural authoring direction (see docs/AUTHORING-INTERFACES.md): AI as a
 * *parameter navigator*. The research's recommendation is explicit — keep signal generation
 * deterministic and on-device, and let "AI" only move points around inside a small perceptual
 * parameter space. So this is the deterministic, procedural backbone of that idea: linear
 * interpolation between two authored feels, producing a perceptually graded family.
 *
 * Two latent spaces are already continuous and meaningful:
 *  - [TextureField] (roughness / density / octaves) — interpolating roughness walks smooth → rough.
 *  - [SpringParams] (naturalHz / damping / x0 / v0) — interpolating damping walks ringy → dead.
 *
 * A learned interpolator (a small VAE/GAN, per the doc's Stage-3 note) could later replace the
 * straight-line walk with a perceptually-shaped trajectory, but the API surface — interpolate(a, b, t)
 * and family(a, b, n) — stays identical.
 */
object ParameterNavigator {

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    // --- texture fields ------------------------------------------------------

    /**
     * Interpolate between two texture fields of the **same type** at fraction [t] (0 → a, 1 → b).
     * The seed is taken from [a] so the whole family shares one underlying noise — the morph reads as
     * "the same texture getting rougher", not a reshuffle.
     */
    fun interpolate(a: TextureField, b: TextureField, t: Double): TextureField {
        require(a.type == b.type) { "texture interpolation needs the same field type (${a.type} vs ${b.type})" }
        val u = t.coerceIn(0.0, 1.0)
        return a.copy(
            roughness = lerp(a.roughness, b.roughness, u),
            density = lerp(a.density, b.density, u),
            octaves = lerp(a.octaves.toDouble(), b.octaves.toDouble(), u).roundToInt(),
        )
    }

    /** A graded family of [count] (≥2) texture fields from [a] to [b], endpoints inclusive. */
    fun textureFamily(a: TextureField, b: TextureField, count: Int): List<TextureField> {
        require(count >= 2) { "a family needs at least 2 members" }
        return (0 until count).map { interpolate(a, b, it.toDouble() / (count - 1)) }
    }

    /** The texture family as playable patterns (each field scrubbed for [duration]s at [velocity]). */
    fun textureFamilyPatterns(
        a: TextureField,
        b: TextureField,
        count: Int,
        duration: Double = 0.5,
        velocity: Double = 1.0,
    ): List<HapticAudioPattern> =
        textureFamily(a, b, count).map { TextureFields.toPattern(it, duration, velocity) }

    // --- motion springs ------------------------------------------------------

    /** Interpolate between two spring parameter sets at fraction [t] (0 → a, 1 → b). */
    fun interpolate(a: SpringParams, b: SpringParams, t: Double): SpringParams {
        val u = t.coerceIn(0.0, 1.0)
        return SpringParams(
            naturalHz = lerp(a.naturalHz, b.naturalHz, u),
            damping = lerp(a.damping, b.damping, u),
            x0 = lerp(a.x0, b.x0, u),
            v0 = lerp(a.v0, b.v0, u),
        )
    }

    /** A graded family of [count] (≥2) spring parameter sets from [a] to [b], endpoints inclusive. */
    fun springFamily(a: SpringParams, b: SpringParams, count: Int): List<SpringParams> {
        require(count >= 2) { "a family needs at least 2 members" }
        return (0 until count).map { interpolate(a, b, it.toDouble() / (count - 1)) }
    }

    /**
     * A graded family of motion patterns morphing from primitive [a] to [b]. Both must be oscillatory
     * spring primitives (Stir/Reach/Erupt/Give/Settle); swells (Breath/Reform) and Coalesce have no
     * spring vector to interpolate. Sharpness is interpolated alongside the spring parameters.
     */
    fun motionFamilyPatterns(a: MotionPrimitive, b: MotionPrimitive, count: Int): List<HapticAudioPattern> {
        require(count >= 2) { "a family needs at least 2 members" }
        val pa = MotionPrimitives.springParamsFor(a)
            ?: error("$a is not a spring primitive — cannot interpolate it")
        val pb = MotionPrimitives.springParamsFor(b)
            ?: error("$b is not a spring primitive — cannot interpolate it")
        val sa = MotionPrimitives.sharpnessFor(a)
        val sb = MotionPrimitives.sharpnessFor(b)
        return (0 until count).map { i ->
            val u = i.toDouble() / (count - 1)
            MotionPrimitives.springPattern(
                name = "${a.displayName}→${b.displayName} ${i + 1}/$count",
                p = interpolate(pa, pb, u),
                sharpness = lerp(sa, sb, u),
            )
        }
    }
}
