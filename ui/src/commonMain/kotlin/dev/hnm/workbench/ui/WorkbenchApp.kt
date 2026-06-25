package dev.hnm.workbench.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import dev.hnm.workbench.ui.components.AssistantPanel
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
import dev.hnm.workbench.ui.components.WalkthroughCard
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors
import dev.hnm.workbench.ui.theme.WorkbenchTheme

/**
 * Editor root. Responsive: on a wide screen (desktop) it's a two-column layout (design | tooling); on
 * a narrow screen (phone) everything stacks into one scrolling column so nothing gets crushed. The
 * Assistant (describe-a-feel AI) and a How-it-works walkthrough sit at the top so the tool is usable
 * without knowing the IR. All state lives in one [EditorState].
 */
@Composable
fun WorkbenchApp(
    state: EditorState = remember { EditorState() },
    onOpenGallery: (() -> Unit)? = null,
) {
    WorkbenchTheme {
        Surface(Modifier.fillMaxSize(), color = WorkbenchColors.Background) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(14.dp)) {
                val narrow = maxWidth < 720.dp
                if (narrow) NarrowLayout(state, onOpenGallery) else WideLayout(state, onOpenGallery)
            }
        }
    }
}

/** Phone: a single scrolling column, assistant-first. */
@Composable
private fun NarrowLayout(state: EditorState, onOpenGallery: (() -> Unit)?) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header(state, onOpenGallery)
        WalkthroughCard()
        AssistantPanel(state)
        HorizontalDivider(color = WorkbenchColors.Grid)
        TimelineView(state)
        InspectorPanel(state)
        HorizontalDivider(color = WorkbenchColors.Grid)
        CapabilityPanel(state)
        EnvelopeEditor(state)
        LibraryPanel(state)
        RhythmCapturePanel(state)
        HorizontalDivider(color = WorkbenchColors.Grid)
        MotionPalette(state)
        TexturePalette(state)
        MaterialPalette(state)
        NavigatorPanel(state)
        PalettePanel(state)
        HorizontalDivider(color = WorkbenchColors.Grid)
        ExportPanel(state)
    }
}

/** Desktop: two columns — design on the left, tooling on the right. */
@Composable
private fun WideLayout(state: EditorState, onOpenGallery: (() -> Unit)?) {
    Column(Modifier.fillMaxSize()) {
        Header(state, onOpenGallery)
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = WorkbenchColors.Grid)
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val designScroll = rememberScrollState()
            Column(
                Modifier.weight(1.4f).verticalScroll(designScroll),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                WalkthroughCard()
                AssistantPanel(state)
                HorizontalDivider(color = WorkbenchColors.Grid)
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
            val toolScroll = rememberScrollState()
            Column(
                Modifier.weight(1f).verticalScroll(toolScroll),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CapabilityPanel(state)
                InspectorPanel(state)
                HorizontalDivider(color = WorkbenchColors.Grid)
                ExportPanel(state)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(state: EditorState, onOpenGallery: (() -> Unit)?) {
    // FlowRow so the title block and the action buttons wrap onto a second line on a narrow screen
    // instead of overlapping (the bug on phones).
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Haptics + Audio Workbench", color = WorkbenchColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${state.pattern.name} · ${state.hapticEvents.size} haptic · ${state.audioEvents.size} audio",
                color = WorkbenchColors.Muted,
                fontSize = 12.sp,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onOpenGallery != null) {
                OutlinedButton(onClick = onOpenGallery) { Text("Gallery", fontSize = 13.sp) }
            }
            Button(
                onClick = { state.playCurrent() },
                enabled = state.canPlay,
                colors = ButtonDefaults.buttonColors(containerColor = WorkbenchColors.Primary),
            ) {
                Text(if (state.canPlay) "▶ Play" else "▶ (desktop)", fontSize = 14.sp)
            }
        }
    }
}
