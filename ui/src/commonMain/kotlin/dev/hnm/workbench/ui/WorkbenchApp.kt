package dev.hnm.workbench.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.ui.components.AssistantPanel
import dev.hnm.workbench.ui.components.BatteryBadge
import dev.hnm.workbench.ui.components.CapabilityPanel
import dev.hnm.workbench.ui.components.EnvelopeEditor
import dev.hnm.workbench.ui.components.ExportPanel
import dev.hnm.workbench.ui.components.GlyphRec
import dev.hnm.workbench.ui.components.GlyphStop
import dev.hnm.workbench.ui.components.InspectorPanel
import dev.hnm.workbench.ui.components.KeypadCell
import dev.hnm.workbench.ui.components.LibraryPanel
import dev.hnm.workbench.ui.components.MaterialPalette
import dev.hnm.workbench.ui.components.MotionPalette
import dev.hnm.workbench.ui.components.NavigatorPanel
import dev.hnm.workbench.ui.components.PalettePanel
import dev.hnm.workbench.ui.components.RecordingDot
import dev.hnm.workbench.ui.components.RhythmCapturePanel
import dev.hnm.workbench.ui.components.ScanlineOverlay
import dev.hnm.workbench.ui.components.SpeakerGrille
import dev.hnm.workbench.ui.components.TexturePalette
import dev.hnm.workbench.ui.components.TimelineView
import dev.hnm.workbench.ui.components.WalkthroughCard
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors
import dev.hnm.workbench.ui.theme.WorkbenchTheme

/**
 * Editor root using recorder2.html's all-dark device language:
 *   • Pitch-black page; device shell is a 160° near-black gradient with 42dp corners
 *   • Screen area #141210 with CRT scanlines for all content
 *   • Chin: dark battery pill + speaker grille
 *   • Transport: a monolithic keypad slab (#050402) of recessed crater keys, hairline seams
 * Responsive: narrow collapses to one scrolling column; wide uses two.
 */
@Composable
fun WorkbenchApp(
    state: EditorState = remember { EditorState() },
    onOpenGallery: (() -> Unit)? = null,
) {
    WorkbenchTheme {
        // Pitch-black page (recorder2 body background:#000)
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
                val narrow = maxWidth < 720.dp
                DeviceShell {
                    if (narrow) NarrowLayout(state, onOpenGallery)
                    else WideLayout(state, onOpenGallery)
                }
            }
        }
    }
}

/** Phone: one scrolling column inside the screen, transport slab pinned in the flow. */
@Composable
private fun NarrowLayout(state: EditorState, onOpenGallery: (() -> Unit)?) {
    val scroll = rememberScrollState()
    ScreenPanel(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusBar(state)
            WalkthroughCard()
            AssistantPanel(state)
            HorizontalDivider(color = WorkbenchColors.Grid)
            TimelineView(state)
            ChinRow()
            KeypadSlab(state, onOpenGallery)
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
}

/** Desktop: two columns inside one dark screen. */
@Composable
private fun WideLayout(state: EditorState, onOpenGallery: (() -> Unit)?) {
    ScreenPanel(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            val leftScroll = rememberScrollState()
            Column(
                Modifier.weight(1.4f).verticalScroll(leftScroll),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatusBar(state)
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
            val rightScroll = rememberScrollState()
            Column(
                Modifier.weight(1f).verticalScroll(rightScroll),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ChinRow()
                KeypadSlab(state, onOpenGallery)
                HorizontalDivider(color = WorkbenchColors.Grid)
                CapabilityPanel(state)
                InspectorPanel(state)
                HorizontalDivider(color = WorkbenchColors.Grid)
                ExportPanel(state)
            }
        }
    }
}

/**
 * Outer device shell (recorder2 .device): 160° near-black gradient, 42dp corners, thin border,
 * deep drop-shadow.
 */
@Composable
private fun DeviceShell(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .shadow(40.dp, RoundedCornerShape(42.dp))
            .clip(RoundedCornerShape(42.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        WorkbenchColors.ShellTop,
                        WorkbenchColors.ShellMid,
                        WorkbenchColors.ShellLo,
                    ),
                )
            )
            .border(1.dp, WorkbenchColors.ShellBorder, RoundedCornerShape(42.dp))
            .padding(14.dp),
    ) {
        content()
    }
}

/** Dark screen surface (recorder2 .screen): #141210, 20dp corners, CRT scanlines on top. */
@Composable
private fun ScreenPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(WorkbenchColors.Screen),
    ) {
        content()
        ScanlineOverlay(Modifier.matchParentSize())
    }
}

/** Status bar (recorder2 .statusbar): red dot + pattern name + counts, all in ink-dim. */
@Composable
private fun StatusBar(state: EditorState) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RecordingDot(active = true, modifier = Modifier.size(8.dp))
        Text(
            state.pattern.name.uppercase(),
            color = WorkbenchColors.InkDim,
            fontSize = 12.sp,
            letterSpacing = 0.12.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${state.hapticEvents.size}H · ${state.audioEvents.size}A",
            color = WorkbenchColors.InkDim,
            fontSize = 12.sp,
        )
    }
}

/** Chin row (recorder2 .chinrow): dark battery pill + speaker grille. */
@Composable
private fun ChinRow() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BatteryBadge(percent = 89)
        SpeakerGrille(Modifier.weight(1f).height(34.dp))
    }
}

/**
 * The transport slab (recorder2 .controls): one rounded #050402 surface with 2dp seams.
 * Layout: [big Play] [big Stop] [stack: Gallery / Replay].
 * Mapping to the workbench: Play → play current; Stop → (reserved); Gallery → open gallery;
 * Replay → play current again.
 */
@Composable
private fun KeypadSlab(state: EditorState, onOpenGallery: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(WorkbenchColors.SlabBg)
                .padding(2.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Big Play key (red glyph, glows red when playable)
                KeypadCell(
                    onClick = { state.playCurrent() },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    domeFraction = 0.80f,
                    glow = if (state.canPlay) WorkbenchColors.Red else Color.Transparent,
                    glyph = { GlyphRec() },
                )
                // Big Stop key
                KeypadCell(
                    onClick = { /* reserved: stop/pause */ },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    domeFraction = 0.80f,
                    glyph = { GlyphStop() },
                )
                // Stack of two small keys
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    KeypadCell(
                        onClick = { onOpenGallery?.invoke() },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        domeFraction = 0.70f,
                        glyph = { Text("≡", color = WorkbenchColors.Icon, fontSize = 16.sp) },
                    )
                    KeypadCell(
                        onClick = { state.playCurrent() },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        domeFraction = 0.70f,
                        glyph = { Text("↻", color = WorkbenchColors.Icon, fontSize = 16.sp) },
                    )
                }
            }
        }
        if (!state.canPlay) {
            Text(
                "▶ Play runs on a device with an actuator",
                color = WorkbenchColors.InkDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
