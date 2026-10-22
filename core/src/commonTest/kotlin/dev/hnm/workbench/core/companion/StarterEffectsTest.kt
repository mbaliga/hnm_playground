package dev.hnm.workbench.core.companion

import dev.hnm.workbench.core.ir.*
import dev.hnm.workbench.core.library.*
import kotlin.test.*

class StarterEffectsTest {
    @Test fun everyBundledEffectValidatesAndLibraryRestores() {
        (BuiltInPatterns.ALL + BuiltInPatterns.CHROME).forEach { pattern ->
            PatternSafety.validate(pattern)
            assertEquals(pattern, PatternSerialization.decode(PatternSerialization.encode(pattern)))
        }
        val saved = PatternLibrary.withBuiltIns().toJson()
        val restored = PatternLibrary.fromJson(saved)
        restored.all().forEach(PatternSafety::validate)
        assertEquals(BuiltInPatterns.ALL.size, restored.size)
        // Noise has no pitched frequency; zero is valid for noise, not an oscillator tone.
        assertFails { PatternSafety.validate(BuiltInPatterns.SNAP.copy(tracks=listOf(AudioTrack("a",events=listOf(OscEvent(0.0,.04,Waveform.SINE,0.0,.5)))))) }
    }

    @Test fun startersHavePortableReferencesAndWorkOnTheWrist() {
        assertEquals(6, StarterEffects.ALL.size)
        assertEquals(6, StarterEffects.ALL.map { it.metadata[StarterEffects.ID] }.distinct().size)
        StarterEffects.ALL.forEach { pattern ->
            assertTrue(WristPatterns.canTune(pattern))
            assertTrue(PatternSafety.duration(pattern) <= 1.5)
            val info = assertNotNull(EffectAttribution.from(pattern))
            assertTrue(info.description.isNotBlank() && info.note.isNotBlank())
            assertTrue(info.sources.isNotEmpty())
            assertTrue(info.sources.all { it.url.startsWith("https://") })
            PatternSafety.validate(WristPatterns.tune(pattern,2.0,.8))
        }
        assertTrue(EffectAttribution.from(StarterEffects.UNLOCK)!!.note.contains("not its waveform"))
        assertTrue(EffectAttribution.from(StarterEffects.LOCK)!!.note.contains("does not specify a lock haptic"))
    }

    @Test fun referencesSurvivePersonalizationSavingAndTransfer() {
        val tuned = WristPatterns.tune(StarterEffects.UNLOCK,1.4,.6)
        val saved = PatternLibrary.withBuiltIns().saveUser(tuned,"watch")
        assertNotEquals(StarterEffects.UNLOCK.name,saved.name)
        val received = PatternTransfer.decode(PatternTransfer.encode(saved))
        assertEquals(EffectAttribution.from(StarterEffects.UNLOCK),EffectAttribution.from(received))
        assertEquals(saved,received)
    }

    @Test fun strengthKeepsTheLightThenFirmRelationship() {
        val original = StarterEffects.UNLOCK.tracks.filterIsInstance<HapticTrack>().single().events.filterIsInstance<Primitive>()
        val edited = WristPatterns.tune(StarterEffects.UNLOCK,1.0,.6).tracks.filterIsInstance<HapticTrack>().single().events.filterIsInstance<Primitive>()
        assertEquals(.6,edited.last().scale,1e-9)
        assertEquals(original.first().scale/original.last().scale,edited.first().scale/edited.last().scale,1e-9)
        assertEquals(.8,WristPatterns.peakStrength(StarterEffects.UNLOCK),1e-9)
    }

    @Test fun existingUserEffectWithNewStarterNameIsPreserved() {
        val user = WristPatterns.doublePulse().copy(name=StarterEffects.UNLOCK.name)
        val oldLibrary = PatternLibrary().save(user)
        val loaded = PatternLibrary.fromJson(oldLibrary.toJson())
        assertEquals(user,loaded.get(user.name))
        assertEquals(user,loaded.remove(user.name))
        assertEquals(StarterEffects.UNLOCK,BuiltInPatterns.ALL.first { it.name == user.name })
    }
}
