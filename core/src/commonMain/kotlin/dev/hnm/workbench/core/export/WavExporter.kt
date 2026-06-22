package dev.hnm.workbench.core.export

import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.dsp.SampleBank
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.playback.FloatStream
import dev.hnm.workbench.core.playback.PatternRenderer
import dev.hnm.workbench.core.playback.readAll
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Renders a [FloatStream] to a canonical PCM WAV byte array (§5d). Defaults to 48 kHz; supports 16-
 * and 24-bit. The optional haptic-waveform WAV is directly importable into DualSense pipelines and
 * game-audio middleware.
 */
object WavExporter {

    const val DEFAULT_SAMPLE_RATE = 48_000

    /** Export the audio track(s) of [pattern] to a mono PCM WAV. */
    fun exportAudio(
        pattern: HapticAudioPattern,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        bitDepth: Int = 16,
        sampleBank: SampleBank = SampleBank.EMPTY,
        renderer: PatternRenderer = DefaultPatternRenderer(sampleBank),
    ): ByteArray = fromStream(renderer.renderAudio(pattern, sampleRate), sampleRate, bitDepth)

    /** Export the rendered haptic waveform (voice-coil) to a mono PCM WAV. */
    fun exportHapticWaveform(
        pattern: HapticAudioPattern,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        bitDepth: Int = 16,
        renderer: PatternRenderer = DefaultPatternRenderer(),
    ): ByteArray = fromStream(renderer.renderHapticWaveform(pattern, sampleRate), sampleRate, bitDepth)

    /** Encode a finite [FloatStream] as a mono WAV. */
    fun fromStream(stream: FloatStream, sampleRate: Int, bitDepth: Int): ByteArray =
        encode(stream.readAll(), sampleRate, bitDepth)

    /** Encode mono float samples in [-1, 1] as a WAV byte array. */
    fun encode(samples: FloatArray, sampleRate: Int, bitDepth: Int): ByteArray {
        require(bitDepth == 16 || bitDepth == 24) { "Only 16- or 24-bit PCM supported, got $bitDepth" }
        val channels = 1
        val bytesPerSample = bitDepth / 8
        val dataSize = samples.size * bytesPerSample * channels
        val byteRate = sampleRate * channels * bytesPerSample
        val blockAlign = channels * bytesPerSample

        val out = ByteArrayBuilder(44 + dataSize)
        // RIFF header
        out.ascii("RIFF")
        out.u32le(36 + dataSize)
        out.ascii("WAVE")
        // fmt chunk
        out.ascii("fmt ")
        out.u32le(16)
        out.u16le(1) // PCM
        out.u16le(channels)
        out.u32le(sampleRate)
        out.u32le(byteRate)
        out.u16le(blockAlign)
        out.u16le(bitDepth)
        // data chunk
        out.ascii("data")
        out.u32le(dataSize)
        for (s in samples) {
            val clamped = s.coerceIn(-1f, 1f).toDouble()
            when (bitDepth) {
                16 -> {
                    val v = (clamped * 32767.0).roundToInt().coerceIn(-32768, 32767)
                    out.u16le(v and 0xFFFF)
                }
                24 -> {
                    val v = (clamped * 8_388_607.0).roundToLong().coerceIn(-8_388_608L, 8_388_607L).toInt()
                    out.u24le(v)
                }
            }
        }
        return out.toByteArray()
    }
}

/** Minimal little-endian byte builder so WAV encoding stays in commonMain with no platform deps. */
private class ByteArrayBuilder(capacity: Int) {
    private var buf = ByteArray(capacity)
    private var size = 0

    private fun ensure(extra: Int) {
        if (size + extra > buf.size) {
            var n = buf.size.coerceAtLeast(16)
            while (n < size + extra) n *= 2
            buf = buf.copyOf(n)
        }
    }

    fun byte(v: Int) { ensure(1); buf[size++] = (v and 0xFF).toByte() }
    fun ascii(s: String) { for (c in s) byte(c.code) }
    fun u16le(v: Int) { byte(v); byte(v ushr 8) }
    fun u24le(v: Int) { byte(v); byte(v ushr 8); byte(v ushr 16) }
    fun u32le(v: Int) { byte(v); byte(v ushr 8); byte(v ushr 16); byte(v ushr 24) }

    fun toByteArray(): ByteArray = buf.copyOf(size)
}
