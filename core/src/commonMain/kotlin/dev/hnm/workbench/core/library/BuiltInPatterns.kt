package dev.hnm.workbench.core.library

import dev.hnm.workbench.core.ir.AudioTrack
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.Envelope
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.OscEvent
import dev.hnm.workbench.core.ir.Primitive
import dev.hnm.workbench.core.ir.PrimitiveType
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.ir.Waveform

/**
 * Reference patterns covering the common UI-feedback vocabulary. Each pattern is fully self-contained
 * (no external samples needed) and playable on any capability tier via the renderer's fallback ladder.
 */
object BuiltInPatterns {

    /** A sharp tick, then a stronger softer click 80 ms later. The canonical "action confirmed." */
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

    /** Two hard thumps in quick succession. Unmistakable "something went wrong." */
    val ERROR = HapticAudioPattern(
        name = "Error",
        tracks = listOf(
            HapticTrack(
                id = "h1",
                events = listOf(
                    Transient(time = 0.0, intensity = 1.0, sharpness = 0.2),
                    Transient(time = 0.12, intensity = 1.0, sharpness = 0.2),
                ),
            ),
            AudioTrack(
                id = "a1",
                events = listOf(
                    OscEvent(
                        time = 0.0, duration = 0.08, waveform = Waveform.SAW,
                        frequencyHz = 180.0, gain = 0.8,
                        envelope = Envelope(attack = 0.003, release = 0.05),
                    ),
                    OscEvent(
                        time = 0.12, duration = 0.08, waveform = Waveform.SAW,
                        frequencyHz = 160.0, gain = 0.8,
                        envelope = Envelope(attack = 0.003, release = 0.05),
                    ),
                ),
            ),
        ),
    )

    /** Three ascending pulses; "pay attention but keep going." */
    val WARNING = HapticAudioPattern(
        name = "Warning",
        tracks = listOf(
            HapticTrack(
                id = "h1",
                events = listOf(
                    Transient(time = 0.0, intensity = 0.6, sharpness = 0.7),
                    Transient(time = 0.1, intensity = 0.8, sharpness = 0.7),
                    Transient(time = 0.2, intensity = 1.0, sharpness = 0.7),
                ),
            ),
            AudioTrack(
                id = "a1",
                events = listOf(
                    OscEvent(time = 0.0, duration = 0.05, waveform = Waveform.SINE, frequencyHz = 440.0, gain = 0.5),
                    OscEvent(time = 0.1, duration = 0.05, waveform = Waveform.SINE, frequencyHz = 554.0, gain = 0.6),
                    OscEvent(time = 0.2, duration = 0.05, waveform = Waveform.SINE, frequencyHz = 659.0, gain = 0.7),
                ),
            ),
        ),
    )

    /** Satisfying upward sweep — "task complete, all good." */
    val SUCCESS = HapticAudioPattern(
        name = "Success",
        tracks = listOf(
            HapticTrack(
                id = "h1",
                events = listOf(
                    Transient(time = 0.0, intensity = 0.7, sharpness = 0.8),
                    Continuous(
                        time = 0.04, duration = 0.18, intensity = 0.6, sharpness = 0.4,
                        envelope = Envelope(attack = 0.03, sustain = 0.8, release = 0.06),
                    ),
                ),
            ),
            AudioTrack(
                id = "a1",
                events = listOf(
                    OscEvent(time = 0.0, duration = 0.05, waveform = Waveform.SINE, frequencyHz = 523.0, gain = 0.6),
                    OscEvent(
                        time = 0.06, duration = 0.14, waveform = Waveform.SINE, frequencyHz = 784.0, gain = 0.7,
                        envelope = Envelope(attack = 0.02, release = 0.08),
                    ),
                ),
            ),
        ),
    )

    /** A single clean transient. Selection feedback, button press, minimal acknowledgement. */
    val TAP = HapticAudioPattern(
        name = "Tap",
        tracks = listOf(
            HapticTrack(
                id = "h1",
                events = listOf(
                    Transient(time = 0.0, intensity = 0.75, sharpness = 0.85),
                ),
            ),
        ),
    )

    /** Two sharp taps in quick succession — double-tap or toggle confirmation. */
    val DOUBLE_TAP = HapticAudioPattern(
        name = "Double tap",
        tracks = listOf(
            HapticTrack(
                id = "h1",
                events = listOf(
                    Transient(time = 0.0, intensity = 0.8, sharpness = 0.9),
                    Transient(time = 0.1, intensity = 0.8, sharpness = 0.9),
                ),
            ),
        ),
    )

    /** A very soft, gentle tick. Cursor movement, list scroll step. */
    val SELECTION = HapticAudioPattern(
        name = "Selection",
        tracks = listOf(
            HapticTrack(
                id = "h1",
                events = listOf(
                    Transient(time = 0.0, intensity = 0.35, sharpness = 0.95),
                ),
            ),
        ),
    )

    /**
     * A short directional buzz that decays — like a swipe completing. The quick-rise primitive at the
     * start gives a sense of direction; the short continuous follows through.
     */
    val SWIPE = HapticAudioPattern(
        name = "Swipe",
        tracks = listOf(
            HapticTrack(
                id = "h1",
                events = listOf(
                    Primitive(time = 0.0, type = PrimitiveType.QUICK_RISE, scale = 0.8),
                    Continuous(
                        time = 0.04, duration = 0.12, intensity = 0.5, sharpness = 0.6,
                        envelope = Envelope(sustain = 0.6, release = 0.08),
                    ),
                ),
            ),
        ),
    )

