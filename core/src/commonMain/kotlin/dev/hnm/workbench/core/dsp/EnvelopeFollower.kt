package dev.hnm.workbench.core.dsp

import kotlin.math.abs
import kotlin.math.exp

/**
 * A classic attack/release envelope follower. This is the heart of audio->haptics coupling (§3, M3):
 * it tracks the audio amplitude so the haptic actuator "feels" the sound. Attack/release are time
 * constants in milliseconds.
 */
class EnvelopeFollower(attackMs: Double, releaseMs: Double, sampleRate: Int) {
    private val aCoeff = timeConstant(attackMs, sampleRate)
    private val rCoeff = timeConstant(releaseMs, sampleRate)
    private var env = 0.0

    fun process(input: Double): Double {
        val x = abs(input)
        val coeff = if (x > env) aCoeff else rCoeff
        env = x + coeff * (env - x)
        return env
    }

    fun reset() {
        env = 0.0
    }

    private fun timeConstant(ms: Double, sampleRate: Int): Double {
        if (ms <= 0.0) return 0.0
        return exp(-1.0 / (ms * 0.001 * sampleRate))
    }
}
