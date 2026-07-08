package dev.hnm.workbench.ui.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Phase 4: the Editor top bar's undo/redo + rename, exercised directly against [EditorState]. */
class EditorStateTest {

    @Test
    fun freshStateHasNothingToUndoOrRedo() {
        val state = EditorState()
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
    }

    @Test
    fun discreteEditIsUndoableAndRedoable() {
        val state = EditorState()
        val before = state.hapticEvents.size
        state.addTransient()
        assertEquals(before + 1, state.hapticEvents.size)
        assertTrue(state.canUndo)

        state.undo()
        assertEquals(before, state.hapticEvents.size)
        assertTrue(state.canRedo)

        state.redo()
        assertEquals(before + 1, state.hapticEvents.size)
        assertFalse(state.canRedo)
    }

    @Test
    fun newEditAfterUndoClearsRedo() {
        val state = EditorState()
        state.addTransient()
        state.undo()
        assertTrue(state.canRedo)

        state.addContinuous()
        assertFalse(state.canRedo, "a fresh edit should discard the old redo branch")
    }

    @Test
    fun slidingCoalescesIntoOneUndoStep() {
        val state = EditorState()
        state.addTransient(intensity = 0.5)
        state.select(state.hapticEvents.lastIndex)
        state.commitEdit() // clear any pending baseline from the add itself (add is discrete, not coalesced)

        // Simulate a slider drag: many onValueChange ticks before the drag ends.
        state.setSelectedIntensity(0.6)
        state.setSelectedIntensity(0.7)
        state.setSelectedIntensity(0.9)
        state.commitEdit() // onValueChangeFinished

        assertEquals(0.9, state.selectedEvent?.let { (it as? dev.hnm.workbench.core.ir.Transient)?.intensity })

        state.undo()
        // One undo should land back before the whole drag, not one tick back.
        val intensityAfterUndo = (state.selectedEvent as? dev.hnm.workbench.core.ir.Transient)?.intensity
        assertEquals(0.5, intensityAfterUndo)
    }

    @Test
    fun undoWithoutExplicitCommitStillCheckpointsThePendingDrag() {
        val state = EditorState()
        state.addTransient(intensity = 0.5)
        state.commitEdit()

        state.setSelectedIntensity(0.6)
        state.setSelectedIntensity(0.8) // no commitEdit() call — as if the drag never finished

        state.undo()
        val intensityAfterUndo = (state.selectedEvent as? dev.hnm.workbench.core.ir.Transient)?.intensity
        assertEquals(0.5, intensityAfterUndo)
    }

    @Test
    fun renamePatternChangesNameAndIsUndoable() {
        val state = EditorState()
        val original = state.pattern.name
        state.renamePattern("My Pattern")
        assertEquals("My Pattern", state.pattern.name)

        state.undo()
        assertEquals(original, state.pattern.name)
    }

    @Test
    fun renameToBlankOrUnchangedNameIsNoOp() {
        val state = EditorState()
        val original = state.pattern.name
        state.renamePattern("   ")
        assertEquals(original, state.pattern.name)
        assertFalse(state.canUndo)

        state.renamePattern(original)
        assertFalse(state.canUndo)
    }

    @Test
    fun undoAndRedoAreNoOpsWhenStacksAreEmpty() {
        val state = EditorState()
        val before = state.pattern
        state.undo()
        assertEquals(before, state.pattern)
        state.redo()
        assertEquals(before, state.pattern)
    }
}
