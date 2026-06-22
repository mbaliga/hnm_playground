package dev.hnm.workbench.core.export

import dev.hnm.workbench.core.dsp.EnvelopeShaper
import dev.hnm.workbench.core.dsp.HapticMapping
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticEvent
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.Primitive
import dev.hnm.workbench.core.ir.PrimitiveType
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.playback.HapticCapabilities
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Generates paste-ready Kotlin that builds an Android `VibrationEffect` (§5b — the dev-tool payoff).
 *
 * The exporter picks the form from a target-capability flag:
 *  - If the target supports composition primitives and every haptic event is a transient/primitive,
 *    it emits `VibrationEffect.startComposition()...compose()`.
 *  - Otherwise it emits a `createWaveform(timings, amplitudes, -1)` variant built from the haptic
 *    intensity envelope across the whole timeline.
 */
object KotlinVibrationEffectExporter {

    fun export(pattern: HapticAudioPattern, target: HapticCapabilities = HapticCapabilities.LRA_FULL): String {
        val events = pattern.tracks.filterIsInstance<HapticTrack>()
            .filterNot { it.muted }
            .flatMap { it.events }
            .sortedBy { it.time }

        val funcName = functionName(pattern.name)
        val canCompose = target.supportedPrimitives.isNotEmpty() &&
            events.all { it is Transient || it is Primitive }

        val body = if (canCompose) composition(events, target) else waveform(events)
        return """
            |/** Generated from "${pattern.name}". */
            |fun $funcName(): VibrationEffect =
            |$body
        """.trimMargin()
    }

    private fun composition(events: List<HapticEvent>, target: HapticCapabilities): String {
        val sb = StringBuilder("    VibrationEffect.startComposition()\n")
        var prevTime = 0.0
        for (e in events) {
            val delayMs = ((e.time - prevTime) * 1000).roundToLong().coerceAtLeast(0)
            prevTime = e.time
            val (prim, scale) = when (e) {
                is Transient -> (HapticMapping.transientToPrimitive(e.sharpness, target.supportedPrimitives)
                    ?: PrimitiveType.CLICK) to e.intensity
                is Primitive -> e.type to e.scale
                else -> PrimitiveType.CLICK to 1.0
            }
            sb.append("        .addPrimitive(${primitiveConst(prim)}, ${fmt(scale)}f, $delayMs)\n")
        }
        sb.append("        .compose()")
        return sb.toString()
    }

    private fun waveform(events: List<HapticEvent>): String {
        val (timings, amps) = buildAmplitudeTimeline(events)
        if (timings.isEmpty()) {
            return "    VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)"
        }
        val timingsStr = timings.joinToString(", ") { "${it}L" }
        val ampsStr = amps.joinToString(", ")
        return """    run {
        |        val timings = longArrayOf($timingsStr)
        |        val amplitudes = intArrayOf($ampsStr)
        |        VibrationEffect.createWaveform(timings, amplitudes, -1)
        |    }""".trimMargin()
    }

    /** Build a run-length-encoded amplitude timeline (1 ms resolution) for createWaveform. */
    fun buildAmplitudeTimeline(events: List<HapticEvent>): Pair<LongArray, IntArray> {
        if (events.isEmpty()) return LongArray(0) to IntArray(0)
        val endMs = events.maxOf { eventEndMs(it) }.toInt()
        if (endMs <= 0) return LongArray(0) to IntArray(0)
        val env = IntArray(endMs + 1)
        for (e in events) fillEnvelope(e, env)

        // Run-length encode contiguous equal amplitudes into (timings, amplitudes).
        val timings = ArrayList<Long>()
        val amps = ArrayList<Int>()
        var runStart = 0
        for (i in 1..env.size) {
            if (i == env.size || env[i] != env[runStart]) {
                timings.add((i - runStart).toLong())
                amps.add(env[runStart])
                runStart = i
            }
        }
        return timings.toLongArray() to amps.toIntArray()
    }

    private fun fillEnvelope(e: HapticEvent, env: IntArray) {
        when (e) {
            is Transient -> {
                val start = (e.time * 1000).roundToInt()
                val amp = HapticMapping.intensityToAmplitude255(e.intensity)
                for (i in 0 until 15) { // ~15 ms tick
                    val idx = start + i
                    if (idx in env.indices) env[idx] = maxOf(env[idx], amp)
                }
            }
            is Continuous -> {
                val shaper = EnvelopeShaper(e.envelope, e.duration)
                val start = (e.time * 1000).roundToInt()
                val len = (shaper.totalDuration * 1000).roundToInt()
                for (i in 0 until len) {
                    val idx = start + i
                    if (idx in env.indices) {
                        val amp = HapticMapping.intensityToAmplitude255(e.intensity * shaper.gainAt(i / 1000.0))
                        env[idx] = maxOf(env[idx], amp)
                    }
                }
            }
            is Primitive -> {
                val start = (e.time * 1000).roundToInt()
                val amp = HapticMapping.intensityToAmplitude255(e.scale)
                for (i in 0 until 20) {
                    val idx = start + i
                    if (idx in env.indices) env[idx] = maxOf(env[idx], amp)
                }
            }
        }
    }

    private fun eventEndMs(e: HapticEvent): Long = when (e) {
        is Transient -> ((e.time + 0.015) * 1000).roundToLong()
        is Continuous -> ((e.time + EnvelopeShaper(e.envelope, e.duration).totalDuration) * 1000).roundToLong()
        is Primitive -> ((e.time + 0.02) * 1000).roundToLong()
    }

    private fun primitiveConst(p: PrimitiveType): String = "VibrationEffect.Composition.PRIMITIVE_$p"

    private fun fmt(v: Double): String {
        val r = (v * 100).roundToInt() / 100.0
        return if (r == r.toLong().toDouble()) "${r.toLong()}.0" else r.toString()
    }

    private fun functionName(name: String): String {
        val cleaned = name.trim().split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return "generatedEffect"
        val camel = cleaned.mapIndexed { i, w ->
            if (i == 0) w.replaceFirstChar { it.lowercase() }
            else w.replaceFirstChar { it.uppercase() }
        }.joinToString("")
        return camel + "Effect"
    }
}
