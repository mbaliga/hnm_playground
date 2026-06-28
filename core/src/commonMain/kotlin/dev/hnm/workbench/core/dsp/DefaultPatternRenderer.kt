package dev.hnm.workbench.core.dsp

import dev.hnm.workbench.core.ir.AudioEvent
import dev.hnm.workbench.core.ir.AudioTrack
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.Coupling
import dev.hnm.workbench.core.ir.CouplingMode
import dev.hnm.workbench.core.ir.CurveParam
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticEvent
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.OscEvent
import dev.hnm.workbench.core.ir.ParameterCurve
import dev.hnm.workbench.core.ir.Primitive
import dev.hnm.workbench.core.ir.PrimitiveType
import dev.hnm.workbench.core.ir.SampleEvent
import dev.hnm.workbench.core.ir.Seconds
import dev.hnm.workbench.core.ir.Track
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.ir.Waveform
import dev.hnm.workbench.core.playback.ArrayFloatStream
import dev.hnm.workbench.core.playback.FloatStream
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.core.playback.HapticCommand
import dev.hnm.workbench.core.playback.EnvelopePoint
import dev.hnm.workbench.core.playback.PatternRenderer
import dev.hnm.workbench.core.playback.PlayEnvelope
import dev.hnm.workbench.core.playback.PlayOneShot
import dev.hnm.workbench.core.playback.PlayPrimitive
import dev.hnm.workbench.core.playback.PlayWaveform
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The reference [PatternRenderer]: pure Kotlin DSP in commonMain. It implements all three output
 * kinds from §2 so backends just consume the side that fits their hardware. The audio and
 * haptic-waveform paths are deliberately self-contained sample loops — the natural Rust/cpal graft
 * target — while [scheduleHaptics] stays Kotlin because it maps onto a native OS API.
 */
