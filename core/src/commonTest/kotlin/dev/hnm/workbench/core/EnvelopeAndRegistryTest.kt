package dev.hnm.workbench.core

import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.core.library.RegistryIndex
import dev.hnm.workbench.core.library.toLibrary
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.core.playback.PlayEnvelope
import dev.hnm.workbench.core.playback.PlayWaveform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnvelopeAndRegistryTest {

    private val renderer = DefaultPatternRenderer()

    private fun buzz(sharpness: Double) = HapticAudioPattern(
        name = "buzz",
        tracks = listOf(
            HapticTrack(
                id = "h",
                events = listOf(Continuous(time = 0.0, duration = 0.4, intensity = 0.8, sharpness = sharpness)),
            ),
        ),
    )

    @Test
    fun widebandUsesEnvelopeWaveformUsesPlain() {
        val wideband = renderer.scheduleHaptics(buzz(0.9), HapticCapabilities.WIDEBAND)
        assertTrue(wideband.single() is PlayEnvelope, "wideband should emit an amplitude+frequency envelope")

        val lra = renderer.scheduleHaptics(buzz(0.9), HapticCapabilities.LRA_FULL)
        assertTrue(lra.single() is PlayWaveform, "amplitude-only LRA should emit a sampled waveform")
    }

    @Test
    fun envelopeFrequencyTracksSharpness() {
        val sharp = (renderer.scheduleHaptics(buzz(0.95), HapticCapabilities.WIDEBAND).single() as PlayEnvelope)
        val dull = (renderer.scheduleHaptics(buzz(0.05), HapticCapabilities.WIDEBAND).single() as PlayEnvelope)
        assertTrue(sharp.points.first().frequencyHz > dull.points.first().frequencyHz,
            "sharper continuous events should map to higher frequency")
    }

    @Test
    fun envelopeAmplitudeStaysInRange() {
        val env = renderer.scheduleHaptics(buzz(0.5), HapticCapabilities.WIDEBAND).single() as PlayEnvelope
        assertTrue(env.points.isNotEmpty())
        assertTrue(env.points.all { it.amplitude in 0f..1f })
    }

    @Test
    fun registrySeedRoundTripsAndLoadsLibrary() {
        val index = RegistryIndex.seed()
        assertTrue(index.size >= 1)
        assertTrue("reference" in index.allTags)

        val restored = RegistryIndex.fromJson(RegistryIndex.toJson(index))
        assertEquals(index.size, restored.size)
        // Nested patterns survive the polymorphic round-trip.
        assertEquals(index.entries.first().pattern.name, restored.entries.first().pattern.name)

        val library = index.toLibrary()
        assertEquals(index.size, library.size)
        assertTrue(library.get(BuiltInPatterns.CONFIRM.name) != null)
    }
}
