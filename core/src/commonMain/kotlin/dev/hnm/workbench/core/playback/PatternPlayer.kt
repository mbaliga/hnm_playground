package dev.hnm.workbench.core.playback

import dev.hnm.workbench.core.ir.HapticAudioPattern

/**
 * The seam that lets the shared workbench UI trigger real playback without depending on any platform.
 *
 * The editor (commonMain Compose) holds a [PatternPlayer] and calls [play] when the user taps Play.
 * Desktop passes [None] (no actuator to feel anyway — it's a visual editor there); Android passes an
 * implementation wired to the real `Vibrator` + `AudioTrack`, so the same workbench both *designs* and
 * *feels* a pattern on the device.
 */
fun interface PatternPlayer {
    fun play(pattern: HapticAudioPattern)

    companion object {
        /** A no-op player for hosts without an actuator (e.g. the desktop window). */
        val None: PatternPlayer = PatternPlayer { }
    }
}
