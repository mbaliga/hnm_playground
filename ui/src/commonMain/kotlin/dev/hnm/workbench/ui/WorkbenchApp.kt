package dev.hnm.workbench.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.ui.components.AssistantPanel
import dev.hnm.workbench.ui.components.BatteryBadge
import dev.hnm.workbench.ui.components.CapabilityPanel
import dev.hnm.workbench.ui.components.DomeButton
import dev.hnm.workbench.ui.components.EnvelopeEditor
import dev.hnm.workbench.ui.components.ExportPanel
import dev.hnm.workbench.ui.components.InspectorPanel
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
 * Editor root using the recorder's device-shell design language:
 *   • Dark "screen" area for content / waveforms / assistant
 *   • Light "housing" area for controls (dome buttons, grille, battery)
 *   • CRT scanlines + top sheen over every dark panel
 * Responsive: narrow (phone) collapses to a single scrolling column; wide (desktop) uses two columns.
 */
@Composable
fun WorkbenchApp(
    state: EditorState = remember { EditorState() },
    onOpenGallery: (() -> Unit)? = null,
) {
    WorkbenchTheme {
        // Page background — warm gray radial gradient as in the HTML body
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFD7D6D3), Color(0xFFBDBCB9), Color(0xFFAEADA9)),
                        radius = Float.POSITIVE_INFINITY,
                    )
                )
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
                val narrow = maxWidth < 720.dp
                if (narrow) NarrowLayout(state, onOpenGallery) else WideLayout(state, onOpenGallery)
            }
        }
    }
}

/** Phone: one scrolling column — screen panels first, housing controls at bottom. */
@Composable
private fun NarrowLayout(state: EditorState, onOpenGallery: (() -> Unit)?) {
    val scroll = rememberScrollState()
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        DeviceShell {
            // Screen area — dark, scanlines
            ScreenPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    StatusBar(state)
                    WalkthroughCard()
                    AssistantPanel(state)
                    HorizontalDivider(color = WorkbenchColors.Grid)
                    TimelineView(state)
                    InspectorPanel(state)
                }
            }
            // Housing area — light, dome buttons
            HousingPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ChinRow(state)
                    ControlRow(state, onOpenGallery)
                    HorizontalDivider(color = WorkbenchColors.HousingEdge)
                    CapabilityPanel(state)
                    EnvelopeEditor(state)
                    LibraryPanel(state)
                    RhythmCapturePanel(state)
                    HorizontalDivider(color = WorkbenchColors.HousingEdge)
                    MotionPalette(state)
                    TexturePalette(state)
                    MaterialPalette(state)
                    NavigatorPanel(state)
                    PalettePanel(state)
                    HorizontalDivider(color = WorkbenchColors.HousingEdge)
                    ExportPanel(state)
                }
            }
        }
    }
}

/** Desktop: two columns inside the device shell. */
@Composable
private fun WideLayout(state: EditorState, onOpenGallery: (() -> Unit)?) {
    DeviceShell {
        Row(Modifier.fillMaxSize()) {
            // Left: dark screen column
            ScreenPanel(Modifier.weight(1.4f)) {
                val leftScroll = rememberScrollState()
                Column(
                    Modifier.verticalScroll(leftScroll).padding(20.dp),
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
            }
            // Right: light housing column
            HousingPanel(Modifier.weight(1f)) {
                val rightScroll = rememberScrollState()
                Column(
                    Modifier.verticalScroll(rightScroll).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ChinRow(state)
                    ControlRow(state, onOpenGallery)
                    HorizontalDivider(color = WorkbenchColors.HousingEdge)
                    CapabilityPanel(state)
                    InspectorPanel(state)
                    HorizontalDivider(color = WorkbenchColors.HousingEdge)
                    ExportPanel(state)
                }
            }
        }
    }
}

/**
 * Outer device shell: matches the HTML .device rule.
 * border-radius:42px, light gray gradient, deep drop-shadow.
 */
@Composable
private fun DeviceShell(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .shadow(30.dp, RoundedCornerShape(42.dp))
            .clip(RoundedCornerShape(42.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFF1F0ED), WorkbenchColors.Frame, Color(0xFFE7E6E2)),
                )
            )
    ) {
        content()
    }
}

/**
 * Dark screen panel: matches the HTML .screen.
 * background:#0b0b0b, box-shadow inset, CRT scanlines overlay.
 */
@Composable
private fun ScreenPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .background(WorkbenchColors.Screen)
    ) {
        content()
        // Scanlines sit on top of all content, pointer-events: none
        ScanlineOverlay(Modifier.matchParentSize())
    }
}

/**
 * Light housing panel for controls: matches HTML .controls / key area.
 * background: housing gradient
 */
@Composable
private fun HousingPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(WorkbenchColors.Housing, Color(0xFFE0DFDB)),
                )
            )
    ) {
        content()
    }
}

/**
 * Status bar inside the screen: clock · red dot + label · play/wifi icons.
 * Matches the HTML .statusbar layout exactly.
 */
@Composable
private fun StatusBar(state: EditorState) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Pattern name as the "recording" label
        RecordingDot(active = true, modifier = Modifier.size(8.dp))
        Text(
            state.pattern.name.uppercase(),
            color = WorkbenchColors.InkDim,
            fontSize = 12.sp,
            letterSpacing = 0.12.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${state.hapticEvents.size}h · ${state.audioEvents.size}a",
            color = WorkbenchColors.InkDim,
            fontSize = 12.sp,
        )
    }
}

/**
 * Chin row: battery badge + speaker grille.
 * Matches HTML .chinrow rule.
 */
@Composable
private fun ChinRow(state: EditorState) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BatteryBadge(percent = 89)
        SpeakerGrille(
            modifier = Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(8.dp)),
        )
    }
}

/**
 * Control row: dome buttons matching the HTML .controls layout.
 * Large dome = Play (red glow when can play), smaller domes = Gallery / (future).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlRow(state: EditorState, onOpenGallery: (() -> Unit)?) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Big play dome
        DomeButton(
            onClick = { state.playCurrent() },
            modifier = Modifier.weight(1f).height(80.dp),
            glowColor = if (state.canPlay) WorkbenchColors.Red else Color.Transparent,
        ) {
            // Red circle glyph (like the record button) or a play triangle
            Box(
                Modifier
                    .size(22.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (state.canPlay) WorkbenchColors.Red else WorkbenchColors.Icon),
            )
        }

        // Big stop / the pattern name dome
        DomeButton(
            onClick = { /* future: stop / pause */ },
            modifier = Modifier.weight(1f).height(80.dp),
        ) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(WorkbenchColors.Icon),
            )
        }

        // Small button column: gallery + (placeholder)
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (onOpenGallery != null) {
                DomeButton(
                    onClick = onOpenGallery,
                    modifier = Modifier.fillMaxWidth().height(33.dp),
                ) {
                    Text("≡", color = WorkbenchColors.Icon, fontSize = 18.sp)
                }
            }
            DomeButton(
                onClick = { state.playCurrent() },
                modifier = Modifier.fillMaxWidth().height(33.dp),
            ) {
                Text("▶", color = WorkbenchColors.Icon, fontSize = 14.sp)
            }
        }
    }

    // Caption below buttons — matching the "▶ (desktop)" hint
    if (!state.canPlay) {
        Text(
            "▶ Play available on device",
            color = WorkbenchColors.Icon,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