class DefaultPatternRenderer(
    private val sampleBank: SampleBank = SampleBank.EMPTY,
) : PatternRenderer {

    // ----- Continuous audio -------------------------------------------------

    override fun renderAudio(pattern: HapticAudioPattern, sampleRate: Int): FloatStream {
        val n = sampleCount(pattern, sampleRate)
        val mix = DoubleArray(n)

        for (track in pattern.tracks) {
            if (track is AudioTrack && !track.muted) renderAudioTrack(track, mix, sampleRate)
        }

        // HAPTICS_DRIVES_AUDIO: sonify the haptic source into the audio mix.
        for (c in pattern.couplings.filter { it.mode == CouplingMode.HAPTICS_DRIVES_AUDIO }) {
            val src = trackById(pattern, c.sourceTrackId) as? HapticTrack ?: continue
            sonifyHapticInto(src, mix, sampleRate, c)
        }

        return ArrayFloatStream(toClampedFloats(mix), sampleRate)
    }

    private fun renderAudioTrack(track: AudioTrack, mix: DoubleArray, sampleRate: Int) {
        val gainCurve = track.curves.find(CurveParam.AUDIO_GAIN)
        val pitchCurve = track.curves.find(CurveParam.AUDIO_PITCH)
        val cutoffCurve = track.curves.find(CurveParam.AUDIO_FILTER_CUTOFF)
        for (event in track.events) {
            when (event) {
                is OscEvent -> renderOsc(event, mix, sampleRate, gainCurve, pitchCurve, cutoffCurve)
                is SampleEvent -> renderSample(event, mix, sampleRate, gainCurve, pitchCurve)
            }
        }
    }

    private fun renderOsc(
        e: OscEvent,
        mix: DoubleArray,
        sampleRate: Int,
        gainCurve: ParameterCurve?,
        pitchCurve: ParameterCurve?,
        cutoffCurve: ParameterCurve?,
    ) {
        val osc = Oscillator(e.waveform)
        val shaper = EnvelopeShaper(e.envelope, e.duration)
        val biquad = e.filter?.let { Biquad.from(it, sampleRate) }
        val start = (e.time * sampleRate).roundToInt()
        val length = (shaper.totalDuration * sampleRate).roundToInt()
        for (i in 0 until length) {
            val idx = start + i
            if (idx < 0) continue
            if (idx >= mix.size) break
            val absT = idx.toDouble() / sampleRate
            val local = i.toDouble() / sampleRate
            val semis = pitchCurve?.let { CurveSampler(it).valueAt(absT) } ?: 0.0
            val freq = e.frequencyHz * 2.0.pow(semis / 12.0)
            var s = osc.next(freq, sampleRate)
            if (biquad != null) {
                cutoffCurve?.let { biquad.setCutoff(CurveSampler(it).valueAt(absT)) }
                s = biquad.process(s)
            }
            val gainMul = gainCurve?.let { CurveSampler(it).valueAt(absT) } ?: 1.0
            mix[idx] += s * e.gain * gainMul * shaper.gainAt(local)
        }
    }

    private fun renderSample(
        e: SampleEvent,
        mix: DoubleArray,
        sampleRate: Int,
        gainCurve: ParameterCurve?,
        pitchCurve: ParameterCurve?,
    ) {
        val sample = sampleBank.resolve(e.sampleId) ?: return
        val rateRatio = sample.sampleRate.toDouble() / sampleRate
        val pitch = 2.0.pow(e.pitchShiftSemitones / 12.0)
        val playLenSamples = (sample.data.size / rateRatio / pitch).toInt()
        val shaper = EnvelopeShaper(e.envelope, playLenSamples.toDouble() / sampleRate)
        val start = (e.time * sampleRate).roundToInt()
        var pos = 0.0
        for (i in 0 until (shaper.totalDuration * sampleRate).roundToInt()) {
            val idx = start + i
            if (idx < 0) { pos += rateRatio * pitch; continue }
            if (idx >= mix.size) break
            val absT = idx.toDouble() / sampleRate
            val local = i.toDouble() / sampleRate
            val s = readInterpolated(sample.data, pos)
            val gainMul = gainCurve?.let { CurveSampler(it).valueAt(absT) } ?: 1.0
            mix[idx] += s * e.gain * gainMul * shaper.gainAt(local)
            pos += rateRatio * pitch
        }
    }

    // ----- Continuous haptic waveform (voice-coil) --------------------------

    override fun renderHapticWaveform(pattern: HapticAudioPattern, sampleRate: Int): FloatStream {
        val n = sampleCount(pattern, sampleRate)
        val buf = DoubleArray(n)

        for (track in pattern.tracks) {
            if (track is HapticTrack && !track.muted) renderHapticTrack(track, buf, sampleRate)
        }

        // AUDIO_DRIVES_HAPTICS: envelope-follow the audio source and drive the actuator.
        for (c in pattern.couplings.filter { it.mode == CouplingMode.AUDIO_DRIVES_HAPTICS }) {
            val src = trackById(pattern, c.sourceTrackId) as? AudioTrack ?: continue
            driveHapticFromAudioInto(src, buf, sampleRate, c)
        }

        return ArrayFloatStream(toClampedFloats(buf), sampleRate)
    }

    private fun renderHapticTrack(track: HapticTrack, buf: DoubleArray, sampleRate: Int) {
        val intensityCurve = track.curves.find(CurveParam.HAPTIC_INTENSITY)
        val sharpnessCurve = track.curves.find(CurveParam.HAPTIC_SHARPNESS)
        for (event in track.events) {
            when (event) {
                is Transient -> renderTransientWave(event, buf, sampleRate, sharpnessCurve)
                is Continuous -> renderContinuousWave(event, buf, sampleRate, intensityCurve, sharpnessCurve)
                is Primitive -> renderPrimitiveWave(event, buf, sampleRate)
            }
        }
    }

    private fun renderTransientWave(e: Transient, buf: DoubleArray, sr: Int, sharpnessCurve: ParameterCurve?) {
        val sharp = sharpnessCurve?.let { CurveSampler(it).valueAt(e.time) } ?: e.sharpness
        val carrier = HapticMapping.sharpnessToCarrierHz(sharp)
        // A short decaying sine impulse; crisper (sharper) transients decay faster.
        val tau = 0.012 - 0.006 * sharp.coerceIn(0.0, 1.0)
        val durSamples = (0.03 * sr).roundToInt()
        val start = (e.time * sr).roundToInt()
        for (i in 0 until durSamples) {
            val idx = start + i
            if (idx !in buf.indices) continue
            val t = i.toDouble() / sr
            buf[idx] += e.intensity * exp(-t / tau) * sin(2 * PI * carrier * t)
        }
    }

    private fun renderContinuousWave(
        e: Continuous,
        buf: DoubleArray,
        sr: Int,
        intensityCurve: ParameterCurve?,
        sharpnessCurve: ParameterCurve?,
    ) {
        val shaper = EnvelopeShaper(e.envelope, e.duration)
        val start = (e.time * sr).roundToInt()
        val len = (shaper.totalDuration * sr).roundToInt()
        for (i in 0 until len) {
            val idx = start + i
            if (idx !in buf.indices) continue
            val absT = idx.toDouble() / sr
            val local = i.toDouble() / sr
            val sharp = sharpnessCurve?.let { CurveSampler(it).valueAt(absT) } ?: e.sharpness
            val intensityMul = intensityCurve?.let { CurveSampler(it).valueAt(absT) } ?: 1.0
            val carrier = HapticMapping.sharpnessToCarrierHz(sharp)
            buf[idx] += e.intensity * intensityMul * shaper.gainAt(local) * sin(2 * PI * carrier * local)
        }
    }

    /** Pre-baked voice-coil approximations of the Android primitives (§6: "no equivalent"). */
    private fun renderPrimitiveWave(e: Primitive, buf: DoubleArray, sr: Int) {
        val (intensity, sharpness) = primitiveToTransient(e.type, e.scale)
        when (e.type) {
            PrimitiveType.QUICK_RISE, PrimitiveType.SLOW_RISE,
            PrimitiveType.QUICK_FALL, PrimitiveType.SPIN -> {
                val dur = if (e.type == PrimitiveType.SLOW_RISE) 0.30 else 0.15
                val carrier = HapticMapping.sharpnessToCarrierHz(sharpness)
                val start = (e.time * sr).roundToInt()
                val len = (dur * sr).roundToInt()
                for (i in 0 until len) {
                    val idx = start + i
                    if (idx !in buf.indices) continue
                    val x = i.toDouble() / len
                    val amp = when (e.type) {
                        PrimitiveType.QUICK_FALL -> 1.0 - x
                        PrimitiveType.SPIN -> 0.5 + 0.5 * sin(2 * PI * 6 * x)
                        else -> x // rises
                    }
                    val t = i.toDouble() / sr
                    buf[idx] += intensity * amp * sin(2 * PI * carrier * t)
                }
            }
            else -> renderTransientWave(Transient(e.time, intensity, sharpness), buf, sr, null)
        }
    }

    // ----- Discrete commands (Android / event-scheduled hardware) -----------

    override fun scheduleHaptics(pattern: HapticAudioPattern, caps: HapticCapabilities): List<HapticCommand> {
        if (!caps.hasVibrator) return emptyList()
        val out = mutableListOf<HapticCommand>()
        for (track in pattern.tracks) {
            if (track !is HapticTrack || track.muted) continue
            val intensityCurve = track.curves.find(CurveParam.HAPTIC_INTENSITY)
            for (event in track.events) {
                when (event) {
                    is Transient -> out += scheduleTransient(event.time, event.intensity, event.sharpness, caps)
                    is Continuous -> out += scheduleContinuous(event, caps, intensityCurve)
                    is Primitive -> out += schedulePrimitive(event, caps)
                }
            }
        }
        return out.sortedBy { it.atSeconds }
    }

    private fun scheduleTransient(
        time: Seconds,
        intensity: Double,
        sharpness: Double,
        caps: HapticCapabilities,
    ): List<HapticCommand> {
        // Prefer a native primitive (best fidelity); sharpness selects which.
        val prim = if (caps.supportedPrimitives.isNotEmpty())
            HapticMapping.transientToPrimitive(sharpness, caps.supportedPrimitives) else null
        return when {
            prim != null -> listOf(PlayPrimitive(time, prim, intensity.coerceIn(0.0, 1.0).toFloat()))
            caps.hasAmplitudeControl -> listOf(PlayOneShot(time, 20, HapticMapping.intensityToAmplitude255(intensity)))
            else -> listOf(PlayOneShot(time, 20, 255)) // on/off degrade
        }
    }

    private fun scheduleContinuous(
        e: Continuous,
        caps: HapticCapabilities,
        intensityCurve: ParameterCurve?,
    ): List<HapticCommand> {
        val shaper = EnvelopeShaper(e.envelope, e.duration)
        if (!caps.hasAmplitudeControl) {
            // Degrade: a single on/off buzz for the held duration (ignore envelope shape).
            return listOf(PlayOneShot(e.time, (e.duration * 1000).toLong().coerceAtLeast(1), 255))
        }
        // Richest path: wideband/PWLE hardware gets a smooth amplitude+frequency envelope so sharpness
        // becomes a real frequency contour, not just a fixed carrier. Falls back to amplitude waveform.
        if (caps.hasFrequencyControl) {
            return listOf(scheduleEnvelope(e, shaper, intensityCurve))
        }
        // Pre-sample the envelope (and any intensity curve) into amplitude steps for createWaveform.
        val stepMs = 16L
        val steps = max(1, (shaper.totalDuration * 1000 / stepMs).roundToInt())
        val timings = LongArray(steps) { stepMs }
        val amps = IntArray(steps) { i ->
            val local = i * stepMs / 1000.0
            val absT = e.time + local
            val curveMul = intensityCurve?.let { CurveSampler(it).valueAt(absT) } ?: 1.0
            HapticMapping.intensityToAmplitude255(e.intensity * curveMul * shaper.gainAt(local))
        }
        return listOf(PlayWaveform(e.time, timings, amps, repeat = -1))
    }

    /** Sample a Continuous event into an amplitude+frequency envelope for PWLE-capable hardware. */
    private fun scheduleEnvelope(
        e: Continuous,
        shaper: EnvelopeShaper,
        intensityCurve: ParameterCurve?,
    ): PlayEnvelope {
        val stepMs = 16L
        val steps = max(2, (shaper.totalDuration * 1000 / stepMs).roundToInt())
        val freq = HapticMapping.sharpnessToEnvelopeHz(e.sharpness)
        val points = ArrayList<EnvelopePoint>(steps)
        for (i in 0 until steps) {
            val local = i * stepMs / 1000.0
            val absT = e.time + local
            val curveMul = intensityCurve?.let { CurveSampler(it).valueAt(absT) } ?: 1.0
            val amp = (e.intensity * curveMul * shaper.gainAt(local)).coerceIn(0.0, 1.0).toFloat()
            points += EnvelopePoint(timeMs = i * stepMs, amplitude = amp, frequencyHz = freq)
        }
        return PlayEnvelope(e.time, points)
    }

    private fun schedulePrimitive(e: Primitive, caps: HapticCapabilities): List<HapticCommand> {
        if (e.type in caps.supportedPrimitives) {
            return listOf(PlayPrimitive(e.time, e.type, e.scale.coerceIn(0.0, 1.0).toFloat()))
        }
        // No primitive support: synthesize from a transient (§4 degrade rule).
        val (intensity, sharpness) = primitiveToTransient(e.type, e.scale)
        return scheduleTransient(e.time, intensity, sharpness, caps)
    }

    // ----- Coupling helpers -------------------------------------------------

    private fun driveHapticFromAudioInto(src: AudioTrack, buf: DoubleArray, sr: Int, c: Coupling) {
        val audio = renderAudio(HapticAudioPattern(name = "_", tracks = listOf(src)), sr)
        val data = FloatArray(buf.size)
        audio.read(data)
        val follower = EnvelopeFollower(c.params.attackMs, c.params.releaseMs, sr)
        // Spectral brightness -> sharpness: ratio of a one-pole high-passed signal's energy.
        var prev = 0.0
        for (i in buf.indices) {
            val x = data[i].toDouble()
            val env = follower.process(x)
            val sharp = if (c.params.sharpnessFromBrightness) {
                val hp = x - prev
                (kotlin.math.abs(hp) * 4.0).coerceIn(0.0, 1.0)
            } else 0.5
            prev = x
            val carrier = HapticMapping.sharpnessToCarrierHz(sharp)
            val t = i.toDouble() / sr
            buf[i] += c.params.gain * env * sin(2 * PI * carrier * t)
        }
    }

    private fun sonifyHapticInto(src: HapticTrack, mix: DoubleArray, sr: Int, c: Coupling) {
        // Render the haptic track as a waveform, then mix it audibly. The carrier already encodes
        // sharpness, so it reads as a pitched buzz that tracks the haptic envelope.
        val wave = renderHapticWaveform(HapticAudioPattern(name = "_", tracks = listOf(src)), sr)
        val data = FloatArray(mix.size)
        wave.read(data)
        for (i in mix.indices) mix[i] += c.params.gain * data[i]
    }

    // ----- Shared utilities -------------------------------------------------

    private fun sampleCount(pattern: HapticAudioPattern, sampleRate: Int): Int {
        val dur = PatternTiming.durationSeconds(pattern, sampleBank)
        return max(0, kotlin.math.ceil(dur * sampleRate).toInt())
    }

    private fun primitiveToTransient(type: PrimitiveType, scale: Double): Pair<Double, Double> = when (type) {
        PrimitiveType.TICK -> scale to 0.95
        PrimitiveType.LOW_TICK -> scale to 0.6
        PrimitiveType.CLICK -> scale to 0.5
        PrimitiveType.THUD -> scale to 0.15
        PrimitiveType.SPIN -> scale to 0.4
        PrimitiveType.QUICK_RISE -> scale to 0.7
        PrimitiveType.SLOW_RISE -> scale to 0.5
        PrimitiveType.QUICK_FALL -> scale to 0.6
    }

    companion object {
        private fun List<ParameterCurve>.find(param: CurveParam): ParameterCurve? =
            firstOrNull { it.parameter == param }

        private fun trackById(pattern: HapticAudioPattern, id: String): Track? =
            pattern.tracks.firstOrNull { it.id == id }

        private fun readInterpolated(data: FloatArray, pos: Double): Double {
            if (pos < 0.0 || pos >= data.size - 1) return 0.0
            val i = pos.toInt()
            val frac = pos - i
            return data[i] * (1 - frac) + data[i + 1] * frac
        }

        private fun toClampedFloats(buf: DoubleArray): FloatArray =
            FloatArray(buf.size) { buf[it].coerceIn(-1.0, 1.0).toFloat() }
    }
}
