package dev.hnm.workbench.core.widget

import dev.hnm.workbench.core.dsp.CurveSampler
import dev.hnm.workbench.core.dsp.EnvelopeShaper
import dev.hnm.workbench.core.dsp.HapticMapping
import dev.hnm.workbench.core.dsp.PatternTiming
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.CurveParam
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticEvent
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.ParameterCurve
import dev.hnm.workbench.core.ir.Primitive
import dev.hnm.workbench.core.ir.PrimitiveType
import dev.hnm.workbench.core.ir.Transient
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A pattern's felt shape over time, reduced to [columns] so a widget can draw it: per column, the
 * strongest felt intensity (0..1) and that event's authored sharpness (null where nothing is felt).
 * Both are read from the IR the way the renderer reads it: transients and primitives as short impulses
 * (swept primitives with their rise/fall/spin shape), continuous events through their envelope and any
 * HAPTIC_INTENSITY curve, sharpness through any HAPTIC_SHARPNESS curve. Muted tracks are skipped.
 */
data class PatternSketch(
    val durationSeconds: Double,
    val intensity: List<Double>,
    val sharpness: List<Double?>,
    val eventCount: Int,
    val peakIntensity: Double,
    val sharpnessMin: Double?,
    val sharpnessMax: Double?,
) {
    val columns: Int get() = intensity.size

    /** True when the pattern has nothing to feel (no unmuted haptic events). */
    val isSilent: Boolean get() = eventCount == 0 || peakIntensity <= SILENT

    /**
     * The shape as block characters, one per column: '▁' where nothing is felt, then '▂'..'█' by
     * intensity. A text-only glyph for places a bitmap would be too heavy (each effect pad).
     */
    fun sparkline(): String = buildString {
        for (v in intensity) {
            append(if (v <= SILENT) BLOCKS[0] else BLOCKS[1 + ((v.coerceIn(0.0, 1.0)) * (BLOCKS.length - 2)).roundToInt()])
        }
    }

    companion object {
        private const val BLOCKS = "▁▂▃▄▅▆▇█"
        private const val SILENT = 0.001

        /** How long a transient (or a non-swept primitive) is drawn: the renderer's impulse length. */
        const val IMPULSE_SECONDS = 0.02

        /** The shortest window drawn, so a single 20 ms tap reads as a tap rather than a full-width bar. */
        const val MIN_WINDOW_SECONDS = 0.1

        /** Sub-samples per column, so a fast-moving curve (a rough texture) isn't missed between columns. */
        private const val SUBSAMPLES = 4

        fun of(pattern: HapticAudioPattern, columns: Int = 48): PatternSketch {
            require(columns > 0) { "a sketch needs at least one column" }
            val tracks = pattern.tracks.filterIsInstance<HapticTrack>().filterNot { it.muted }
            val events = tracks.flatMap { track -> track.events.map { it to track } }
            val end = maxOf(
                PatternTiming.durationSeconds(pattern),
                events.maxOfOrNull { (e, _) -> e.time + spanOf(e) } ?: 0.0,
                if (events.isEmpty()) 0.0 else MIN_WINDOW_SECONDS,
            )
            if (events.isEmpty() || end <= 0.0) {
                return PatternSketch(
                    durationSeconds = end,
                    intensity = List(columns) { 0.0 },
                    sharpness = List(columns) { null },
                    eventCount = 0,
                    peakIntensity = 0.0,
                    sharpnessMin = null,
                    sharpnessMax = null,
                )
            }

            val step = end / columns
            val intensity = DoubleArray(columns)
            val sharpness = arrayOfNulls<Double>(columns)
            for (c in 0 until columns) {
                val t0 = c * step
                val t1 = t0 + step
                var best = 0.0
                var bestSharpness: Double? = null
                for ((event, track) in events) {
                    val (level, sharp) = levelIn(event, track, t0, t1)
                    if (level > best) {
                        best = level
                        bestSharpness = sharp
                    }
                }
                intensity[c] = best.coerceIn(0.0, 1.0)
                sharpness[c] = if (best > SILENT) bestSharpness?.coerceIn(0.0, 1.0) else null
            }

            val authored = events.map { (e, track) -> authoredSharpness(e, track) }
            return PatternSketch(
                durationSeconds = end,
                intensity = intensity.toList(),
                sharpness = sharpness.toList(),
                eventCount = events.size,
                peakIntensity = intensity.maxOrNull() ?: 0.0,
                sharpnessMin = authored.minOrNull(),
                sharpnessMax = authored.maxOrNull(),
            )
        }

        /** How long an event is felt for, as drawn. */
        private fun spanOf(e: HapticEvent): Double = when (e) {
            is Transient -> IMPULSE_SECONDS
            is Continuous -> e.duration + e.envelope.release
            is Primitive -> sweepSeconds(e.type) ?: IMPULSE_SECONDS
        }

        /** Swept primitives' lengths, as the renderer's voice-coil approximations draw them. */
        private fun sweepSeconds(type: PrimitiveType): Double? = when (type) {
            PrimitiveType.SLOW_RISE -> 0.30
            PrimitiveType.QUICK_RISE, PrimitiveType.QUICK_FALL, PrimitiveType.SPIN -> 0.15
            else -> null
        }

        private fun curve(track: HapticTrack, param: CurveParam): ParameterCurve? =
            track.curves.firstOrNull { it.parameter == param }

        private fun authoredSharpness(e: HapticEvent, track: HapticTrack): Double = when (e) {
            is Transient -> CurveSampler.sampleOrDefault(curve(track, CurveParam.HAPTIC_SHARPNESS), e.time, e.sharpness)
            is Continuous -> CurveSampler.sampleOrDefault(curve(track, CurveParam.HAPTIC_SHARPNESS), e.time, e.sharpness)
            is Primitive -> HapticMapping.primitiveAsTransient(e.type, e.scale).second
        }

        /** The strongest level [e] reaches inside [t0, t1), with its sharpness there. */
        private fun levelIn(e: HapticEvent, track: HapticTrack, t0: Double, t1: Double): Pair<Double, Double> {
            val start = e.time
            val stop = start + spanOf(e)
            if (stop <= t0 || start >= t1) return 0.0 to 0.0
            val sharpnessCurve = curve(track, CurveParam.HAPTIC_SHARPNESS)
            return when (e) {
                is Transient ->
                    e.intensity.coerceIn(0.0, 1.0) to CurveSampler.sampleOrDefault(sharpnessCurve, e.time, e.sharpness)
                is Primitive -> {
                    val (level, sharp) = HapticMapping.primitiveAsTransient(e.type, e.scale)
                    val sweep = sweepSeconds(e.type)
                    if (sweep == null) {
                        level.coerceIn(0.0, 1.0) to sharp
                    } else {
                        var peak = 0.0
                        forEachSubsample(max(t0, start), minOf(t1, stop)) { t ->
                            val x = ((t - start) / sweep).coerceIn(0.0, 1.0)
                            val shape = when (e.type) {
                                PrimitiveType.QUICK_FALL -> 1.0 - x
                                PrimitiveType.SPIN -> 0.5 + 0.5 * sin(2 * PI * 6 * x)
                                else -> x
                            }
                            peak = max(peak, level * shape)
                        }
                        peak.coerceIn(0.0, 1.0) to sharp
                    }
                }
                is Continuous -> {
                    val shaper = EnvelopeShaper(e.envelope, e.duration)
                    val intensityCurve = curve(track, CurveParam.HAPTIC_INTENSITY)
                    var peak = 0.0
                    var sharpAtPeak = e.sharpness
                    forEachSubsample(max(t0, start), minOf(t1, stop)) { t ->
                        val mul = CurveSampler.sampleOrDefault(intensityCurve, t, 1.0)
                        val v = e.intensity * mul * shaper.gainAt(t - start)
                        if (v > peak) {
                            peak = v
                            sharpAtPeak = CurveSampler.sampleOrDefault(sharpnessCurve, t, e.sharpness)
                        }
                    }
                    peak.coerceIn(0.0, 1.0) to sharpAtPeak
                }
            }
        }

        private inline fun forEachSubsample(from: Double, to: Double, action: (Double) -> Unit) {
            if (to <= from) {
                action(from)
                return
            }
            for (i in 0 until SUBSAMPLES) action(from + (to - from) * (i + 0.5) / SUBSAMPLES)
        }
    }
}
