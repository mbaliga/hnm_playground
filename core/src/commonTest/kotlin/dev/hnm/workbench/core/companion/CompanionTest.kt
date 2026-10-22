package dev.hnm.workbench.core.companion

import dev.hnm.workbench.core.ir.*
import dev.hnm.workbench.core.library.*
import dev.hnm.workbench.core.export.KotlinVibrationEffectExporter
import dev.hnm.workbench.core.playback.HapticCapabilities
import kotlin.test.*

class CompanionTest {
    @Test fun savedBuiltInBecomesAnEditableCopyAndPersists() {
        val library = PatternLibrary.withBuiltIns()
        var disk = ""
        library.persist = { disk = it }
        val saved = library.saveUser(BuiltInPatterns.CONFIRM)
        assertNotEquals(BuiltInPatterns.CONFIRM.name, saved.name)
        assertEquals(BuiltInPatterns.CONFIRM, library.get(BuiltInPatterns.CONFIRM.name))
        assertEquals(saved, PatternLibrary.fromJson(disk).get(saved.name))
        val second = library.saveUser(saved.copy(name = "My confirmation"))
        assertEquals(saved.metadata[PatternTransfer.DOCUMENT], second.metadata[PatternTransfer.DOCUMENT])
        assertEquals(saved.metadata[PatternTransfer.REVISION], second.metadata[PatternTransfer.PARENT])
        assertNotEquals(saved.metadata[PatternTransfer.REVISION], second.metadata[PatternTransfer.REVISION])
    }

    @Test fun failedWriteDoesNotPretendToSaveOrDelete() {
        val library = PatternLibrary.withBuiltIns()
        val saved = library.saveUser(WristPatterns.doublePulse())
        val before = library.toJson()
        library.persist = { error("Disk full") }
        assertFails { library.saveUser(saved.copy(name="Changed")) }
        assertEquals(before, library.toJson())
        assertFails { library.remove(saved.name) }
        assertEquals(before, library.toJson())
    }

    @Test fun receivingIsIdempotentAndPreservesConcurrentEdits() {
        val watch = PatternLibrary.withBuiltIns()
        val phone = PatternLibrary.withBuiltIns()
        val initial = watch.saveUser(WristPatterns.doublePulse(), "watch")
        val wire = PatternTransfer.encode(initial)
        val received = PatternTransfer.decode(wire)
        assertTrue(phone.receive(received))
        val count = phone.size
        assertFalse(phone.receive(received))
        assertEquals(count,phone.size)
        val phoneEdit = phone.saveUser(received.copy(tracks = emptyList()), "phone")
        val watchEdit = watch.saveUser(initial.copy(name = "Watch edit"), "watch")
        assertTrue(phone.receive(watchEdit))
        assertEquals(phoneEdit, phone.get(phoneEdit.name))
        assertTrue(watch.receive(phoneEdit))
        assertEquals(watchEdit, watch.get(watchEdit.name))
    }

    @Test fun nameCollisionDoesNotOverwriteAnUnrelatedDocument() {
        val library = PatternLibrary.withBuiltIns()
        val first = library.saveUser(WristPatterns.doublePulse())
        val second = library.saveUser(WristPatterns.doublePulse())
        assertNotEquals(first.name,second.name)
        assertEquals(first,library.get(first.name))
    }

    @Test fun invalidEffectsAreRejectedBeforeRenderOrTransfer() {
        val base = WristPatterns.doublePulse()
        assertFails { PatternSafety.validate(base.copy(version=9)) }
        assertFails { PatternSafety.validate(base.copy(tracks=listOf(HapticTrack("h",events=listOf(Transient(-1.0,.5,.5)))))) }
        assertFails { PatternSafety.validate(base.copy(tracks=listOf(HapticTrack("h",events=listOf(Transient(0.0,Double.NaN,.5)))))) }
        assertFails { PatternSafety.validate(base.copy(tracks=listOf(HapticTrack("h",events=listOf(Continuous(29.0,3.0,.5,.5)))))) }
        assertFails { PatternTransfer.decode(ByteArray(PatternSafety.MAX_BYTES+1)) }
        assertFails { PatternTransfer.encode(base) }
        val sample=PatternTransfer.revise(base.copy(tracks=listOf(AudioTrack("a",events=listOf(SampleEvent(0.0,"missing"))))))
        assertFails { PatternTransfer.encode(sample) }
    }

    @Test fun wristCaptureUsesRelativeMonotonicIntervalsAndHasBounds() {
        val pattern=WristPatterns.fromTaps(listOf(11000,11240,11500))
        val events=(pattern.tracks.single() as HapticTrack).events
        assertEquals(listOf(0.0,.24,.5),events.map { it.time })
        assertFails { WristPatterns.fromTaps(emptyList()) }
        assertFails { WristPatterns.fromTaps(listOf(100L,99L)) }
        assertFails { WristPatterns.fromTaps(listOf(0L,4001L)) }
        assertFails { WristPatterns.fromTaps(List(7){it.toLong()}) }
        val tuned=WristPatterns.tune(pattern,2.0,.3)
        assertEquals(1.0,(tuned.tracks.single() as HapticTrack).events.last().time)
        assertEquals(.5,events.last().time)
    }

    @Test fun searchDetectsSkippedCrossingsInBothDirectionsWithoutRepeatAtRest() {
        val index=SearchCueIndex("one haptic two HAPTIC three", "haptic")
        assertEquals(listOf(4,15),index.matches)
        assertTrue(index.crossed(0,20))
        assertTrue(index.crossed(20,0))
        assertFalse(index.crossed(4,4))
        assertFalse(index.crossed(4,10))
        assertTrue(index.crossed(10,4))
        assertEquals(15,index.next(4))
        assertEquals(4,index.next(15))
        assertEquals(15,index.previous(4))
        assertTrue(SearchCueIndex("abc", "").matches.isEmpty())
    }

    @Test fun exporterFallsBackWhenOnlySomePrimitivesAreSupported() {
        val pattern=HapticAudioPattern(name="Thud",tracks=listOf(HapticTrack("h",events=listOf(Primitive(0.0,PrimitiveType.THUD)))))
        val target=HapticCapabilities.LRA_FULL.copy(supportedPrimitives=setOf(PrimitiveType.CLICK))
        val code=KotlinVibrationEffectExporter.export(pattern,target)
        assertFalse(code.contains("startComposition"))
        assertTrue(code.contains("createWaveform"))
    }
}
