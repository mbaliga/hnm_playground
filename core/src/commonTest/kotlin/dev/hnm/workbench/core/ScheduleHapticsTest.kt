package dev.hnm.workbench.core

import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.Primitive
import dev.hnm.workbench.core.ir.PrimitiveType
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.core.playback.PlayOneShot
import dev.hnm.workbench.core.playback.PlayPrimitive
import dev.hnm.workbench.core.playback.PlayWaveform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScheduleHapticsTest {

    private val renderer = DefaultPatternRenderer()

    @Test
    fun confirmOnLraUsesPrimitivesBySharpness() {
        val cmds = renderer.scheduleHaptics(BuiltInPatterns.CONFIRM, HapticCapabilities.LRA_FULL)
        assertEquals(2, cmds.size)
        val first = cmds[0] as PlayPrimitive
        val second = cmds[1] as PlayPrimitive
        // sharpness 0.9 -> TICK ; sharpness 0.5 -> CLICK
        assertEquals(PrimitiveType.TICK, first.type)
        assertEquals(PrimitiveType.CLICK, second.type)
        assertEquals(0.8f, first.scale)
        assertEquals(0.08, second.atSeconds, 1e-9)
    }

    @Test
    fun ermDegradesToOnOffOneShots() {
        val cmds = renderer.scheduleHaptics(BuiltInPatterns.CONFIRM, HapticCapabilities.ERM_BASIC)
        assertEquals(2, cmds.size)
        assertTrue(cmds.all { it is PlayOneShot })
        // No amplitude control -> full-on amplitude.
        assertEquals(255, (cmds[0] as PlayOneShot).amplitude)
    }

    @Test
    fun continuousNeedsAmplitudeControlForWaveform() {
        val pattern = HapticAudioPattern(
            name = "buzz",
            tracks = listOf(HapticTrack(id = "h", events = listOf(Continuous(0.0, 0.2, 0.9, 0.5)))),
        )
        val lra = renderer.scheduleHaptics(pattern, HapticCapabilities.LRA_FULL)
        assertTrue(lra.single() is PlayWaveform)

        val erm = renderer.scheduleHaptics(pattern, HapticCapabilities.ERM_BASIC)
        assertTrue(erm.single() is PlayOneShot, "ERM should degrade continuous to a single on/off buzz")
    }

    @Test
    fun unsupportedPrimitiveSynthesizesFromTransient() {
        val pattern = HapticAudioPattern(
            name = "spin",
            tracks = listOf(HapticTrack(id = "h", events = listOf(Primitive(0.0, PrimitiveType.SPIN, 0.7)))),
        )
        // A device that has a vibrator + amplitude control but NO primitive support.
        val caps = HapticCapabilities.LRA_FULL.copy(supportedPrimitives = emptySet())
        val cmds = renderer.scheduleHaptics(pattern, caps)
        assertTrue(cmds.single() is PlayOneShot, "no primitive support -> synthesize from transient")
    }

    @Test
    fun noVibratorEmitsNothing() {
        val cmds = renderer.scheduleHaptics(BuiltInPatterns.CONFIRM, HapticCapabilities.NONE)
        assertTrue(cmds.isEmpty())
    }
}
