package dev.hnm.workbench.core

import dev.hnm.workbench.core.design.SplashGeometry
import dev.hnm.workbench.core.design.SplashMotifs
import dev.hnm.workbench.core.design.SplashVisual
import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.core.playback.readAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplashTest {

    private val renderer = DefaultPatternRenderer()

    @Test
    fun generateIsDeterministicInSeed() {
        val a = SplashMotifs.generate(2)
        val b = SplashMotifs.generate(2)
        assertEquals(a.visual, b.visual)
        assertEquals(a.beats, b.beats)
        assertEquals(a.pattern.name, b.pattern.name)
    }

    @Test
    fun seedSelectsEachVisual() {
        val visuals = (0 until SplashVisual.entries.size).map { SplashMotifs.generate(it).visual }.toSet()
        assertEquals(SplashVisual.entries.toSet(), visuals)
    }

    @Test
    fun everySceneHasBeatsAndRendersBothPaths() {
        for (scene in SplashMotifs.all()) {
            assertTrue(scene.beats.isNotEmpty(), "${scene.title} has no beats")
            assertTrue(scene.durationSeconds > 0.0)
            // Haptic schedule is non-empty on an LRA.
            val cmds = renderer.scheduleHaptics(scene.pattern, HapticCapabilities.LRA_FULL)
            assertTrue(cmds.isNotEmpty(), "${scene.title} scheduled nothing")
            // Audio renders to a non-silent buffer.
            val audio = renderer.renderAudio(scene.pattern, 48_000).readAll()
            assertTrue(audio.any { kotlin.math.abs(it) > 1e-4 }, "${scene.title} produced silence")
            // The visual's beats line up with real haptic events.
            val hapticTimes = (scene.pattern.tracks.first { it is HapticTrack } as HapticTrack).events.map { it.time }
            assertTrue(scene.beats.all { b -> hapticTimes.any { kotlin.math.abs(it - b) < 1e-6 } },
                "${scene.title} beats don't all map to haptic events")
        }
    }

    @Test
    fun masterAlphaFadesInAndOut() {
        val dur = 2.0
        assertTrue(SplashGeometry.masterAlpha(0.0, dur) < 0.05)
        assertEquals(1f, SplashGeometry.masterAlpha(1.0, dur), 0.05f)
        assertTrue(SplashGeometry.masterAlpha(dur, dur) < 0.05)
    }

    @Test
    fun rippleRingsExpandAndFade() {
        val beats = listOf(0.0)
        val early = SplashGeometry.ripple(0.1, beats).single()
        val late = SplashGeometry.ripple(0.9, beats).single()
        assertTrue(late.radius > early.radius, "rings should expand over time")
        assertTrue(late.alpha < early.alpha, "rings should fade over time")
        // Past its life, the ring is gone.
        assertTrue(SplashGeometry.ripple(2.0, beats).isEmpty())
    }

    @Test
    fun bloomBarsAreSeededAndBounded() {
        val a = SplashGeometry.bloom(1.0, 2.0, listOf(0.0), bars = 24, seed = 1)
        val b = SplashGeometry.bloom(1.0, 2.0, listOf(0.0), bars = 24, seed = 2)
        assertEquals(24, a.size)
        assertTrue(a.all { it in 0f..1f })
        assertTrue(a.toList() != b.toList(), "different seeds should differ")
    }

    @Test
    fun sparksBurstThenVanish() {
        val beats = listOf(0.0)
        assertTrue(SplashGeometry.sparks(0.1, beats, seed = 3).isNotEmpty())
        assertTrue(SplashGeometry.sparks(2.0, beats, seed = 3).isEmpty())
        // Particles stay within the unit disk.
        SplashGeometry.sparks(0.4, beats, seed = 3).forEach {
            assertTrue(it.x * it.x + it.y * it.y <= 1.2f)
        }
    }
}
