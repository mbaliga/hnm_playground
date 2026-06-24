package dev.hnm.workbench.core

import dev.hnm.workbench.core.design.MotionPrimitive
import dev.hnm.workbench.core.design.MotionPrimitives
import dev.hnm.workbench.core.design.ParameterNavigator
import dev.hnm.workbench.core.design.TextureField
import dev.hnm.workbench.core.design.TextureFieldType
import dev.hnm.workbench.core.design.TextureFields
import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.core.playback.readAll
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ParameterNavigatorTest {

    private val renderer = DefaultPatternRenderer()

    // --- texture interpolation ---

    @Test
    fun textureEndpointsAreTheInputs() {
        val a = TextureField(TextureFieldType.PERLIN, roughness = 0.1, density = 0.2, octaves = 3)
        val b = TextureField(TextureFieldType.PERLIN, roughness = 0.9, density = 0.8, octaves = 6)
        assertEquals(a, ParameterNavigator.interpolate(a, b, 0.0))
        assertEquals(b, ParameterNavigator.interpolate(a, b, 1.0))
    }

    @Test
    fun textureMidpointIsBetween() {
        val a = TextureField(TextureFieldType.PERLIN, roughness = 0.2)
        val b = TextureField(TextureFieldType.PERLIN, roughness = 0.8)
        val mid = ParameterNavigator.interpolate(a, b, 0.5)
        assertTrue(abs(mid.roughness - 0.5) < 1e-9, "midpoint roughness should be 0.5, was ${mid.roughness}")
    }

    @Test
    fun mismatchedTypesAreRejected() {
        val a = TextureField(TextureFieldType.PERLIN)
        val b = TextureField(TextureFieldType.WORLEY)
        assertFailsWith<IllegalArgumentException> { ParameterNavigator.interpolate(a, b, 0.5) }
    }

    @Test
    fun textureFamilyRoughnessIsStrictlyMonotonic() {
        val fam = ParameterNavigator.textureFamily(
            TextureField(TextureFieldType.PERLIN, roughness = 0.05),
            TextureField(TextureFieldType.PERLIN, roughness = 0.95),
            count = 5,
        )
        assertEquals(5, fam.size)
        assertTrue(fam.zipWithNext().all { (x, y) -> y.roughness > x.roughness }, "roughness must increase across the family")
    }

    @Test
    fun textureFamilyIsPerceptuallyGraded() {
        // The roadmap's Stage-3 threshold: a family should be perceptually graded, not just numerically.
        // A rougher field crosses a mid-level threshold more often (higher spatial frequency). With a
        // shared seed, that crossing count should grow from the smooth end to the rough end.
        val fam = ParameterNavigator.textureFamily(
            TextureField(TextureFieldType.PERLIN, roughness = 0.05),
            TextureField(TextureFieldType.PERLIN, roughness = 0.95),
            count = 5,
        )
        fun crossings(field: TextureField): Int {
            val c = TextureFields.curve(field, duration = 1.0, velocity = 1.0)
            var count = 0
            var prev = c.valueAt(0.0) > 0.5
            for (k in 1..300) {
                val cur = c.valueAt(k / 300.0) > 0.5
                if (cur != prev) count++
                prev = cur
            }
            return count
        }
        val counts = fam.map { crossings(it) }
        assertTrue(counts.last() > counts.first(), "rough end (${counts.last()}) should be finer-grained than smooth end (${counts.first()}); counts=$counts")
    }

    @Test
    fun textureFamilyIsDeterministic() {
        val a = TextureField(TextureFieldType.FBM, roughness = 0.1)
        val b = TextureField(TextureFieldType.FBM, roughness = 0.9)
        assertEquals(
            ParameterNavigator.textureFamilyPatterns(a, b, 5),
            ParameterNavigator.textureFamilyPatterns(a, b, 5),
        )
    }

    // --- spring interpolation ---

    @Test
    fun springEndpointsAreTheInputs() {
        val stir = MotionPrimitives.springParamsFor(MotionPrimitive.STIR)!!
        val give = MotionPrimitives.springParamsFor(MotionPrimitive.GIVE)!!
        assertEquals(stir, ParameterNavigator.interpolate(stir, give, 0.0))
        assertEquals(give, ParameterNavigator.interpolate(stir, give, 1.0))
    }

    @Test
    fun springFamilyDampingIsMonotonic() {
        val stir = MotionPrimitives.springParamsFor(MotionPrimitive.STIR)!! // damping 0.14
        val give = MotionPrimitives.springParamsFor(MotionPrimitive.GIVE)!! // damping 0.55
        val fam = ParameterNavigator.springFamily(stir, give, 5)
        assertTrue(fam.zipWithNext().all { (x, y) -> y.damping > x.damping }, "damping should increase from light to heavy")
    }

    @Test
    fun motionFamilyOscillationCountDecreasesWithDamping() {
        // Stir is lightly damped (rings a lot); Give is heavily damped (settles fast). A morph from
        // Stir to Give should lose felt taps as damping climbs — a monotonic perceptual gradient.
        val fam = ParameterNavigator.motionFamilyPatterns(MotionPrimitive.STIR, MotionPrimitive.GIVE, count = 5)
        val taps = fam.map { (it.tracks.single() as HapticTrack).events.filterIsInstance<Transient>().size }
        assertTrue(taps.first() > taps.last(), "lightly-damped Stir end (${taps.first()}) should ring more than damped Give end (${taps.last()}); taps=$taps")
    }

    @Test
    fun motionFamilyRendersAndSchedules() {
        val fam = ParameterNavigator.motionFamilyPatterns(MotionPrimitive.REACH, MotionPrimitive.SETTLE, count = 4)
        assertEquals(4, fam.size)
        for (pattern in fam) {
            val wave = renderer.renderHapticWaveform(pattern, 4000).readAll()
            assertTrue(wave.any { abs(it) > 1e-3 }, "${pattern.name} should render non-silently")
            assertTrue(renderer.scheduleHaptics(pattern, HapticCapabilities.LRA_FULL).isNotEmpty())
        }
    }

    @Test
    fun nonSpringPrimitivesAreRejected() {
        // Breath is a swell, not a spring — it has no spring vector to interpolate.
        assertFailsWith<IllegalStateException> {
            ParameterNavigator.motionFamilyPatterns(MotionPrimitive.BREATH, MotionPrimitive.SETTLE, count = 3)
        }
    }

    @Test
    fun namedPrimitivesAreUnchangedByRefactor() {
        // The spring-parameter extraction must not have altered the existing named primitives.
        for (p in listOf(MotionPrimitive.STIR, MotionPrimitive.REACH, MotionPrimitive.ERUPT, MotionPrimitive.GIVE, MotionPrimitive.SETTLE)) {
            val params = MotionPrimitives.springParamsFor(p)!!
            val viaParams = MotionPrimitives.springCurve(params).samples
            val viaName = MotionPrimitives.curve(p).samples
            assertEquals(viaName.size, viaParams.size)
            assertTrue(viaName.indices.all { abs(viaName[it] - viaParams[it]) < 1e-12 }, "$p curve changed after refactor")
        }
    }
}
