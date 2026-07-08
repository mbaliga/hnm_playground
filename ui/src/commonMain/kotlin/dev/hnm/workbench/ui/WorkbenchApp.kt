package dev.hnm.workbench.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.hnm.workbench.ui.splash.SplashPreferences
import dev.hnm.workbench.ui.splash.shouldShowSplash
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
import dev.hnm.workbench.ui.components.ImportPanel
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
import dev.hnm.workbench.ui.components.SplashScreen
import dev.hnm.workbench.ui.components.TexturePalette
import dev.hnm.workbench.ui.components.TimelineView
import dev.hnm.workbench.ui.components.WalkthroughCard
import dev.hnm.workbench.core.ir.PatternSerialization
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.HyleRoles
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
    onBack: (() -> Unit)? = null,
) {
    WorkbenchTheme {
        // Pitch-black page (recorder2 body background:#000)
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
                val narrow = maxWidth < 720.dp
                DeviceShell {
                    if (narrow) NarrowLayout(state, onOpenGallery, onBack)
                    else WideLayout(state, onOpenGallery, onBack)
                }
            }
        }
    }
}

/**
 * The app with a procedural splash overlaid on first launch. The splash's visual, sound and haptics all
 * come from one seed-selected [dev.hnm.workbench.core.design.SplashScene]; when it finishes (or is
 * tapped) the workbench is revealed. [WorkbenchApp] itself stays splash-free so headless render tests of
 * the editor are unaffected.
 */
@Composable
fun WorkbenchWithSplash(
    state: EditorState = remember { EditorState() },
    seed: Int = 0,
    onOpenGallery: (() -> Unit)? = null,
    onSelfTest: (() -> Unit)? = null,
    onCaptureDeviceReport: (() -> String)? = null,
    preferences: SplashPreferences = remember { SplashPreferences.inMemory() },
    reducedMotion: Boolean = false,
) {
    var showSplash by remember { mutableStateOf(preferences.shouldShowSplash()) }
    LaunchedEffect(Unit) { preferences.launchCount += 1 }
    val scene = remember(seed) { dev.hnm.workbench.core.design.SplashMotifs.generate(seed) }
    AppShell(
        state = state,
        onOpenGallery = onOpenGallery,
        onSelfTest = onSelfTest,
        onCaptureDeviceReport = onCaptureDeviceReport,
    )
    if (showSplash) {
        SplashScreen(
            scene = scene,
            onStart = { if (state.canPlay) state.player.play(scene.pattern) },
            onFinished = { showSplash = false },
            reducedMotion = reducedMotion,
        )
    }
}

/** Phone: one scrolling column inside the screen, transport slab pinned in the flow. */
@Composable
private fun NarrowLayout(state: EditorState, onOpenGallery: (() -> Unit)?, onBack: (() -> Unit)?) {
    val scroll = rememberScrollState()
    ScreenPanel(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EditorTopBar(state, onBack)
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
            ImportPanel(state)
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
private fun WideLayout(state: EditorState, onOpenGallery: (() -> Unit)?, onBack: (() -> Unit)?) {
    ScreenPanel(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            val leftScroll = rememberScrollState()
            Column(
                Modifier.weight(1.4f).verticalScroll(leftScroll),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EditorTopBar(state, onBack)
                WalkthroughCard()
                AssistantPanel(state)
                HorizontalDivider(color = WorkbenchColors.Grid)
                TimelineView(state)
                EnvelopeEditor(state)
                HorizontalDivider(color = WorkbenchColors.Grid)
                LibraryPanel(state)
                ImportPanel(state)
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

/**
 * Editor top bar (recorder2 .statusbar, extended for Phase 4): back arrow (when hosted inside
 * [dev.hnm.workbench.ui.AppShell]'s Editor route) or the recording dot otherwise, the pattern name
 * (tap to rename), event counts, and undo/redo.
 */
@Composable
private fun EditorTopBar(state: EditorState, onBack: (() -> Unit)?) {
    var renaming by remember { mutableStateOf(false) }
    var editingJson by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            Text(
                "←",
                color = WorkbenchColors.Icon,
                fontSize = 18.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 2.dp),
            )
        } else {
            RecordingDot(active = true, modifier = Modifier.size(8.dp))
        }
        Text(
            state.pattern.name.uppercase(),
            color = WorkbenchColors.InkDim,
            fontSize = 12.sp,
            letterSpacing = 0.12.sp,
            modifier = Modifier.weight(1f).clickable { renaming = true },
        )
        Text(
            "${state.hapticEvents.size}H · ${state.audioEvents.size}A",
            color = WorkbenchColors.InkDim,
            fontSize = 12.sp,
        )
        Text(
            "{ }",
            color = WorkbenchColors.Icon,
            fontSize = 12.sp,
            modifier = Modifier.clickable { editingJson = true },
        )
        Text(
            "↶",
            color = if (state.canUndo) WorkbenchColors.Icon else WorkbenchColors.Muted,
            fontSize = 16.sp,
            modifier = Modifier.clickable(enabled = state.canUndo) { state.undo() },
        )
        Text(
            "↷",
            color = if (state.canRedo) WorkbenchColors.Icon else WorkbenchColors.Muted,
            fontSize = 16.sp,
            modifier = Modifier.clickable(enabled = state.canRedo) { state.redo() },
        )
    }
    if (renaming) {
        RenamePatternDialog(state = state, onDismiss = { renaming = false })
    }
    if (editingJson) {
        EditAsJsonSheet(state = state, onDismiss = { editingJson = false })
    }
}

@Composable
private fun RenamePatternDialog(state: EditorState, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(state.pattern.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename pattern") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { state.renamePattern(text); onDismiss() }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * The "Edit as JSON" sheet (Phase 5, technical workspace): the raw IR, round-tripped through
 * [PatternSerialization] rather than through sliders. Applying invalid JSON shows an inline error
 * and leaves the pattern untouched — it never throws into the UI.
 */
@Composable
private fun EditAsJsonSheet(state: EditorState, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(PatternSerialization.encode(state.pattern)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit as JSON") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                )
                state.jsonEditError?.let {
                    Text(it, color = HyleRoles.Destructive, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (state.applyPatternJson(text)) onDismiss() }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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
                // Big Play key: violet action glyph (GlyphRec), radium "armed/live" glow when playable —
                // the D5 split between primary-action color and playback/live-state color.
                KeypadCell(
                    onClick = { state.playCurrent() },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    domeFraction = 0.80f,
                    glow = if (state.canPlay) HyleRoles.PlaybackGlow else Color.Transparent,
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
