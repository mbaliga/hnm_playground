package dev.hnm.workbench.core

import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.export.AhapExporter
import dev.hnm.workbench.core.export.KotlinVibrationEffectExporter
import dev.hnm.workbench.core.export.WavExporter
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.core.playback.readAll
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderAndExportTest {

    private val renderer = DefaultPatternRenderer()

    @Test
    fun renderedAudioHasCorrectLengthAndIsAudible() {
        val sr = 48_000
        val stream = renderer.renderAudio(BuiltInPatterns.CONFIRM, sr)
        val samples = stream.readAll()
        // 0.06s tone + 0.05s release tail = ~0.11s.
        assertTrue(samples.size in (0.10 * sr).toInt()..(0.12 * sr).toInt(), "len=${samples.size}")
        val rms = sqrt(samples.sumOf { (it * it).toDouble() } / samples.size)
        assertTrue(rms > 0.01, "expected audible signal, rms=$rms")
        assertTrue(samples.all { it in -1f..1f }, "samples must be normalized")
    }

    @Test
    fun wavHeaderIsValid() {
        val sr = 48_000
        val wav = WavExporter.exportAudio(BuiltInPatterns.CONFIRM, sampleRate = sr, bitDepth = 16)
        fun ascii(off: Int, len: Int) = wav.copyOfRange(off, off + len).map { it.toInt().toChar() }.joinToString("")
        fun u32(off: Int): Long {
            var v = 0L
            for (i in 0 until 4) v = v or ((wav[off + i].toLong() and 0xFF) shl (8 * i))
            return v
        }
        fun u16(off: Int): Int = (wav[off].toInt() and 0xFF) or ((wav[off + 1].toInt() and 0xFF) shl 8)

        assertEquals("RIFF", ascii(0, 4))
        assertEquals("WAVE", ascii(8, 4))
        assertEquals("fmt ", ascii(12, 4))
        assertEquals(1, u16(20)) // PCM
        assertEquals(1, u16(22)) // mono
        assertEquals(sr.toLong(), u32(24)) // sample rate
        assertEquals(16, u16(34)) // bit depth
        assertEquals("data", ascii(36, 4))
        // RIFF size = file - 8.
        assertEquals((wav.size - 8).toLong(), u32(4))
        // data size matches the declared header.
        assertEquals((wav.size - 44).toLong(), u32(40))
    }

    @Test
    fun wav24BitProducesThreeBytesPerSample() {
        val wav16 = WavExporter.exportAudio(BuiltInPatterns.CONFIRM, bitDepth = 16)
        val wav24 = WavExporter.exportAudio(BuiltInPatterns.CONFIRM, bitDepth = 24)
        val data16 = wav16.size - 44
        val data24 = wav24.size - 44
        assertEquals(data16 / 2, data24 / 3, "same frame count, different byte width")
    }

    @Test
    fun kotlinExporterGeneratesComposition() {
        val code = KotlinVibrationEffectExporter.export(BuiltInPatterns.CONFIRM)
        assertContains(code, "fun confirmEffect(): VibrationEffect")
        assertContains(code, "VibrationEffect.startComposition()")
        assertContains(code, "PRIMITIVE_TICK, 0.8f, 0")
        assertContains(code, "PRIMITIVE_CLICK, 1.0f, 80")
        assertContains(code, ".compose()")
    }

    @Test
    fun ahapExporterMapsTransients() {
        val ahap = AhapExporter.export(BuiltInPatterns.CONFIRM)
        assertContains(ahap, "\"EventType\": \"HapticTransient\"")
        assertContains(ahap, "\"ParameterID\": \"HapticIntensity\"")
        assertContains(ahap, "\"ParameterValue\": 0.9") // first transient sharpness
        assertEquals(0, AhapExporter.decomposedPrimitiveCount(BuiltInPatterns.CONFIRM))
    }
}
