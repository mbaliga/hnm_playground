package dev.hnm.workbench.core

import dev.hnm.workbench.core.design.MotionPrimitive
import dev.hnm.workbench.core.design.MotionPrimitives
import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.core.playback.readAll
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotionPrimitivesTest {

    private val renderer = DefaultPatternRenderer()

    @Test
    fun everyPrimitiveProducesARenderablePattern() {
        for (p in MotionPrimitive.entries) {
            val pattern = MotionPrimitives.toPattern(p)
            assertEquals(p.displayName, pattern.name)
            val track = pattern.tracks.single() as HapticTrack
            assertTrue(track.events.isNotEmpty(), "${p.name} should produce events")

            // Renders to a non-silent haptic waveform via the existing DSP path.
            val wave = renderer.renderHapticWaveform(pattern, 4000).readAll()
            assertTrue(wave.any { abs(it) > 1e-3 }, "${p.name} should render a non-silent waveform")

            // And schedules to at least one command for an amplitude-capable device.
            val commands = renderer.scheduleHaptics(pattern, HapticCapabilities.LRA_FULL)
            assertTrue(commands.isNotEmpty(), "${p.name} should schedule commands")
        }
    }

    @Test
    fun settleIsADecayingBounceTrain() {
        val track = MotionPrimitives.toPattern(MotionPrimitive.SETTLE).tracks.single() as HapticTrack
        val taps = track.events.filterIsInstance<Transient>()
        assertTrue(taps.size >= 3, "settle should bounce a few times, got ${taps.size}")
        // Times strictly increasing.
        assertTrue(taps.zipWithNext().all { (a, b) -> b.time > a.time })
        // Overall decay: the last peak is clearly weaker than the first.
        assertTrue(taps.last().intensity < taps.first().intensity * 0.6)
    }

    @Test
    fun dampingControlsOscillationCount() {
        // Give is heavily damped; Settle is lightly damped -> Settle should ring more.
        val give = (MotionPrimitives.toPattern(MotionPrimitive.GIVE).tracks.single() as HapticTrack).events.size
        val settle = (MotionPrimitives.toPattern(MotionPrimitive.SETTLE).tracks.single() as HapticTrack).events.size
        assertTrue(settle > give, "lightly-damped Settle ($settle) should have more taps than damped Give ($give)")
    }

    @Test
    fun reachBuildsFromQuietThenArrives() {
        // v0-driven spring starts at 0 and swings out: the curve should peak later, not at t=0.
        val curve = MotionPrimitives.curve(MotionPrimitive.REACH)
        assertTrue(abs(curve.valueAt(0.0)) < 0.2, "reach should start quiet")
        val peakTime = curve.peaks().maxByOrNull { it.second }?.first ?: 0.0
        assertTrue(peakTime > 0.01, "reach's strongest moment should be after the start")
    }

    @Test
    fun coalesceAcceleratesAndStrengthens() {
        val taps = (MotionPrimitives.toPattern(MotionPrimitive.COALESCE).tracks.single() as HapticTrack)
            .events.filterIsInstance<Transient>()
        assertTrue(taps.size >= 4)
        // Intervals shrink (taps converge).
        val gaps = taps.zipWithNext { a, b -> b.time - a.time }
        assertTrue(gaps.zipWithNext().all { (g1, g2) -> g2 <= g1 + 1e-6 }, "gaps should not grow")
        // Magnitude grows toward the final coalescing tap.
        assertTrue(taps.last().intensity > taps.first().intensity)
    }

    @Test
    fun breathIsASustainedSwellPeakingInTheMiddle() {
        val track = MotionPrimitives.toPattern(MotionPrimitive.BREATH).tracks.single() as HapticTrack
        val continuous = track.events.single()
        assertTrue(continuous is Continuous, "breath should be a sustained event")
        val curve = MotionPrimitives.curve(MotionPrimitive.BREATH)
        val mid = curve.valueAt(curve.durationSeconds * 0.45)
        assertTrue(mid > curve.valueAt(0.0) && mid > curve.valueAt(curve.durationSeconds), "swell should peak in the middle")
    }

    @Test
    fun generationIsDeterministic() {
        assertEquals(
            MotionPrimitives.toPattern(MotionPrimitive.ERUPT),
            MotionPrimitives.toPattern(MotionPrimitive.ERUPT),
        )
    }
}
