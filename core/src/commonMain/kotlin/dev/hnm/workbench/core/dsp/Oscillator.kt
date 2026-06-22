package dev.hnm.workbench.core.dsp

import dev.hnm.workbench.core.ir.Waveform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random

/**
 * A phase-accumulating oscillator. Phase is normalized 0..1 so a per-sample frequency (driven by a
 * pitch curve) can be fed in without discontinuities. Naive (non-bandlimited) shapes — fine for a
 * feel-and-export workbench; the place a future Rust/cpal graft would add PolyBLEP if needed.
 */
class Oscillator(private val waveform: Waveform, seed: Int = 0x51EED) {
    private var phase = 0.0
    private val rng = Random(seed)

    /** Advance by [frequencyHz] at [sampleRate] and return the next sample in [-1, 1]. */
    fun next(frequencyHz: Double, sampleRate: Int): Double {
        val sample = when (waveform) {
            Waveform.SINE -> kotlin.math.sin(2.0 * PI * phase)
            Waveform.SQUARE -> if (phase < 0.5) 1.0 else -1.0
            Waveform.SAW -> 2.0 * phase - 1.0
            Waveform.TRIANGLE -> 4.0 * abs(phase - 0.5) - 1.0
            Waveform.NOISE -> rng.nextDouble(-1.0, 1.0)
        }
        if (waveform != Waveform.NOISE) {
            phase += frequencyHz / sampleRate
            phase -= kotlin.math.floor(phase) // wrap to [0,1)
        }
        return sample
    }

    fun reset() {
        phase = 0.0
    }
}
