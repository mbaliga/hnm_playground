package dev.hnm.workbench.core

import dev.hnm.workbench.core.design.Material
import dev.hnm.workbench.core.design.MaterialPreset
import dev.hnm.workbench.core.design.ModalSynth
import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.ir.AudioTrack
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.core.playback.readAll
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModalSynthTest {

    private val renderer = DefaultPatternRenderer()
    private val sampleRate = 48_000

    /** RMS energy of the rendered audio in the window [from, to] seconds. */
    private fun audioEnergyWindow(material: Material, from: Double, to: Double): Double {
        val wave = renderer.renderAudio(ModalSynth.toPattern(material), sampleRate).readAll()
        val a = (from * sampleRate).toInt().coerceIn(0, wave.size)
        val b = (to * sampleRate).toInt().coerceIn(0, wave.size)
        if (b <= a) return 0.0
        var sum = 0.0
        for (i in a until b) sum += wave[i].toDouble() * wave[i]
        return sum / (b - a)
    }

    // --- pitch from stiffness/density ---

    @Test
    fun stifferLighterMaterialIsHigherPitched() {
        val glass = MaterialPreset.GLASS.material
        val rubber = MaterialPreset.RUBBER.material
        assertTrue(
            ModalSynth.fundamentalHz(glass) > ModalSynth.fundamentalHz(rubber),
            "glass (${ModalSynth.fundamentalHz(glass)}Hz) should ring higher than rubber (${ModalSynth.fundamentalHz(rubber)}Hz)",
        )
    }

    @Test
    fun stiffnessRaisesPitchMonotonically() {
        val soft = Material("soft", stiffness = 0.1)
        val hard = Material("hard", stiffness = 0.9)
        assertTrue(ModalSynth.fundamentalHz(hard) > ModalSynth.fundamentalHz(soft))
    }

    // --- ring time from damping ---

    @Test
    fun metalRingsLongerThanRubber() {
        // At 0.25 s the metal should still be ringing; rubber should be essentially silent.
        val metalLate = audioEnergyWindow(MaterialPreset.METAL.material, 0.25, 0.30)
        val rubberLate = audioEnergyWindow(MaterialPreset.RUBBER.material, 0.25, 0.30)
        assertTrue(metalLate > rubberLate * 5, "metal late energy ($metalLate) should dwarf rubber's ($rubberLate)")
    }

    @Test
    fun dampingShortensRingMonotonically() {
        val light = Material("light", damping = 0.1)
        val heavy = Material("heavy", damping = 0.9)
        assertTrue(
            ModalSynth.ringSeconds(light) > ModalSynth.ringSeconds(heavy),
            "less damping should ring longer",
        )
    }

    // --- THE Stage-4 threshold: one material edit co-varies BOTH channels ---

    @Test
    fun increasingDampingShortensBothAudioAndHapticRing() {
        val ringy = Material("ringy", damping = 0.15)
        val dead = Material("dead", damping = 0.9)

        // Audio: late-window energy drops when damping rises.
        val ringyAudioLate = audioEnergyWindow(ringy, 0.2, 0.25)
        val deadAudioLate = audioEnergyWindow(dead, 0.2, 0.25)
        assertTrue(ringyAudioLate > deadAudioLate, "audio ring should shorten with damping ($ringyAudioLate vs $deadAudioLate)")

        // Haptics: the felt ring (Continuous duration) shrinks too — same edit, both channels.
        fun hapticRing(m: Material): Double {
            val track = ModalSynth.toPattern(m).tracks.filterIsInstance<HapticTrack>().single()
            return (track.events.filterIsInstance<Continuous>().single()).duration
        }
        assertTrue(hapticRing(ringy) > hapticRing(dead), "felt ring should shorten with damping too")
    }

    @Test
    fun brighterMaterialHasMoreModes() {
        val dull = Material("dull", brightness = 0.1)
        val bright = Material("bright", brightness = 0.95)
        assertTrue(
            ModalSynth.modes(bright).size >= ModalSynth.modes(dull).size,
            "brighter material should sound through at least as many modes",
        )
    }

    // --- structure / rendering ---

    @Test
    fun everyPresetRendersBothChannels() {
        for (preset in MaterialPreset.entries) {
            val pattern = ModalSynth.toPattern(preset.material)
            assertTrue(pattern.tracks.any { it is HapticTrack }, "${preset.displayName} needs a haptic track")
            assertTrue(pattern.tracks.any { it is AudioTrack }, "${preset.displayName} needs an audio track")

            val audio = renderer.renderAudio(pattern, sampleRate).readAll()
            assertTrue(audio.any { abs(it) > 1e-3 }, "${preset.displayName} audio should be non-silent")

            val haptic = renderer.renderHapticWaveform(pattern, 4000).readAll()
            assertTrue(haptic.any { abs(it) > 1e-3 }, "${preset.displayName} haptic should be non-silent")

            assertTrue(renderer.scheduleHaptics(pattern, HapticCapabilities.LRA_FULL).isNotEmpty())
        }
    }

    @Test
    fun modeAmplitudesAreNormalized() {
        val sum = ModalSynth.modes(MaterialPreset.WOOD.material).sumOf { it.amplitude }
        assertTrue(abs(sum - 1.0) < 1e-9, "mode amplitudes should sum to 1, was $sum")
    }

    @Test
    fun higherModesDecayFasterThanTheFundamental() {
        val modes = ModalSynth.modes(MaterialPreset.METAL.material)
        assertTrue(modes.size >= 2)
        assertTrue(modes.last().decaySeconds < modes.first().decaySeconds, "upper modes should die before the fundamental")
    }

    @Test
    fun generationIsDeterministic() {
        assertEquals(
            ModalSynth.toPattern(MaterialPreset.CERAMIC.material),
            ModalSynth.toPattern(MaterialPreset.CERAMIC.material),
        )
    }
}
