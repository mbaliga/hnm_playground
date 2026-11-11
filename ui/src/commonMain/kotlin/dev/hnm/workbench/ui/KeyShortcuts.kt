package dev.hnm.workbench.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.hnm.workbench.ui.model.EditorState

/** Hardware-keyboard actions. Touch-only hosts never produce these events, so nothing changes for them. */
enum class ShortcutAction { PLAY, DELETE_SELECTED, SAVE_TO_LIBRARY }

/** Pure mapping from a key press to an action; null when the key is not a shortcut. */
fun shortcutFor(key: Key, ctrlOrMeta: Boolean): ShortcutAction? = when {
    key == Key.Spacebar && !ctrlOrMeta -> ShortcutAction.PLAY
    (key == Key.Enter || key == Key.NumPadEnter) && ctrlOrMeta -> ShortcutAction.PLAY
    key == Key.Delete && !ctrlOrMeta -> ShortcutAction.DELETE_SELECTED
    key == Key.S && ctrlOrMeta -> ShortcutAction.SAVE_TO_LIBRARY
    else -> null
}

/** Run [action] against [state]. Play is a no-op when the host has no player. */
fun EditorState.perform(action: ShortcutAction) {
    when (action) {
        ShortcutAction.PLAY -> if (canPlay) playCurrent()
        ShortcutAction.DELETE_SELECTED -> deleteSelected()
        ShortcutAction.SAVE_TO_LIBRARY -> saveToLibrary()
    }
}

/**
 * Handle a key event for the editor; returns true when it was consumed. Only key-down is handled, and a
 * focused text field consumes its own Space/Delete before this sees them.
 */
fun handleShortcut(state: EditorState, event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val action = shortcutFor(event.key, event.isCtrlPressed || event.isMetaPressed) ?: return false
    state.perform(action)
    return true
}
