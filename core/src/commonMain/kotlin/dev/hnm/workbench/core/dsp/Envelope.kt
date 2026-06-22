package dev.hnm.workbench.core.dsp

import dev.hnm.workbench.core.ir.Envelope
import dev.hnm.workbench.core.ir.Seconds

/**
 * Evaluates an ADSR [Envelope] for an event of a given sustained [duration]. The total audible length
 * is `duration + release`; gain is 0 before the event and after the release tail.
 */
class EnvelopeShaper(private val env: Envelope, private val duration: Seconds) {

    /** Total length including the release tail. */
    val totalDuration: Seconds get() = duration + env.release

    /** Envelope gain in 0..1 at [t] seconds relative to the event start. */
    fun gainAt(t: Seconds): Double {
        if (t < 0.0) return 0.0
        val a = env.attack
        val d = env.decay
        val s = env.sustain.coerceIn(0.0, 1.0)
        val r = env.release

        // Sustain phase ends when the held note ends.
        if (t <= a) {
            return if (a <= 0.0) 1.0 else t / a
        }
        if (t <= a + d) {
            if (d <= 0.0) return s
            val x = (t - a) / d
            return 1.0 + (s - 1.0) * x // 1 -> sustain
        }
        if (t <= duration) {
            return s
        }
        if (t <= duration + r) {
            if (r <= 0.0) return 0.0
            val x = (t - duration) / r
            return s * (1.0 - x) // sustain -> 0
        }
        return 0.0
    }
}