    /** Spring-back snap — the finger goes, and the thing stays. Modal dismiss, rubber-band. */
    val SNAP = HapticAudioPattern(
        name = "Snap",
        tracks = listOf(
            HapticTrack(
                id = "h1",
                events = listOf(
                    Transient(time = 0.0, intensity = 1.0, sharpness = 1.0),
                    Transient(time = 0.06, intensity = 0.4, sharpness = 0.5),
                    Transient(time = 0.11, intensity = 0.15, sharpness = 0.4),
                ),
            ),
            AudioTrack(
                id = "a1",
                events = listOf(
                    OscEvent(
                        time = 0.0, duration = 0.04, waveform = Waveform.NOISE,
                        frequencyHz = 0.0, gain = 0.9,
                        envelope = Envelope(attack = 0.001, release = 0.03),
                    ),
                ),
            ),
        ),
    )

    /** Heartbeat — two pulses (lub-dub) then silence. Alive / loading / pulse metaphor. */
    val HEARTBEAT = HapticAudioPattern(
        name = "Heartbeat",
        tracks = listOf(
            HapticTrack(
                id = "h1",
                events = listOf(
                    Transient(time = 0.0, intensity = 0.9, sharpness = 0.3),
                    Transient(time = 0.15, intensity = 0.7, sharpness = 0.2),
                ),
            ),
        ),
    )

    /** Three quick ticks — "upload sent", "message delivered", three-dot ellipsis becoming a result. */
    val TRIPLE_TICK = HapticAudioPattern(
        name = "Triple tick",
        tracks = listOf(
            HapticTrack(
                id = "h1",
                events = listOf(
                    Transient(time = 0.0, intensity = 0.65, sharpness = 0.9),
                    Transient(time = 0.07, intensity = 0.65, sharpness = 0.9),
                    Transient(time = 0.14, intensity = 0.65, sharpness = 0.9),
                ),
            ),
        ),
    )

    /**
     * Toggle ON — light → firm transition, like latching a switch. Use the SLOW_RISE primitive so it
     * builds and settles, then a crisp close transient.
     */
    val TOGGLE_ON = HapticAudioPattern(
        name = "Toggle ON",
        tracks = listOf(
            HapticTrack(
                id = "h1",
                events = listOf(
                    Primitive(time = 0.0, type = PrimitiveType.SLOW_RISE, scale = 0.7),
                    Transient(time = 0.22, intensity = 0.85, sharpness = 0.7),
                ),
            ),
        ),
    )

    /**
     * Toggle OFF — firm → light, reverse of TOGGLE_ON. The quick-fall primitive gives the "releasing"
     * sensation before a softer close.
     */
    val TOGGLE_OFF = HapticAudioPattern(
        name = "Toggle OFF",
        tracks = listOf(
            HapticTrack(
                id = "h1",
                events = listOf(
                    Transient(time = 0.0, intensity = 0.75, sharpness = 0.75),
                    Primitive(time = 0.04, type = PrimitiveType.QUICK_FALL, scale = 0.65),
                ),
            ),
        ),
    )

    /** Gentle incoming notification — soft long buzz with a finishing tick. Non-urgent. */
    val NOTIFICATION = HapticAudioPattern(
        name = "Notification",
        tracks = listOf(
            HapticTrack(
                id = "h1",
                events = listOf(
                    Continuous(
                        time = 0.0, duration = 0.25, intensity = 0.55, sharpness = 0.3,
                        envelope = Envelope(attack = 0.06, sustain = 0.8, release = 0.08),
                    ),
                    Transient(time = 0.28, intensity = 0.5, sharpness = 0.6),
                ),
            ),
            AudioTrack(
                id = "a1",
                events = listOf(
                    OscEvent(
                        time = 0.0, duration = 0.2, waveform = Waveform.SINE,
                        frequencyHz = 660.0, gain = 0.5,
                        envelope = Envelope(attack = 0.05, sustain = 0.7, release = 0.1),
                    ),
                ),
            ),
        ),
    )

    /** Spin-up buzz followed by a soft landing. Reorder/drag-complete/lock metaphor. */
    val SPIN_LOCK = HapticAudioPattern(
        name = "Spin lock",
        tracks = listOf(
            HapticTrack(
                id = "h1",
                events = listOf(
                    Primitive(time = 0.0, type = PrimitiveType.SPIN, scale = 0.9),
                    Transient(time = 0.3, intensity = 0.7, sharpness = 0.55),
                ),
            ),
        ),
    )

    /** All patterns in a logical order for display in library panels. */
    val ALL: List<HapticAudioPattern> = listOf(
        TAP, SELECTION, DOUBLE_TAP, CONFIRM, SUCCESS, ERROR, WARNING,
        NOTIFICATION, TOGGLE_ON, TOGGLE_OFF, SWIPE, SNAP, HEARTBEAT,
        TRIPLE_TICK, SPIN_LOCK,
    )
}
