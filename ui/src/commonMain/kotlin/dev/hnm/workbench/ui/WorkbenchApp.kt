package dev.hnm.workbench.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.ui.components.CapabilityPanel
import dev.hnm.workbench.ui.components.EnvelopeEditor
import dev.hnm.workbench.ui.components.ExportPanel
import dev.hnm.workbench.ui.components.InspectorPanel
import dev.hnm.workbench.ui.components.MotionPalette
import dev.hnm.workbench.ui.components.NavigatorPanel
import dev.hnm.workbench.ui.components.PalettePanel
import dev.hnm.workbench.ui.components.TexturePalette
import dev.hnm.workbench.ui.components.TimelineView
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors
import dev.hnm.workbench.ui.theme.WorkbenchTheme

/**
 * The editor root (M4). Left: timeline + envelope + palette (design & feel). Right: target profile,
 * inspector (live knobs), and the live export preview (the dev-tool payoff). All state lives in one
 * [EditorState]; every panel reads and edits the same immutable IR.
 */
@Composable
fun WorkbenchApp(state: EditorState = remember { EditorState() }) {
    WorkbenchTheme {
        Surface(Modifier.fillMaxSize(), color = WorkbenchColors.Background) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Header(state)
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = WorkbenchColors.Grid)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Design column.
                    Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TimelineView(state)
                        EnvelopeEditor(state)
                        MotionPalette(state)
                        TexturePalette(state)
                        NavigatorPanel(state)
                        PalettePanel(state)
                    }
                    // Tooling column.
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
private fun Header(state: EditorState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text("Haptics + Audio Workbench", color = WorkbenchColors.OnSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("design · feel · export", color = WorkbenchColors.Muted, fontSize = 12.sp)
        }
        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            Text(state.pattern.name, color = WorkbenchColors.Primary, fontSize = 16.sp)
            Text("${state.hapticEvents.size} haptic events", color = WorkbenchColors.Muted, fontSize = 12.sp)
        }
    }
}
