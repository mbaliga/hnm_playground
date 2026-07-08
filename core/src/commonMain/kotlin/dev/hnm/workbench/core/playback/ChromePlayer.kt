package dev.hnm.workbench.core.playback

import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.Primitive
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.library.ChromeSemantic

/** The Interface-feels setting (UX brief §3.5/§6.5): how loud the app's own chrome.* feedback is. */
enum class InterfaceFeelLevel(val intensityScale: Double) {
    OFF(0.0),
    SUBTLE(0.55),
    FULL(1.0),
    ;

    companion object {
        val Default = SUBTLE
    }
}

/**
 * Gates and scales the app's own `chrome.*` interface feedback (UX brief §3.3). Wraps a real
 * [PatternPlayer] so chrome plays through the same `scheduleHaptics`/capability-degrade path as every
 * user pattern — no special-cased rendering.
 *
 * Three gates, all must pass:
 *  - **Priority latch** ([busy]): chrome never fires while a real pattern is playing or a slider
 *    live-preview is dragging — the user's own pattern owns the actuator. The caller (UI layer) is
 *    responsible for setting/clearing this around real playback and drag gestures.
 *  - **Capability** ([hasAmplitudeControl]): chrome only plays on amplitude-capable hardware — below
 *    that tier, silence beats mush (an on/off-only buzz for every tap reads as noise, not feedback).
 *  - **Interface-feels level** ([level]): `OFF` suppresses everything; `SUBTLE`/`FULL` scale intensity
 *    via [InterfaceFeelLevel.intensityScale] rather than gating individual semantics.
 */
class ChromePlayer(
    private val player: PatternPlayer,
    var hasAmplitudeControl: Boolean = true,
    var level: InterfaceFeelLevel = InterfaceFeelLevel.Default,
) {
    /** True while a real pattern is playing or a slider is being dragged; chrome is suppressed. */
    var busy: Boolean = false

    fun play(semantic: ChromeSemantic) {
        if (busy || level == InterfaceFeelLevel.OFF || !hasAmplitudeControl) return
        player.play(scaled(semantic.pattern, level.intensityScale))
    }

    /** Whether [play] would actually produce a sound/feel right now — for UI to reason about, if needed. */
    fun wouldPlay(): Boolean = !busy && level != InterfaceFeelLevel.OFF && hasAmplitudeControl

    private fun scaled(pattern: HapticAudioPattern, factor: Double): HapticAudioPattern {
        if (factor >= 1.0) return pattern
        return pattern.copy(
            tracks = pattern.tracks.map { track ->
                if (track !is HapticTrack) track
                else track.copy(
                    events = track.events.map { event ->
                        when (event) {
                            is Transient -> event.copy(intensity = event.intensity * factor)
                            is Continuous -> event.copy(intensity = event.intensity * factor)
                            is Primitive -> event.copy(scale = event.scale * factor)
                        }
                    },
                )
            },
        )
    }
}
