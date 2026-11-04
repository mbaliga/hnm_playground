package dev.hnm.workbench.backend.desktop

import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.playback.ArrayFloatStream
import dev.hnm.workbench.core.playback.AudioBackend
import dev.hnm.workbench.core.playback.PatternPlayer
import dev.hnm.workbench.core.playback.PatternRenderer

/**
 * The desktop [PatternPlayer]: renders a pattern's audio from the shared IR and hands the stream to an
 * [AudioBackend]. Desktops have no actuator, so only the audio path plays; haptics stay on-screen.
 * A failure to open an audio line is reported through [onMessage] rather than thrown into the UI.
 */
class JvmPatternPlayer(
    private val backend: AudioBackend = JvmAudioBackend(),
    private val renderer: PatternRenderer = DefaultPatternRenderer(),
    private val sampleRate: Int = 48_000,
    private val onMessage: (String) -> Unit = {},
) : PatternPlayer {

    override fun play(pattern: HapticAudioPattern) {
        val stream = renderer.renderAudio(pattern, sampleRate)
        try {
            backend.start(stream, sampleRate)
        } catch (t: Throwable) {
            onMessage("audio failed: ${t.message}")
        }
    }

    companion object {
        /**
         * True when the default backend can open an audio output line here. Probes with a short silent
         * stream; false on headless machines with no sound device.
         */
        fun audioAvailable(): Boolean = try {
            val probe = JvmAudioBackend()
            probe.start(ArrayFloatStream(FloatArray(0), 48_000), 48_000)
            probe.stop()
            true
        } catch (t: Throwable) {
            false
        }
    }
}
