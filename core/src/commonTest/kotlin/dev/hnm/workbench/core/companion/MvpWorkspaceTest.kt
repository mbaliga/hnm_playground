package dev.hnm.workbench.core.companion

import dev.hnm.workbench.core.ir.*
import dev.hnm.workbench.core.library.*
import kotlin.test.*

class MvpWorkspaceTest {
    @Test fun backupImportsBothLayersOnceWithoutReplacingExistingWork() {
        val source=PatternLibrary.withBuiltIns()
        val saved=source.saveUser(StarterEffects.ALL.first(), "watch")
        val target=PatternLibrary.withBuiltIns()
        val local=target.saveUser(WristPatterns.doublePulse(), "studio")
        val decoded=WorkspaceFiles.decode(source.toJson().encodeToByteArray())
        assertEquals(1,target.importEffects(decoded))
        assertEquals(0,target.importEffects(decoded))
        assertEquals(local,target.get(local.name))
        assertTrue(WristPatterns.hasSound(target.get(saved.name)!!))
        assertNotNull(EffectAttribution.from(target.get(saved.name)!!))
    }
    @Test fun invalidBackupDoesNotPartiallyCommit() {
        val library=PatternLibrary.withBuiltIns()
        val before=library.toJson()
        assertFailsWith<IllegalArgumentException> { library.importEffects(listOf(WristPatterns.doublePulse(),WristPatterns.doublePulse().copy(name=""))) }
        assertEquals(before,library.toJson())
    }
    @Test fun failedDiskWriteDuringImportKeepsVisibleLibrary() {
        val library=PatternLibrary.withBuiltIns()
        val before=library.toJson()
        library.persist={error("disk full")}
        assertFails { library.importEffects(listOf(WristPatterns.doublePulse())) }
        assertEquals(before,library.toJson())
    }
    @Test fun renameIsAtomicAndKeepsDocumentLineage() {
        val library=PatternLibrary.withBuiltIns()
        val saved=library.saveUser(StarterEffects.ALL.first(),"watch")
        val renamed=library.renameUser(saved.name,"My unlock","watch")
        assertNull(library.get(saved.name))
        assertEquals(saved.metadata[PatternTransfer.DOCUMENT],renamed.metadata[PatternTransfer.DOCUMENT])
        assertEquals(saved.metadata[PatternTransfer.REVISION],renamed.metadata[PatternTransfer.PARENT])
        assertTrue(WristPatterns.hasSound(renamed))
        val before=library.toJson()
        assertFails { library.renameUser(renamed.name,BuiltInPatterns.ALL.first().name,"watch") }
        assertEquals(before,library.toJson())
    }
    @Test fun durationIncludesReleaseAndRejectsExcessiveTail() {
        val effect=HapticAudioPattern(name="Tail", tracks=listOf(AudioTrack("a",events=listOf(OscEvent(waveform=Waveform.SINE,frequencyHz=700.0,time=0.0,duration=1.0,envelope=Envelope(release=2.0))))))
        assertEquals(3.0,PatternSafety.duration(effect))
        assertFails { PatternSafety.validate(effect.copy(tracks=listOf(AudioTrack("a",events=listOf(OscEvent(waveform=Waveform.SINE,frequencyHz=700.0,time=29.0,duration=1.0,envelope=Envelope(release=2.0))))))) }
    }
    @Test fun nativeFileRejectsOversizedAndSampleDependencies() {
        assertFails { WorkspaceFiles.decode(ByteArray(WorkspaceFiles.MAX_BACKUP_BYTES+1)) }
        val effect=HapticAudioPattern(name="Missing sample",tracks=listOf(AudioTrack("a",events=listOf(SampleEvent(time=0.0,sampleId="missing")))))
        assertFails { WorkspaceFiles.decode(PatternSerialization.encode(effect).encodeToByteArray()) }
    }
    @Test fun pairedSoundCanBeCreatedAndTunedOnTheWatch() {
        val pulse=WristPatterns.fromTaps(listOf(0,120,310))
        val paired=WristPatterns.addPairedSound(pulse)
        assertFalse(WristPatterns.hasSound(pulse))
        assertTrue(WristPatterns.hasSound(paired))
        val audio=paired.tracks.filterIsInstance<AudioTrack>().single()
        assertEquals(listOf(0.0,.12,.31),audio.events.map { it.time })
        val tuned=WristPatterns.tune(WristPatterns.withSoundLevel(paired,.2),1.5,.7)
        assertEquals(.2,WristPatterns.soundLevel(tuned))
        listOf(0.0,.18,.465).zip(tuned.tracks.filterIsInstance<AudioTrack>().single().events.map { it.time }).forEach { (expected,actual) -> assertEquals(expected,actual,1e-9) }
        assertTrue(WorkspaceFiles.decode(PatternSerialization.encode(tuned).encodeToByteArray()).single()==tuned)
    }

