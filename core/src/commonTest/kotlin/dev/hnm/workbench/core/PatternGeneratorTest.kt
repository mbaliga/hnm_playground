package dev.hnm.workbench.core

import dev.hnm.workbench.core.design.GenerationResult
import dev.hnm.workbench.core.design.HybridPatternGenerator
import dev.hnm.workbench.core.design.OnDevicePatternGenerator
import dev.hnm.workbench.core.design.PatternGenerator
import dev.hnm.workbench.core.dsp.PatternTiming
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.library.BuiltInPatterns
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PatternGeneratorTest {

    private val gen = OnDevicePatternGenerator()

    private fun haptics(p: HapticAudioPattern) =
        p.tracks.filterIsInstance<HapticTrack>().first().events

    @Test
    fun emptyPromptGivesGuidance() = runTest {
        val r = gen.generate("", null)
        assertTrue(r.explanation.isNotBlank())
        // Every result is renderable.
        assertTrue(PatternTiming.durationSeconds(r.pattern) >= 0.0)
    }

    @Test
    fun urgentMakesMultipleStrongSharpTransients() = runTest {
        val r = gen.generate("urgent alert", null)
        val events = haptics(r.pattern).filterIsInstance<Transient>()
        assertTrue(events.size >= 3, "urgent should be several taps")
        assertTrue(events.all { it.intensity >= 0.85 }, "urgent should be strong")
        assertTrue(events.all { it.sharpness >= 0.7 }, "urgent should be sharp")
    }

    @Test
    fun gentleTickIsSoftSingleTap() = runTest {
        val r = gen.generate("gentle tick", null)
        val events = haptics(r.pattern)
        // Either routed to a soft built-in or synthesized as a single soft transient.
        assertTrue(events.isNotEmpty())
        val first = events.first()
        if (first is Transient) {
            assertTrue(first.intensity <= 0.6, "gentle should be soft")
        }
    }

    @Test
    fun buzzPromptProducesContinuous() = runTest {
        val r = gen.generate("long soft buzz", null)
        assertTrue(haptics(r.pattern).any { it is Continuous }, "a buzz should be a sustained event")
    }

    @Test
    fun metalRoutesToMaterialStrike() = runTest {
        val r = gen.generate("metal tap", null)
        assertTrue(r.explanation.contains("metal", ignoreCase = true))
        // A material strike has both an audio track (modes) and a haptic track.
        assertTrue(r.pattern.tracks.size >= 2, "modal strike drives both sound and haptics")
    }

    @Test
    fun roughRoutesToTexture() = runTest {
        val r = gen.generate("rough gritty texture", null)
        assertTrue(r.explanation.contains("texture", ignoreCase = true))
        assertTrue(haptics(r.pattern).isNotEmpty())
    }

    @Test
    fun editSofterReducesIntensity() = runTest {
        val base = BuiltInPatterns.CONFIRM
        val baseMax = haptics(base).filterIsInstance<Transient>().maxOf { it.intensity }
        val r = gen.generate("make it softer", base)
        val newMax = haptics(r.pattern).filterIsInstance<Transient>().maxOf { it.intensity }
        assertTrue(newMax < baseMax, "softer must lower intensity ($newMax should be < $baseMax)")
    }

    @Test
    fun editLongerStretchesTiming() = runTest {
        val base = BuiltInPatterns.CONFIRM
        val baseSpan = PatternTiming.durationSeconds(base)
        val r = gen.generate("longer", base)
        assertTrue(PatternTiming.durationSeconds(r.pattern) > baseSpan, "longer must extend the pattern")
    }

    @Test
    fun editKeepsItAnEditNotANewPattern() = runTest {
        // An edit verb with a current pattern should preserve event count (it scales, not regenerates).
        val base = BuiltInPatterns.WARNING
        val r = gen.generate("sharper", base)
        assertEquals(haptics(base).size, haptics(r.pattern).size)
    }

    @Test
    fun allResultsAreRenderableAndExplained() = runTest {
        val prompts = listOf(
            "success", "error", "warning", "notification", "heartbeat", "double tap",
            "metal", "wood", "glass", "rubber", "ceramic",
            "breath", "settle", "erupt", "stir",
            "rough texture", "smooth fine surface",
            "strong sharp tap", "three quick taps", "soft long hum",
            "something completely unrecognizable zxcvbn",
        )
        for (prompt in prompts) {
            val r = gen.generate(prompt, null)
            assertTrue(r.explanation.isNotBlank(), "missing explanation for '$prompt'")
            assertTrue(haptics(r.pattern).isNotEmpty(), "empty haptics for '$prompt'")
            assertTrue(PatternTiming.durationSeconds(r.pattern) > 0.0, "zero-length for '$prompt'")
        }
    }

    @Test
    fun hybridFallsBackToOnDeviceWhenCloudThrows() = runTest {
        val throwingCloud = object : PatternGenerator {
            override suspend fun generate(prompt: String, current: HapticAudioPattern?): GenerationResult =
                throw RuntimeException("network down")
        }
        val hybrid = HybridPatternGenerator(onDevice = gen, cloud = throwingCloud)
        val r = hybrid.generate("urgent alert", null)
        assertEquals("on-device", r.source, "must fall back when cloud fails")
        assertNotNull(r.pattern)
    }

    @Test
    fun hybridPrefersCloudWhenAvailable() = runTest {
        val fakeCloud = object : PatternGenerator {
            override suspend fun generate(prompt: String, current: HapticAudioPattern?) =
                GenerationResult(BuiltInPatterns.TAP, "from cloud", "cloud")
        }
        val hybrid = HybridPatternGenerator(onDevice = gen, cloud = fakeCloud)
        val r = hybrid.generate("anything", null)
        assertEquals("cloud", r.source)
    }
}
