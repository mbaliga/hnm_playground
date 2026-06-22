package dev.hnm.workbench.core.dsp

import dev.hnm.workbench.core.ir.Filter
import dev.hnm.workbench.core.ir.FilterType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A transposed-direct-form-II biquad (RBJ cookbook coefficients) supporting low/high/band-pass.
 * Coefficients can be retuned per-sample so an AUDIO_FILTER_CUTOFF curve animates the filter live.
 */
class Biquad(private val type: FilterType, sampleRate: Int, resonance: Double) {
    private val sr = sampleRate.toDouble()
    private val q = resonance.coerceAtLeast(0.0001)

    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var z1 = 0.0
    private var z2 = 0.0

    fun setCutoff(cutoffHz: Double) {
        val f = cutoffHz.coerceIn(10.0, sr * 0.45)
        val w0 = 2.0 * PI * f / sr
        val cosw = cos(w0)
        val sinw = sin(w0)
        val alpha = sinw / (2.0 * q)

        val a0: Double
        when (type) {
            FilterType.LOWPASS -> {
                val nb1 = 1.0 - cosw
                b0 = nb1 / 2.0; b1 = nb1; b2 = nb1 / 2.0
                a0 = 1.0 + alpha; a1 = -2.0 * cosw; a2 = 1.0 - alpha
            }
            FilterType.HIGHPASS -> {
                val nb1 = 1.0 + cosw
                b0 = nb1 / 2.0; b1 = -nb1; b2 = nb1 / 2.0
                a0 = 1.0 + alpha; a1 = -2.0 * cosw; a2 = 1.0 - alpha
            }
            FilterType.BANDPASS -> {
                b0 = alpha; b1 = 0.0; b2 = -alpha
                a0 = 1.0 + alpha; a1 = -2.0 * cosw; a2 = 1.0 - alpha
            }
        }
        // Normalize.
        b0 /= a0; b1 /= a0; b2 /= a0
        a1 /= a0; a2 /= a0
    }

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    companion object {
        fun from(filter: Filter, sampleRate: Int): Biquad =
            Biquad(filter.type, sampleRate, filter.resonance).apply { setCutoff(filter.cutoffHz) }
    }
}
