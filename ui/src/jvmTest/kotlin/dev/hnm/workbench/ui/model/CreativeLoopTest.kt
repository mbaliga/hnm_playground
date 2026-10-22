package dev.hnm.workbench.ui.model

import dev.hnm.workbench.core.companion.WristPatterns
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.core.playback.PatternPlayer
import kotlin.test.*

class CreativeLoopTest {
    @Test fun auditionAndStopDoNotReplaceDraftOrUndo() {
        val state=EditorState()
        state.renamePattern("My draft")
        val draft=state.pattern
        var heard: HapticAudioPattern?=null
        var stopped=false
        state.player=object:PatternPlayer {
            override fun play(pattern:HapticAudioPattern) { heard=pattern }
            override fun stop() { stopped=true }
        }
        state.audition(WristPatterns.doublePulse())
        assertEquals(draft,state.pattern)
        assertEquals("Double pulse",heard?.name)
        state.stopPlayback()
        assertTrue(stopped)
        state.undo()
        assertEquals(BuiltInPatterns.CONFIRM.name,state.pattern.name)
    }
    @Test fun editsUndoAndRedoAllNotifyDraftPersistence() {
        val drafts=mutableListOf<HapticAudioPattern>()
        val state=EditorState(onDraftChanged={drafts.add(it)})
        state.renamePattern("Renamed")
        state.undo()
        state.redo()
        assertEquals(listOf("Renamed",BuiltInPatterns.CONFIRM.name,"Renamed"),drafts.map { it.name })
    }
    @Test fun saveFailureIsVisibleAndDoesNotClaimSuccess() {
        val state=EditorState()
        state.library.persist={error("Disk full")}
        assertNull(state.saveToLibrary())
        assertTrue(state.workspaceMessage!!.contains("Not saved"))
    }
    @Test fun newStartersLoadFromAnOlderSavedLibraryWithoutChangingUserWork() {
        val oldLibrary = dev.hnm.workbench.core.library.PatternLibrary().save(WristPatterns.doublePulse())
        val before = oldLibrary.toJson()
        val state = EditorState(library = oldLibrary)
        val unlock = dev.hnm.workbench.core.library.StarterEffects.UNLOCK
        state.loadFromLibrary(unlock.name)
        assertEquals(unlock, state.pattern)
        assertEquals(before, oldLibrary.toJson())
    }
    @Test fun fileExportsPreserveBothLayersAndDoNotChangeDraft() {
        val state=EditorState()
        state.load(dev.hnm.workbench.core.library.StarterEffects.UNLOCK)
        val before=state.pattern
        var exported:ByteArray?=null
        state.exportFile={_,_,bytes -> exported=bytes}
        state.exportNative()
        assertEquals(before.tracks,dev.hnm.workbench.core.ir.PatternSerialization.decode(exported!!.decodeToString()).tracks)
        state.exportAudio()
        assertEquals("RIFF",exported!!.take(4).toByteArray().decodeToString())
        assertEquals(before,state.pattern)
    }
    @Test fun backupExportIsAReimportableLibrary() {
        val state=EditorState()
        state.saveToLibrary()
        var exported:ByteArray?=null
        state.exportFile={_,_,bytes -> exported=bytes}
        state.backupLibrary()
        val decoded=dev.hnm.workbench.core.companion.WorkspaceFiles.decode(exported!!)
        assertTrue(decoded.any { it.metadata.containsKey(dev.hnm.workbench.core.companion.PatternTransfer.DOCUMENT) })
    }

    @Test fun exportedUnsavedEditsImportAsANewRevisionWithoutChangingTheDraft() {
        val state=EditorState()
        state.load(dev.hnm.workbench.core.library.StarterEffects.UNLOCK)
        val saved=state.saveToLibrary()!!
        val edited=WristPatterns.tune(saved,1.5,.4)
        state.load(edited)
        var bytes:ByteArray?=null
        state.exportFile={_,_,data -> bytes=data}
        state.exportNative()
        val exported=dev.hnm.workbench.core.companion.WorkspaceFiles.decode(bytes!!).single()
        assertNotEquals(saved.metadata[dev.hnm.workbench.core.companion.PatternTransfer.REVISION],exported.metadata[dev.hnm.workbench.core.companion.PatternTransfer.REVISION])
        assertEquals(1,state.library.importEffects(listOf(exported)))
        assertEquals(edited,state.pattern)
        assertEquals(saved,state.library.get(saved.name))
        assertTrue(state.library.all().any { it.tracks==edited.tracks })
    }

}
