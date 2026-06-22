package dev.hnm.workbench.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.hnm.workbench.ui.model.EditorState

/** Desktop entrypoint: `./gradlew :ui:run`. Hosts the shared [WorkbenchApp] in a window. */
fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1180.dp, 820.dp))
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Haptics + Audio Workbench",
    ) {
        val state = remember { EditorState() }
        WorkbenchApp(state)
    }
}
