package dev.hnm.workbench.core.dsp

import dev.hnm.workbench.core.ir.AudioTrack
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.OscEvent
import dev.hnm.workbench.core.ir.SampleEvent
import dev.hnm.workbench.core.ir.Seconds
import dev.hnm.workbench.core.ir.Transient

/** Timeline helpers shared across renderers and exporters. */
object PatternTiming {

    /** End time (seconds) of the whole pattern, including envelope release tails. */
    fun durationSeconds(pattern: HapticAudioPattern, sampleBank: SampleBank = SampleBank.EMPTY): Seconds {
        var end = 0.0
        for (track in pattern.tracks) {
            when (track) {
                is HapticTrack -> for (e in track.events) end = maxOf(end, hapticEventEnd(e))
                is AudioTrack -> for (e in track.events) {
                    end = when (e) {
                        is OscEvent -> maxOf(end, e.time + e.duration + e.envelope.release)
                        is SampleEvent -> {
                            val s = sampleBank.resolve(e.sampleId)
                            val len = if (s != null) s.data.size.toDouble() / s.sampleRate else 0.0
                            maxOf(end, e.time + len + e.envelope.release)
                        }
                    }
                }
            }
        }
        return end
    }

    private fun hapticEventEnd(e: dev.hnm.workbench.core.ir.HapticEvent): Seconds = when (e) {
        is Transient -> e.time + 0.02 // a transient renders as a short impulse
        is Continuous -> e.time + e.duration + e.envelope.release
        is dev.hnm.workbench.core.ir.Primitive -> e.time + 0.1 // nominal primitive length
    }
}
