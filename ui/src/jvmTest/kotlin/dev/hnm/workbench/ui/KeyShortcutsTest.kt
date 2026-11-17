package dev.hnm.workbench.ui

import androidx.compose.ui.input.key.Key
import dev.hnm.workbench.core.playback.PatternPlayer
import dev.hnm.workbench.ui.model.EditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeyShortcutsTest {
    @Test
    fun mapping() {
        assertEquals(ShortcutAction.PLAY, shortcutFor(Key.Spacebar, false))
        assertEquals(ShortcutAction.PLAY, shortcutFor(Key.Enter, true))
        assertNull(shortcutFor(Key.Enter, false))
        assertEquals(ShortcutAction.DELETE_SELECTED, shortcutFor(Key.Delete, false))
        assertEquals(ShortcutAction.SAVE_TO_LIBRARY, shortcutFor(Key.S, true))
        assertNull(shortcutFor(Key.S, false))
        assertNull(shortcutFor(Key.A, false))
    }

    @Test
    fun playActionInvokesThePlayerOnlyWhenOneIsWired() {
        var played = 0
        val state = EditorState()
        state.perform(ShortcutAction.PLAY) // PatternPlayer.None: no-op
        assertEquals(0, played)
        state.player = PatternPlayer { played++ }
        state.perform(ShortcutAction.PLAY)
        assertEquals(1, played)
    }

    @Test
    fun deleteRemovesTheSelectedEvent() {
        val state = EditorState()
        val before = state.hapticEvents.size
        state.perform(ShortcutAction.DELETE_SELECTED)
        assertEquals(before - 1, state.hapticEvents.size)
    }
}
