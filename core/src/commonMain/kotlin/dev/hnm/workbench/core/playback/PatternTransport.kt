package dev.hnm.workbench.core.playback

import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.Seconds

/** A backend sink for discrete haptic commands as they fire. The Android backend implements this. */
fun interface HapticCommandSink {
    fun fire(command: HapticCommand)
}

/**
 * Ties the continuous (audio) and discrete (haptic) paths onto one [TransportClock] so they stay
 * coincident (§6 — the simultaneity test). Each path gets its own latency compensation: the audio
 * backend's buffer delay and the haptic actuator's mechanical delay are usually different, and the
 * scheduler shifts each action earlier by its own amount so they align at the *output*.
 *
 * This deliberately drives backends through their interfaces and a clock that the host pumps, so it is
 * fully testable with [ManualTransportClock] and a recording sink — no device required.
 */
class PatternTransport(
    private val renderer: PatternRenderer,
    private val clock: TransportClock,
    private val caps: HapticCapabilities,
    private val hapticLatency: Seconds = 0.0,
) {
    private var hapticSink: HapticCommandSink? = null

    fun onHapticCommand(sink: HapticCommandSink) = apply { hapticSink = sink }

    /**
     * Schedule [pattern]'s haptic commands on the clock and hand the audio stream to [audioBackend]
     * (if any). Returns the rendered audio so an offline caller can use it directly.
     */
    fun play(
        pattern: HapticAudioPattern,
        sampleRate: Int = 48_000,
        audioBackend: AudioBackend? = null,
        at: Seconds = 0.0,
    ): FloatStream {
        val commands = renderer.scheduleHaptics(pattern, caps)
        for (cmd in commands) {
            clock.scheduleAt(cmd.atSeconds, latencyComp = hapticLatency) {
                hapticSink?.fire(cmd)
            }
        }
        val audio = renderer.renderAudio(pattern, sampleRate)
        clock.start(at)
        audioBackend?.start(audio, sampleRate)
        return audio
    }

    fun stop(audioBackend: AudioBackend? = null) {
        clock.stop()
        audioBackend?.stop()
    }
}
