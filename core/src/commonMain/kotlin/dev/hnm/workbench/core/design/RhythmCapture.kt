package dev.hnm.workbench.core.design

import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.Seconds
import dev.hnm.workbench.core.ir.Transient

/** A single captured tap: when it happened and how hard (0..1). */
data class Tap(val time: Seconds, val pressure: Double = 1.0)

/**
 * Capture-a-rhythm input (M7): turn a stream of taps into a haptic pattern. Tap times are normalized
 * so the first tap lands at t=0. Pressure maps to intensity; the inter-tap interval maps to sharpness
 * (quick taps feel crisper) so a tapped rhythm comes back feeling like what you played.
 */
object RhythmCapture {

    fun fromTaps(
        taps: List<Tap>,
        name: String = "Captured",
        trackId: String = "h1",
    ): HapticAudioPattern {
        val events = toTransients(taps)
        return HapticAudioPattern(name = name, tracks = listOf(HapticTrack(id = trackId, events = events)))
    }

    /** Convenience for raw timestamps with a fixed intensity/sharpness. */
    fun fromTimes(
        timesSeconds: List<Seconds>,
        intensity: Double = 0.8,
        sharpness: Double = 0.7,
        name: String = "Captured",
    ): HapticAudioPattern =
        fromTaps(timesSeconds.map { Tap(it, intensity) }, name).let { p ->
            // Override the interval-derived sharpness with the caller's fixed value.
            val track = p.tracks.first() as HapticTrack
            p.copy(tracks = listOf(track.copy(events = track.events.map { (it as Transient).copy(sharpness = sharpness) })))
        }

    private fun toTransients(taps: List<Tap>): List<Transient> {
        if (taps.isEmpty()) return emptyList()
        val sorted = taps.sortedBy { it.time }
        val t0 = sorted.first().time
        return sorted.mapIndexed { i, tap ->
            val prevGap = if (i == 0) Double.MAX_VALUE else sorted[i].time - sorted[i - 1].time
            Transient(
                time = tap.time - t0,
                intensity = tap.pressure.coerceIn(0.0, 1.0),
                sharpness = gapToSharpness(prevGap),
            )
        }
    }

    /** Faster taps (smaller gaps) -> sharper. ~50 ms or less is fully crisp; ~500 ms+ is dull. */
    private fun gapToSharpness(gapSeconds: Double): Double {
        if (gapSeconds == Double.MAX_VALUE) return 0.8 // first tap: neutral-crisp
        val clamped = gapSeconds.coerceIn(0.05, 0.5)
        // 0.05s -> 1.0 ; 0.5s -> 0.2
        val t = (clamped - 0.05) / (0.5 - 0.05)
        return (1.0 - t * 0.8)
    }
}
