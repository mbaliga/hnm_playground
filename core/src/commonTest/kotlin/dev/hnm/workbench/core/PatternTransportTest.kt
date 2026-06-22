package dev.hnm.workbench.core

import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.core.playback.HapticCommand
import dev.hnm.workbench.core.playback.ManualTransportClock
import dev.hnm.workbench.core.playback.PatternTransport
import dev.hnm.workbench.core.playback.PlayPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PatternTransportTest {

    @Test
    fun firesHapticCommandsInOrderOnSharedClockWithLatency() {
        val clock = ManualTransportClock()
        val fired = mutableListOf<HapticCommand>()
        val transport = PatternTransport(
            renderer = DefaultPatternRenderer(),
            clock = clock,
            caps = HapticCapabilities.LRA_FULL,
            hapticLatency = 0.01, // actuator fires 10 ms early so it meets the audio
        ).onHapticCommand { fired += it }

        transport.play(BuiltInPatterns.CONFIRM, sampleRate = 48_000, audioBackend = null)

        clock.advanceTo(0.0)
        // The first transient (t=0) is latency-compensated to fire at/just before t=0.
        assertEquals(1, fired.size)
        assertEquals(0.0, (fired[0] as PlayPrimitive).atSeconds, 1e-9)

        clock.advanceTo(0.06)
        assertEquals(1, fired.size, "second command (t=0.08) should not have fired yet")

        clock.advanceTo(0.08)
        assertEquals(2, fired.size)
        assertTrue(fired.map { it.atSeconds } == listOf(0.0, 0.08))
    }
}
