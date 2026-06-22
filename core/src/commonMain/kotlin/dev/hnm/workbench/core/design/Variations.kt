package dev.hnm.workbench.core.design

import dev.hnm.workbench.core.ir.AudioTrack
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticEvent
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.OscEvent
import dev.hnm.workbench.core.ir.Primitive
import dev.hnm.workbench.core.ir.SampleEvent
import dev.hnm.workbench.core.ir.Transient
import kotlin.math.max
import kotlin.random.Random

/**
 * Mutate / randomize for generating variations (M7). Everything is a pure, deterministic transform of
 * the IR given a seed, so the same seed reproduces the same variation — handy for A/B exploration and
 * for regenerating a family of related effects.
 */
object Variations {

    /**
     * Jitter every event's continuous parameters (intensity / sharpness / scale / gain) and timing by
     * up to [amount] (0..1 fraction of full scale; timing jitter is `amount * timeJitterSeconds`).
     * Levels stay clamped to 0..1 and times stay >= 0.
     */
    fun mutate(
        pattern: HapticAudioPattern,
        amount: Double = 0.15,
        seed: Int = 0,
        timeJitterSeconds: Double = 0.02,
    ): HapticAudioPattern {
        val rng = Random(seed)
        fun jitterLevel(v: Double) = (v + rng.nextDouble(-amount, amount)).coerceIn(0.0, 1.0)
        fun jitterTime(t: Double) = max(0.0, t + rng.nextDouble(-amount, amount) * timeJitterSeconds)

        val tracks = pattern.tracks.map { track ->
            when (track) {
                is HapticTrack -> track.copy(events = track.events.map { mutateHaptic(it, ::jitterLevel, ::jitterTime) })
                is AudioTrack -> track.copy(
                    events = track.events.map { e ->
                        when (e) {
                            is OscEvent -> e.copy(time = jitterTime(e.time), gain = jitterLevel(e.gain))
                            is SampleEvent -> e.copy(time = jitterTime(e.time), gain = jitterLevel(e.gain))
                        }
                    },
                )
                else -> track
            }
        }
        return pattern.copy(name = "${pattern.name} (var $seed)", tracks = tracks)
    }

    /** Produce [count] deterministic variations from a base pattern, seeds `0..count-1`. */
    fun family(pattern: HapticAudioPattern, count: Int, amount: Double = 0.15): List<HapticAudioPattern> =
        (0 until count).map { mutate(pattern, amount = amount, seed = it) }

    private fun mutateHaptic(
        e: HapticEvent,
        jitterLevel: (Double) -> Double,
        jitterTime: (Double) -> Double,
    ): HapticEvent = when (e) {
        is Transient -> e.copy(time = jitterTime(e.time), intensity = jitterLevel(e.intensity), sharpness = jitterLevel(e.sharpness))
        is Continuous -> e.copy(time = jitterTime(e.time), intensity = jitterLevel(e.intensity), sharpness = jitterLevel(e.sharpness))
        is Primitive -> e.copy(time = jitterTime(e.time), scale = jitterLevel(e.scale))
    }
}

/** A named pair of patterns for A/B comparison (M4). */
data class ABPair(val a: HapticAudioPattern, val b: HapticAudioPattern) {
    companion object {
        /** B is a single mutated variation of A. */
        fun fromMutation(base: HapticAudioPattern, amount: Double = 0.15, seed: Int = 1): ABPair =
            ABPair(base, Variations.mutate(base, amount = amount, seed = seed))
    }
}
