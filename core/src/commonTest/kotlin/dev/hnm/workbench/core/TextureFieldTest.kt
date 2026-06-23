package dev.hnm.workbench.core

import dev.hnm.workbench.core.design.TextureField
import dev.hnm.workbench.core.design.TextureFieldType
import dev.hnm.workbench.core.design.TextureFields
import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.core.playback.readAll
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextureFieldTest {

    private val renderer = DefaultPatternRenderer()

    // --- field sampling ---

    @Test
    fun sampleAlwaysInUnitRange() {
        for (type in TextureFieldType.entries) {
            val field = TextureField(type = type, roughness = 0.5, density = 0.5)
            repeat(200) { k ->
                val v = TextureFields.sample(field, k * 0.013 + 0.0001)
                assertTrue(v in 0.0..1.0, "$type sample out of [0,1]: $v at k=$k")
            }
        }
    }

    @Test
    fun generationIsDeterministic() {
        val field = TextureField(type = TextureFieldType.FBM, roughness = 0.6, seed = 42)
        assertEquals(TextureFields.sample(field, 1.23), TextureFields.sample(field, 1.23))
        assertEquals(TextureFields.toPattern(field), TextureFields.toPattern(field))
    }

    @Test
    fun seedChangesOutput() {
        val a = TextureField(type = TextureFieldType.PERLIN, roughness = 0.5, seed = 0)
        val b = TextureField(type = TextureFieldType.PERLIN, roughness = 0.5, seed = 99)
        // Different seeds produce different values at the same position.
        val diffs = (0..20).count { k ->
            abs(TextureFields.sample(a, k * 0.17) - TextureFields.sample(b, k * 0.17)) > 0.01
        }
        assertTrue(diffs > 10, "different seeds should produce meaningfully different outputs (diffs=$diffs/21)")
    }

    // --- roughness perceptual ordering ---

    @Test
    fun rougherFieldHasHigherSpatialVariation() {
        // Rougher = higher spatial frequency = more zero-crossings / sign changes per unit length.
        fun crossings(roughness: Double): Int {
            val field = TextureField(type = TextureFieldType.PERLIN, roughness = roughness)
            val c = TextureFields.curve(field, duration = 1.0, velocity = 1.0)
            val threshold = 0.5
            var count = 0
            var prev = c.valueAt(0.0) > threshold
            for (k in 1..200) {
                val cur = c.valueAt(k / 200.0) > threshold
                if (cur != prev) count++
                prev = cur
            }
            return count
        }
        val smooth = crossings(0.1)
        val rough = crossings(0.9)
        assertTrue(rough > smooth, "rough field (c=$rough) should cross threshold more often than smooth (c=$smooth)")
    }

    @Test
    fun worleyIsSpikierThanValue() {
        // Worley inverted-distance → sharp peaks → high kurtosis.
        // Value noise → smoother landscape. Check: worley has more samples near 0 or 1.
        fun extremeness(type: TextureFieldType): Double {
            val field = TextureField(type = type, roughness = 0.5)
            return (0..199).count { k ->
                val v = TextureFields.sample(field, k * 0.013)
                v < 0.1 || v > 0.9
            } / 200.0
        }
        val w = extremeness(TextureFieldType.WORLEY)
        val v = extremeness(TextureFieldType.VALUE)
        assertTrue(w > v, "Worley ($w) should be spikier (more extremes) than value noise ($v)")
    }

    // --- velocity modulation ---

    @Test
    fun higherVelocityProducesHigherTemporalFrequency() {
        val field = TextureField(type = TextureFieldType.PERLIN, roughness = 0.5)
        fun crossings(velocity: Double): Int {
            val c = TextureFields.curve(field, duration = 0.5, velocity = velocity)
            var count = 0
            var prev = c.valueAt(0.0) > 0.5
            for (k in 1..100) {
                val cur = c.valueAt(0.5 * k / 100) > 0.5
                if (cur != prev) count++
                prev = cur
            }
            return count
        }
        val slow = crossings(0.5)
        val fast = crossings(3.0)
        assertTrue(fast > slow, "fast scrub (c=$fast) should produce more transitions than slow (c=$slow)")
    }

    // --- pattern structure ---

    @Test
    fun toPatternProducesRenderableOutput() {
        for (type in TextureFieldType.entries) {
            val pattern = TextureFields.toPattern(TextureField(type = type))
            val track = pattern.tracks.single() as HapticTrack
            val event = track.events.single()
            assertTrue(event is Continuous, "$type should produce a Continuous event")

            // Renders to non-silent haptic waveform.
            val wave = renderer.renderHapticWaveform(pattern, 4000).readAll()
            assertTrue(wave.any { abs(it) > 1e-4 }, "$type pattern should render non-silently")

            // Schedules commands on an amplitude-capable device.
            val cmds = renderer.scheduleHaptics(pattern, HapticCapabilities.LRA_FULL)
            assertTrue(cmds.isNotEmpty(), "$type should schedule commands")
        }
    }

    @Test
    fun sharpnessScalesWithRoughness() {
        // Rougher fields should emit patterns with higher sharpness.
        fun sharpnessOf(roughness: Double): Double {
            val pattern = TextureFields.toPattern(TextureField(type = TextureFieldType.PERLIN, roughness = roughness))
            return ((pattern.tracks.single() as HapticTrack).events.single() as Continuous).sharpness
        }
        assertTrue(sharpnessOf(0.9) > sharpnessOf(0.1), "rougher field should have higher sharpness")
    }

    @Test
    fun allDefaultFieldsAreValid() {
        val fields = TextureFields.all()
        assertEquals(TextureFieldType.entries.size, fields.size)
        for (field in fields) {
            val pattern = TextureFields.toPattern(field)
            assertTrue(pattern.tracks.isNotEmpty())
        }
    }

    // --- fBm octave depth ---

    @Test
    fun moreOctavesAddsDetail() {
        // More octaves → more fine-grained variation → more threshold crossings at high sample density.
        fun detail(octaves: Int): Int {
            val field = TextureField(type = TextureFieldType.FBM, roughness = 0.5, octaves = octaves)
            val c = TextureFields.curve(field, duration = 0.5, velocity = 1.0)
            var count = 0
            var prev = c.valueAt(0.0)
            for (k in 1..400) {
                val cur = c.valueAt(0.5 * k / 400)
                if (abs(cur - prev) > 0.005) count++
                prev = cur
            }
            return count
        }
        assertTrue(detail(6) >= detail(2), "more octaves should add at least as much detail")
    }
}