    @Test fun searchContextTravelsWithBothModalitiesAndReferences() {
        val effect=StarterEffects.UNLOCK.copy(metadata=StarterEffects.UNLOCK.metadata + ("hafx.search.text" to "Find this phrase. Find it twice."))
        val saved=PatternLibrary().saveUser(effect,"studio")
        val received=PatternTransfer.decode(PatternTransfer.encode(saved))
        val restored=WorkspaceFiles.decode(PatternSerialization.encode(received).encodeToByteArray()).single()
        val index=SearchCueIndex(restored.metadata.getValue("hafx.search.text"),"find")
        assertEquals(2,index.matches.size)
        assertTrue(index.crossed(0,20))
        assertTrue(WristPatterns.hasSound(restored))
        assertNotNull(EffectAttribution.from(restored))
    }

    @Test fun revisionMetadataCannotPushASavedEffectPastLimits() {
        val library=PatternLibrary()
        val crowded=WristPatterns.doublePulse().copy(metadata=(1..32).associate { "key$it" to "value" })
        assertFails { library.saveUser(crowded) }
        assertEquals(0,library.size)
    }

    @Test fun zeroStrengthNeverProducesAnOnOffFallbackBuzz() {
        val silent=HapticAudioPattern(name="Silent",tracks=listOf(HapticTrack("h",events=listOf(
            Transient(0.0,0.0,.5),Primitive(.1,PrimitiveType.CLICK,0.0),Continuous(time=.2,duration=.1,intensity=0.0,sharpness=.5)))))
        val renderer=dev.hnm.workbench.core.dsp.DefaultPatternRenderer()
        assertFalse(WristPatterns.hasHaptics(silent))
        val caps=dev.hnm.workbench.core.playback.HapticCapabilities.NONE.copy(hasVibrator=true,hasAmplitudeControl=false)
        assertTrue(renderer.scheduleHaptics(silent,caps).isEmpty())
    }

    @Test fun extremePitchAutomationIsRejectedBeforeRendering() {
        val effect=StarterEffects.UNLOCK
        val bad=effect.copy(tracks=effect.tracks.map { if(it is AudioTrack) it.copy(curves=listOf(
            ParameterCurve(CurveParam.AUDIO_PITCH,listOf(ControlPoint(0.0,24000.0))))) else it })
        assertFails { PatternSafety.validate(bad) }
        assertFails { WorkspaceFiles.decode(PatternSerialization.encode(bad).encodeToByteArray()) }
    }

    @Test fun renamingAnEditedSavedEffectKeepsUnsavedTuningAtomically() {
        val library=PatternLibrary()
        val saved=library.saveUser(StarterEffects.UNLOCK,"watch")
        val edited=WristPatterns.tune(saved,1.5,.4)
        library.persist={error("disk full")}
        assertFails { library.renameUser(saved.name,"Personal unlock","watch",edited) }
        assertEquals(saved,library.get(saved.name))
        library.persist=null
        val renamed=library.renameUser(saved.name,"Personal unlock","watch",edited)
        assertEquals(edited.tracks,renamed.tracks)
        assertNull(library.get(saved.name))
        assertEquals(renamed,library.get(renamed.name))
    }

    @Test fun conflictingContentsCannotSilentlyReuseAnExistingRevision() {
        val library=PatternLibrary()
        val saved=library.saveUser(StarterEffects.UNLOCK)
        assertFalse(library.receive(saved))
        assertFails { library.receive(WristPatterns.tune(saved,1.5,.4)) }
        assertEquals(saved,library.get(saved.name))
    }

}
