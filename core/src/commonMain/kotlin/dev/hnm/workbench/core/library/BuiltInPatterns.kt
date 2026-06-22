package dev.hnm.workbench.core.library

import dev.hnm.workbench.core.ir.AudioTrack
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.OscEvent
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.ir.Waveform

/** Hardcoded reference patterns. "Confirm" is the worked example threaded through the brief (§5). */
object BuiltInPatterns {

    /** A sharp tick, then a stronger softer click 80 ms later, with an 880 Hz sine blip. */
    val CONFIRM = HapticAudioPattern(
        name = "Confirm",
        tracks = listOf(
            HapticTrack(
                id = "h1",
                events = listOf(
                    Transient(time = 0.0, intensity = 0.8, sharpness = 0.9),
                    Transient(time = 0.08, intensity = 1.0, sharpness = 0.5),
                ),
            ),
            AudioTrack(
                id = "a1",
                events = listOf(
                    OscEvent(
                        time = 0.0,
                        duration = 0.06,
                        waveform = Waveform.SINE,
                        frequencyHz = 880.0,
                        gain = 0.7,
                    ),
                ),
            ),
        ),
    )
}
