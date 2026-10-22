package dev.hnm.workbench.core.companion

import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.ir.*
import dev.hnm.workbench.core.library.StarterEffects
import dev.hnm.workbench.core.playback.readAll
import kotlin.math.abs
import kotlin.test.*

class PairedEffectsTest {
    @Test fun soundAndHapticsShareEveryAuthoredOnset() {
        listOf(StarterEffects.UNLOCK,StarterEffects.LOCK).forEach { pattern ->
            val haptic=pattern.tracks.filterIsInstance<HapticTrack>().flatMap { it.events }.map { it.time }.toSet()
            val sound=pattern.tracks.filterIsInstance<AudioTrack>().flatMap { it.events }.map { it.time }.toSet()
            assertEquals(haptic,sound)
            assertTrue(WristPatterns.hasSound(pattern))
            assertEquals("audio+haptics",pattern.metadata["hafx.design.modalities"])
        }
    }
    @Test fun spacingMovesBothModalitiesButKeepsPitchAndDecay() {
        val original=StarterEffects.UNLOCK
        val tuned=WristPatterns.tune(original,1.7,.5)
        val oldSound=original.tracks.filterIsInstance<AudioTrack>().single().events.filterIsInstance<OscEvent>()
        val newSound=tuned.tracks.filterIsInstance<AudioTrack>().single().events.filterIsInstance<OscEvent>()
        oldSound.zip(newSound).forEach { (a,b) ->
            assertEquals(a.time*1.7,b.time,1e-9)
            assertEquals(a.copy(time=b.time),b)
        }
        assertEquals(tuned.tracks.filterIsInstance<HapticTrack>().single().events.map { it.time }.toSet(),newSound.map { it.time }.toSet())
        assertEquals(original.metadata,tuned.metadata)
    }
    @Test fun audioIsAudibleFiniteUnclippedAndHasARealGapBetweenTaps() {
        val renderer=DefaultPatternRenderer()
        listOf(StarterEffects.UNLOCK,StarterEffects.LOCK).forEach { pattern ->
            val audio=renderer.renderAudio(pattern,48_000).readAll()
            assertTrue(audio.all { it.isFinite() && abs(it)<.5f })
            assertTrue(audio.take(900).any { abs(it)>.01f })
            val second=(pattern.tracks.filterIsInstance<HapticTrack>().single().events[1].time*48_000).toInt()
            assertTrue(audio.drop(second).take(900).any { abs(it)>.01f })
            assertTrue(audio.slice(2_400 until 3_300).all { abs(it)<1e-6f })
            assertTrue(audio.takeLast(100).all { abs(it)<1e-6f })
        }
    }
    @Test fun hapticsOnlyPreviewDoesNotMutateOrDiscardTheAuthoredSound() {
        val original=StarterEffects.UNLOCK
        val preview=WristPatterns.hapticsOnly(original)
        assertFalse(WristPatterns.hasSound(preview))
        assertEquals(original.tracks.filterIsInstance<HapticTrack>(),preview.tracks)
        assertTrue(DefaultPatternRenderer().renderAudio(preview,48_000).readAll().all { it==0f })
        assertTrue(WristPatterns.hasSound(original))
        assertTrue(original.tracks.filterIsInstance<AudioTrack>().isNotEmpty())
    }
    @Test fun sampledAudioCannotBeAccidentallyRetimedOnTheWrist() {
        val sampled=StarterEffects.UNLOCK.copy(tracks=listOf(HapticTrack("h"),AudioTrack("a",events=listOf(SampleEvent(0.0,"sample")))))
        assertFalse(WristPatterns.canTune(sampled))
        assertFails { WristPatterns.tune(sampled,1.5,.5) }
    }
    @Test fun upgradeRefreshesStockHapticOnlyUnlockButKeepsUserCopies() {
        val legacy = StarterEffects.UNLOCK.copy(tracks=StarterEffects.UNLOCK.tracks.filterIsInstance<HapticTrack>())
        val user = PatternTransfer.revise(legacy.copy(name="My quiet unlock"),"watch")
        val json="["+PatternSerialization.encode(legacy)+","+PatternSerialization.encode(user)+"]"
        val library=dev.hnm.workbench.core.library.PatternLibrary.fromJson(json)
        assertEquals(StarterEffects.UNLOCK,library.get(legacy.name))
        assertTrue(WristPatterns.hasSound(library.get(legacy.name)!!))
        assertEquals(user,library.get(user.name))
        assertFalse(WristPatterns.hasSound(library.get(user.name)!!))
        assertNull(library.remove(legacy.name))
        assertEquals(user,library.remove(user.name))
    }
}
