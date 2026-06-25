package dev.hnm.workbench.android

import android.os.Handler
import android.os.Vibrator
import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.core.playback.PatternPlayer

/**
 * The real, on-device [PatternPlayer] handed to the workbench so its Play button *feels* a pattern.
 * It renders both the haptic schedule and the audio stream from the same `core` IR, then triggers
 * them together — exactly what the standalone player [MainActivity] does, factored out so the Compose
 * workbench can share it.
 */
class AndroidPatternPlayer(
    private val vibrator: Vibrator,
    private val capabilities: HapticCapabilities,
    private val handler: Handler,
    private val audio: AudioPlayer,
    private val onMessage: (String) -> Unit = {},
    private val sampleRate: Int = 48_000,
) : PatternPlayer {

    private val renderer = DefaultPatternRenderer()

    override fun play(pattern: HapticAudioPattern) {
        // Render both paths first, then trigger together so they stay coincident.
        val commands = renderer.scheduleHaptics(pattern, capabilities)
        val stream = renderer.renderAudio(pattern, sampleRate)
        AndroidHaptics.playPattern(vibrator, pattern, commands, handler, onMessage)
        runCatching { audio.play(stream) }.onFailure { onMessage("audio failed: ${it.message}") }
    }
}
