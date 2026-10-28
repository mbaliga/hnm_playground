package dev.hnm.workbench.core

import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.ir.AudioTrack
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.ir.OscEvent
import dev.hnm.workbench.core.ir.PatternSerialization
import dev.hnm.workbench.core.ir.Transient
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The HAfx intro score is authored outside Kotlin (the web player's score.mjs) and shipped as an
 * Android asset. This pins that the JSON stays inside the native IR contract: it decodes, round-trips,
 * fits its 10 s slot, and renders to audio that neither clips nor goes silent.
 */
class IntroScoreTest {
    private val json = File("../androidApp/src/main/assets/intro/intro.hafx.json").readText()
    private val pattern = PatternSerialization.decode(json)

    @Test fun decodesAndRoundTrips() {
        assertEquals("HAfx · Intro", pattern.name)
        assertEquals(pattern, PatternSerialization.decode(PatternSerialization.encode(pattern)))
    }

    @Test fun fitsTheTenSecondSlot() {
        val ends = pattern.tracks.flatMap { t ->
            when (t) {
                is HapticTrack -> t.events.map { e -> if (e is Continuous) e.time + e.duration + e.envelope.release else e.time + 0.03 }
                is AudioTrack -> t.events.map { e -> if (e is OscEvent) e.time + e.duration + e.envelope.release else e.time }
                else -> emptyList()
            }
        }
        assertTrue(ends.max() <= 10.0, "score runs past 10 s: ${ends.max()}")
        val transients = pattern.tracks.filterIsInstance<HapticTrack>().flatMap { it.events }.filterIsInstance<Transient>()
        assertTrue(transients.any { it.intensity == 1.0 && abs(it.time - 5.8) < 1e-9 }, "impact hit missing")
    }

    @Test fun rendersWithoutClipping() {
        val pcm = DefaultPatternRenderer().renderAudio(pattern, 48_000).readAll()
        // renderAudio clamps to ±1, so clipping shows up as samples pinned at full scale.
        val peak = pcm.maxOf { abs(it) }
        val pinned = pcm.count { abs(it) >= 0.999f }
        assertTrue(peak > 0.2f, "intro audio is near-silent (peak $peak)")
        assertEquals(0, pinned, "intro audio clips on $pinned samples")
    }
}
