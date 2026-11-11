package dev.hnm.workbench.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.hnm.workbench.backend.desktop.JvmPatternPlayer
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.ui.model.EditorState

/** Desktop entrypoint: `./gradlew :ui:run`. Hosts the shared [WorkbenchApp] in a window. */
fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1180.dp, 820.dp))
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Haptics + Audio Workbench",
    ) {
        // Wire audio playback only when an output line exists; otherwise the player stays None, canPlay
        // stays false and the workbench shows its existing "no playback here" state.
        val state = remember {
            EditorState().apply {
                if (JvmPatternPlayer.audioAvailable()) player = JvmPatternPlayer()
                // Desktops have no haptic actuator; say so rather than imply one.
                hostCapabilities = HapticCapabilities.NONE
            }
        }
        // A different procedural splash motif each launch (seed varies with wall-clock).
        val seed = remember { (System.currentTimeMillis() / 500L).toInt() }
        WorkbenchWithSplash(state, seed = seed)
    }
}
