package dev.hnm.workbench.android

import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import dev.hnm.workbench.core.companion.PatternSafety
import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.playback.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A single owner cancels preparation, queued motor events and audio as one session. */
class AndroidPatternPlayer(
    private val vibrator: Vibrator,
    private val context: android.content.Context,
    private val capabilities: HapticCapabilities,
    private val audio: AudioPlayer = AudioPlayer(),
    private val onMessage: (String) -> Unit = {},
) : PatternPlayer {
    private val handler = Handler(Looper.getMainLooper())
    private val renderer = DefaultPatternRenderer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var generation = 0L
    private val mutableStatus = MutableStateFlow(PlaybackStatus())
    override val status = mutableStatus.asStateFlow()

    override fun play(pattern: HapticAudioPattern) {
        stop()
        val session = generation
        mutableStatus.value = PlaybackStatus(true, "Preparing ${pattern.name}")
        job = scope.launch {
            try {
                PatternSafety.validate(pattern)
                require(pattern.couplings.none { it.mode == dev.hnm.workbench.core.ir.CouplingMode.AUDIO_DRIVES_HAPTICS }) {
                    "Audio-driven haptic coupling is available in waveform export only. Use explicit haptic events for Android playback."
                }
                require(pattern.tracks.filterIsInstance<dev.hnm.workbench.core.ir.AudioTrack>()
                    .filterNot { it.muted }.none { track -> track.events.any { it is dev.hnm.workbench.core.ir.SampleEvent } }) {
                    "Sample audio requires source assets. Use synthesized audio or haptics for now."
                }
                val prepared = withContext(Dispatchers.Default) {
                    val commands = renderer.scheduleHaptics(pattern, capabilities)
                    val hasAudio = pattern.tracks.any { it is dev.hnm.workbench.core.ir.AudioTrack && !it.muted && it.events.isNotEmpty() } ||
                        pattern.couplings.any { it.mode == dev.hnm.workbench.core.ir.CouplingMode.HAPTICS_DRIVES_AUDIO }
                    val samples = if (hasAudio) renderer.renderAudio(pattern, 48_000).readAll() else FloatArray(0)
                    commands to samples
                }
                ensureActive()
                if (session != generation) return@launch
                // Audio allocation/writing happens before either output is started.
                audio.prepare(prepared.second, 48_000)
                audio.requestFocus(context) { stop() }
                mutableStatus.value = PlaybackStatus(true, "Playing ${pattern.name}")
                var hapticError: String? = null
                if(dev.hnm.workbench.core.companion.WristPatterns.hasHaptics(pattern)) AndroidHaptics.playPattern(vibrator, pattern, prepared.first, handler) { message ->
                    hapticError = message
                    mutableStatus.value = PlaybackStatus(true, message)
                    onMessage(message)
                }
                audio.start()
                delay((maxOf(PatternSafety.duration(pattern), prepared.second.size / 48_000.0) * 1000).toLong() + 100)
                if (session == generation) {
                    cancelOutputs()
                    mutableStatus.value = PlaybackStatus(false, hapticError ?: "Ready")
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (session == generation) {
                    cancelOutputs()
                    mutableStatus.value = PlaybackStatus(false, "Could not play: ${e.message}")
                    onMessage(mutableStatus.value.message)
                }
            }
        }
    }

    private fun cancelOutputs() {
        handler.removeCallbacksAndMessages(null)
        vibrator.cancel()
        audio.release()
    }

    override fun stop() {
        generation++
        job?.cancel()
        job = null
        cancelOutputs()
        mutableStatus.value = PlaybackStatus(false, "Stopped")
    }

    fun close() { stop(); scope.cancel() }
}
