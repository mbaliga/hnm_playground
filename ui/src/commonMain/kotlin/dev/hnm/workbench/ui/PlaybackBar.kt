package dev.hnm.workbench.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.hnm.workbench.ui.model.EditorState

/** Outside scrolling content: physical output can always be stopped. */
@Composable
fun PlaybackBar(state: EditorState) {
    val playback by state.player.status.collectAsState()
    Surface {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (state.canPlay) "${state.playbackDeviceName} · ${playback.message}" else "Preview only · no playback device", modifier = Modifier.weight(1f))
                Button(onClick = state::stopPlayback, enabled = playback.active) { Text("Stop") }
            }
            state.workspaceMessage?.let { message ->
                Row {
                    Text(message, Modifier.weight(1f))
                    TextButton(onClick = { state.workspaceMessage = null }) { Text("Dismiss") }
                }
            }
        }
    }
}
