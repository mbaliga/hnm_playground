package dev.hnm.workbench.core

import dev.hnm.workbench.core.dsp.CurveSampler
import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.dsp.EnvelopeFollower
import dev.hnm.workbench.core.ir.AudioTrack
import dev.hnm.workbench.core.ir.ControlPoint
import dev.hnm.workbench.core.ir.Coupling
import dev.hnm.workbench.core.ir.CouplingMode
import dev.hnm.workbench.core.ir.CurveParam
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.Interpolation
import dev.hnm.workbench.core.ir.OscEvent
import dev.hnm.workbench.core.ir.ParameterCurve
import dev.hnm.workbench.core.ir.Waveform
import dev.hnm.workbench.core.playback.ManualTransportClock
import dev.hnm.workbench.core.playback.readAll
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DspTest {

    @Test
    fun linearCurveInterpolatesAndClamps() {
        val curve = ParameterCurve(
            CurveParam.AUDIO_GAIN,
            listOf(ControlPoint(0.0, 0.0), ControlPoint(1.0, 1.0)),
            Interpolation.LINEAR,
        )
        val s = CurveSampler(curve)
        assertEquals(0.0, s.valueAt(-1.0), 1e-9) // clamp left
        assertEquals(0.5, s.valueAt(0.5), 1e-9)
        assertEquals(1.0, s.valueAt(2.0), 1e-9) // clamp right
    }

    @Test
    fun stepCurveHoldsLeftValue() {
        val curve = ParameterCurve(
            CurveParam.HAPTIC_INTENSITY,
            listOf(ControlPoint(0.0, 0.2), ControlPoint(1.0, 0.9)),
            Interpolation.STEP,
        )
        assertEquals(0.2, CurveSampler(curve).valueAt(0.5), 1e-9)
    }

    @Test
    fun envelopeFollowerTracksAmplitude() {
        val f = EnvelopeFollower(attackMs = 1.0, releaseMs = 50.0, sampleRate = 48_000)
        var env = 0.0
        repeat(4800) { env = f.process(1.0) } // 0.1s of full-scale input
        assertTrue(env > 0.9, "follower should rise toward 1.0, got $env")
        repeat(4800) { env = f.process(0.0) } // then silence
        assertTrue(env < 0.5, "follower should release toward 0, got $env")
    }

    @Test
    fun audioDrivesHapticsProducesWaveform() {
        // Empty haptic track + a loud audio track + a coupling -> the haptic waveform is non-silent.
        val pattern = HapticAudioPattern(
            name = "coupled",
            tracks = listOf(
                HapticTrack(id = "h"),
                AudioTrack(id = "a", events = listOf(OscEvent(0.0, 0.2, Waveform.SINE, 200.0, gain = 0.9))),
            ),
            couplings = listOf(Coupling("c", CouplingMode.AUDIO_DRIVES_HAPTICS, "a", "h")),
        )
        val wave = DefaultPatternRenderer().renderHapticWaveform(pattern, 48_000).readAll()
        val rms = sqrt(wave.sumOf { (it * it).toDouble() } / wave.size)
        assertTrue(rms > 0.01, "coupled haptic waveform should be audible-felt, rms=$rms")
    }

    @Test
    fun transportClockFiresScheduledActionsWithLatencyComp() {
        val clock = ManualTransportClock()
        val fired = mutableListOf<String>()
        clock.scheduleAt(0.10, latencyComp = 0.02, action = { fired += "haptic" }) // fires at 0.08
        clock.scheduleAt(0.10, latencyComp = 0.0, action = { fired += "audio" }) // fires at 0.10
        clock.start()
        clock.advanceTo(0.05)
        assertTrue(fired.isEmpty())
        clock.advanceTo(0.08)
        assertEquals(listOf("haptic"), fired) // latency-compensated action fires earlier
        clock.advanceTo(0.10)
        assertEquals(listOf("haptic", "audio"), fired)
    }
}
