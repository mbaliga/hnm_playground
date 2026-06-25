package dev.hnm.workbench.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.ui.components.CapabilityPanel
import dev.hnm.workbench.ui.components.EnvelopeEditor
import dev.hnm.workbench.ui.components.ExportPanel
import dev.hnm.workbench.ui.components.InspectorPanel
import dev.hnm.workbench.ui.components.LibraryPanel
import dev.hnm.workbench.ui.components.MaterialPalette
import dev.hnm.workbench.ui.components.MotionPalette
import dev.hnm.workbench.ui.components.NavigatorPanel
import dev.hnm.workbench.ui.components.PalettePanel
import dev.hnm.workbench.ui.components.RhythmCapturePanel
import dev.hnm.workbench.ui.components.TexturePalette
import dev.hnm.workbench.ui.components.TimelineView
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors
import dev.hnm.workbench.ui.theme.WorkbenchTheme

/**
 * Editor root. Left (design) column scrolls — timeline, envelope editor, library, rhythm capture,
 * and all design palettes (motion, texture, material, navigator, primitives). Right (tooling) column
 * holds the target profile picker, inspector (live knobs for haptic + audio events), and the live
 * export preview. All state lives in one [EditorState]; every panel reads and edits the same IR.
 */
@Composable
fun WorkbenchApp(
    state: EditorState = remember { EditorState() },
    onOpenGallery: (() -> Unit)? = null,
) {
    WorkbenchTheme {
        Surface(Modifier.fillMaxSize(), color = WorkbenchColors.Background) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Header(state, onOpenGallery)
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = WorkbenchColors.Grid)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Design column — scrollable; everything the author touches.
                    val designScroll = rememberScrollState()
                    Column(
                        Modifier.weight(1.4f).verticalScroll(designScroll),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        TimelineView(state)
                        EnvelopeEditor(state)
                        HorizontalDivider(color = WorkbenchColors.Grid)
                        LibraryPanel(state)
                        HorizontalDivider(color = WorkbenchColors.Grid)
                        RhythmCapturePanel(state)
                        HorizontalDivider(color = WorkbenchColors.Grid)
                        MotionPalette(state)
                        TexturePalette(state)
                        MaterialPalette(state)
                        NavigatorPanel(state)
                        PalettePanel(state)
                    }
                    // Tooling column — fixed; always visible.
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CapabilityPanel(state)
                        InspectorPanel(state)
                        HorizontalDivider(color = WorkbenchColors.Grid)
                        ExportPanel(state, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: EditorState, onOpenGallery: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("Haptics + Audio Workbench", color = WorkbenchColors.OnSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("design · feel · export", color = WorkbenchColors.Muted, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(horizontalAlignment = Alignment.End) {
                Text(state.pattern.name, color = WorkbenchColors.Primary, fontSize = 16.sp)
                Text("${state.hapticEvents.size} haptic · ${state.audioEvents.size} audio", color = WorkbenchColors.Muted, fontSize = 12.sp)
            }
            // Optional jump to the feel-test gallery (Android only; desktop passes null).
            if (onOpenGallery != null) {
                OutlinedButton(onClick = onOpenGallery, modifier = Modifier.height(44.dp)) {
                    Text("Gallery", fontSize = 13.sp)
                }
            }
            // The Play button only does something on a host with a real actuator (Android); on desktop
            // it stays disabled because there's nothing to feel.
            Button(
                onClick = { state.playCurrent() },
                enabled = state.canPlay,
                colors = ButtonDefaults.buttonColors(containerColor = WorkbenchColors.Primary),
                modifier = Modifier.height(44.dp),
            ) {
                Text(if (state.canPlay) "▶ Play" else "▶ (desktop)", fontSize = 14.sp)
            }
        }
    }
}
