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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.ui.components.AssistantPanel
import dev.hnm.workbench.ui.components.BatteryBadge
import dev.hnm.workbench.ui.components.CapabilityPanel
import dev.hnm.workbench.ui.components.EnvelopeEditor
import dev.hnm.workbench.ui.components.GlyphRec
import dev.hnm.workbench.ui.components.GlyphStop
import dev.hnm.workbench.ui.components.InspectorPanel
import dev.hnm.workbench.ui.components.KeypadCell
import dev.hnm.workbench.ui.components.LibraryListPanel
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
import dev.hnm.workbench.ui.coachmarks.CoachAnchor
import dev.hnm.workbench.ui.coachmarks.CoachAnchorId
import dev.hnm.workbench.ui.coachmarks.CoachMarkEngine
import dev.hnm.workbench.ui.coachmarks.CoachMarkOverlay
import dev.hnm.workbench.ui.coachmarks.LocalCoachMarksBlocked
import dev.hnm.workbench.ui.coachmarks.onboardingCoachMarkSpecs
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.nav.Screen
import dev.hnm.workbench.ui.nav.Sheet
import dev.hnm.workbench.ui.nav.SheetHost
import dev.hnm.workbench.ui.nav.Tab
import dev.hnm.workbench.ui.nav.WorkbenchNavState
import dev.hnm.workbench.ui.theme.WorkbenchColors
import dev.hnm.workbench.ui.theme.WorkbenchTheme

/**
 * The app's navigation root, using recorder2.html's all-dark device language:
 *   • Pitch-black page; device shell is a 160° near-black gradient with 42dp corners
 *   • Screen area #141210 with CRT scanlines for all content
 *   • Chin: dark battery pill + speaker grille (shown in the full-screen editor's transport)
 *   • Transport: a monolithic keypad slab (#050402) of recessed crater keys
 *
 * Real navigation, not a single static screen: [nav] drives a bottom-tab shell (Library / Make /
 * Device — see [Tab]), a full-screen editor pushed over it ([Screen.Editor]), and modal sheets
 * presentable from anywhere ([Sheet]). Responsive within the editor: narrow collapses to one
 * scrolling column; wide uses two.
 *
 * [coachMarks] drives the beginner-path coachmarks (see `coachmarks/CoachMark.kt`) wired onto the
 * Library tab and the Editor route below. Its queue is re-derived from [state]'s onboarding progress on
 * every recomposition; [LocalCoachMarksBlocked] is provided here from [nav]'s sheet state so an anchor
 * that's still composed but covered by a modal sheet correctly reports itself unreachable.
 *
 * [onShipToClackpad], when non-null, wires up the Clackpad hand-off's "ship" action
 * (`docs/CLACKPAD_CONTRACT.md` §2 delivery a): it receives the already-built `clackpad-haptic` JSON
 * string and is responsible for getting it back to Clackpad (Android's `WorkbenchActivity` does this via
 * `setResult`/`finish()`). Left `null` by every host that doesn't have a Clackpad round trip to honor
 * (desktop, tests) — the Export panel then falls back to its normal format-chip picker even for a
 * pattern that happens to have a [dev.hnm.workbench.ui.model.EditorState.handoffRole] set. [sourceVersion]
 * / [sourceBuild] are this host's own app version/build, stamped into the exported file's `source` block
 * — real values on Android (`dev.hnm.workbench.android.WorkbenchHandoff.hostVersionInfo`), harmless
 * placeholders everywhere else.
 */
@Composable
fun WorkbenchApp(
    state: EditorState = remember { EditorState() },
    onOpenGallery: (() -> Unit)? = null,
    nav: WorkbenchNavState = remember { WorkbenchNavState() },
    coachMarks: CoachMarkEngine = remember { CoachMarkEngine() },
    onShipToClackpad: ((json: String) -> Unit)? = null,
    sourceVersion: String = "dev",
    sourceBuild: String = "dev",
) {
    coachMarks.specs = onboardingCoachMarkSpecs(state.onboarding)

    // Bridges ExportPanel's role-only callback to the host's json-only one: the JSON itself is always
    // built here (via EditorState.exportClackpadJson -> core's ClackpadExporter), never by the host.
    val onShip: ((String) -> Unit)? = onShipToClackpad?.let { ship ->
        { role: String -> ship(state.exportClackpadJson(role, sourceVersion, sourceBuild)) }
    }

    WorkbenchTheme {
        CompositionLocalProvider(LocalCoachMarksBlocked provides (nav.sheet != null)) {
            // Pitch-black page (recorder2 body background:#000)
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
                    val narrow = maxWidth < 720.dp
                    val galleryAction = resolveGalleryAction(nav, onOpenGallery)
                    DeviceShell {
                        ScreenPanel(Modifier.fillMaxSize()) {
                            SheetHost(nav = nav, state = state, onShipToClackpad = onShip) {
                                when (nav.screen) {
                                    is Screen.Editor -> EditorRoute(state, nav, narrow, galleryAction, coachMarks)
                                    null -> TabShell(state, nav, coachMarks)
                                }
                            }
                        }
                    }
                }
                CoachMarkOverlay(coachMarks)
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
    nav: WorkbenchNavState = remember { WorkbenchNavState() },
) {
    var showSplash by remember { mutableStateOf(true) }
    val scene = remember(seed) { dev.hnm.workbench.core.design.SplashMotifs.generate(seed) }
    WorkbenchApp(state, onOpenGallery, nav)
    if (showSplash) {
        SplashScreen(
            scene = scene,
            onStart = { if (state.canPlay) state.player.play(scene.pattern) },
            onFinished = { showSplash = false },
        )
    }
}

/**
 * Resolves the keypad's "Gallery" key: a host (e.g. Android's `WorkbenchActivity`) may pass
 * [onOpenGallery] to jump to a separate native gallery Activity; absent that, it's wired into our own
 * [nav] — back out to the tab shell's Library tab, which is this app's built-in gallery — rather than
 * staying a dead no-op stub. A plain function (not inlined into the composable) so this wiring is
 * directly unit-testable without standing up a Compose scene.
 */
internal fun resolveGalleryAction(nav: WorkbenchNavState, onOpenGallery: (() -> Unit)?): () -> Unit =
    onOpenGallery ?: {
        nav.selectTab(Tab.LIBRARY)
        nav.closeScreen()
    }

// ---------------------------------------------------------------------------------------------
// Tab shell: Library / Make / Device
// ---------------------------------------------------------------------------------------------

/** The home of the app: a persistent header, the active tab's content, and the bottom tab bar. */
@Composable
private fun TabShell(state: EditorState, nav: WorkbenchNavState, coachMarks: CoachMarkEngine) {
    Column(Modifier.fillMaxSize()) {
        TabHeader(state, nav)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (nav.tab) {
                Tab.LIBRARY -> LibraryListPanel(
                    state = state,
                    onOpenEditor = { name -> nav.openEditor(name) },
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                )
                Tab.MAKE -> MakeTab(state, nav, Modifier.fillMaxSize())
                Tab.DEVICE -> DeviceTab(state, Modifier.fillMaxSize())
            }
        }
        BottomTabBar(nav, coachMarks)
    }
}

/** Status row (see [StatusBar]) plus the two globally-reachable sheet launchers: Learn and Settings. */
@Composable
private fun TabHeader(state: EditorState, nav: WorkbenchNavState) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 14.dp, 10.dp, 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusBar(state, Modifier.weight(1f))
        TopBarIcon("?") { nav.openSheet(Sheet.LEARN) }
        TopBarIcon("⚙") { nav.openSheet(Sheet.SETTINGS) }
    }
}

/** The "Make" tab: the assistant-first creation surface — describe it, build it by hand, or capture it. */
@Composable
private fun MakeTab(state: EditorState, nav: WorkbenchNavState, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    Column(
        modifier
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AssistantPanel(state)
        HorizontalDivider(color = WorkbenchColors.Grid)

        // Quick preview + the way into the deep-edit surface.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(state.pattern.name, color = WorkbenchColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    "${state.hapticEvents.size}H · ${state.audioEvents.size}A",
                    color = WorkbenchColors.Muted,
                    fontSize = 11.sp,
                )
            }
            OutlinedButton(onClick = { state.playCurrent() }, enabled = state.canPlay) {
                Text("▶ Preview", fontSize = 12.sp)
            }
            Button(
                onClick = { nav.openEditor(state.pattern.name) },
                colors = ButtonDefaults.buttonColors(containerColor = WorkbenchColors.Red),
            ) {
                Text("Editor ›", fontSize = 12.sp)
            }
        }

        HorizontalDivider(color = WorkbenchColors.Grid)
        MotionPalette(state)
        TexturePalette(state)
        MaterialPalette(state)
        NavigatorPanel(state)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = { nav.openSheet(Sheet.COMBINE) }) {
                Text("Combine patterns…", fontSize = 11.sp)
            }
        }
        PalettePanel(state)
        HorizontalDivider(color = WorkbenchColors.Grid)
        RhythmCapturePanel(state)
    }
}

/** The "Device" tab: pick a target capability profile and preview how the current pattern degrades. */
@Composable
private fun DeviceTab(state: EditorState, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    Column(
        modifier
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Target device", color = WorkbenchColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Preview how the current pattern degrades on a given actuator before you ship it.",
            color = WorkbenchColors.InkDim,
            fontSize = 11.sp,
        )
        CapabilityPanel(state)
        HorizontalDivider(color = WorkbenchColors.Grid)
        Text("Schedule on this target", color = WorkbenchColors.Muted, fontSize = 12.sp)
        Text(
            state.scheduleSummary,
            color = WorkbenchColors.OnSurface,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/**
 * Bottom tab bar (recorder2-flavored: a flat slab, not the keypad's crater keys — that's transport).
 * The Library entry is wrapped in a [CoachAnchor] — this is where the beginner-path "feel one" coachmark
 * points, since it's reachable (composed, unobstructed) any time the tab shell itself is showing,
 * regardless of which of the three tabs is currently selected.
 */
@Composable
private fun BottomTabBar(nav: WorkbenchNavState, coachMarks: CoachMarkEngine) {
    val reachable = !LocalCoachMarksBlocked.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WorkbenchColors.SlabBg)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tab.entries.forEach { tab ->
            val selected = tab == nav.tab
            val tabButton = @Composable {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { nav.selectTab(tab) }
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(tab.glyph, color = if (selected) WorkbenchColors.Red else WorkbenchColors.Icon, fontSize = 17.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(tab.label, color = if (selected) WorkbenchColors.Red else WorkbenchColors.InkDim, fontSize = 10.sp)
                }
            }
            if (tab == Tab.LIBRARY) {
                CoachAnchor(CoachAnchorId.LIBRARY_TAB, coachMarks.registry, reachable = reachable) { tabButton() }
            } else {
                tabButton()
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Editor route: full-screen push (from a library row's chevron, or "Editor" in Make)
// ---------------------------------------------------------------------------------------------

@Composable
private fun EditorRoute(
    state: EditorState,
    nav: WorkbenchNavState,
    narrow: Boolean,
    onOpenGallery: () -> Unit,
    coachMarks: CoachMarkEngine,
) {
    Column(Modifier.fillMaxSize()) {
        EditorTopBar(state, nav, coachMarks)
        HorizontalDivider(color = WorkbenchColors.Grid)
        val onOpenEditor: (String) -> Unit = { name -> nav.openEditor(name) }
        if (narrow) EditorNarrowBody(state, onOpenGallery, onOpenEditor, coachMarks, Modifier.weight(1f))
        else EditorWideBody(state, onOpenGallery, onOpenEditor, coachMarks, Modifier.weight(1f))
    }
}

/**
 * Back chevron + live pattern name/counts + the sheet launchers that only make sense while editing.
 * The Ship icon is wrapped in a [CoachAnchor] — the beginner-path "ship one" coachmark points here, and
 * clears itself the moment the icon is actually tapped ([ClearOn.TARGET_ACTION][dev.hnm.workbench.ui.coachmarks.ClearOn.TARGET_ACTION]).
 */
@Composable
private fun EditorTopBar(state: EditorState, nav: WorkbenchNavState, coachMarks: CoachMarkEngine) {
    val reachable = !LocalCoachMarksBlocked.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "‹",
            color = WorkbenchColors.Red,
            fontSize = 22.sp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { nav.closeScreen() }
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(state.pattern.name.uppercase(), color = WorkbenchColors.Ink, fontSize = 13.sp, letterSpacing = 0.1.sp)
            Text(
                "${state.hapticEvents.size}H · ${state.audioEvents.size}A",
                color = WorkbenchColors.InkDim,
                fontSize = 10.sp,
            )
        }
        TopBarIcon("⇲") { nav.openSheet(Sheet.IMPORT) }
        CoachAnchor(CoachAnchorId.EDITOR_SHIP, coachMarks.registry, reachable = reachable) {
            TopBarIcon("⇱") {
                nav.openSheet(Sheet.SHIP)
                coachMarks.clearByAction(CoachAnchorId.EDITOR_SHIP)
            }
        }
        TopBarIcon("⚙") { nav.openSheet(Sheet.SETTINGS) }
    }
}

@Composable
private fun TopBarIcon(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Text(glyph, color = WorkbenchColors.Icon, fontSize = 15.sp)
    }
}

/**
 * Phone: one scrolling column for the deep-edit surface, transport slab pinned below it. [InspectorPanel]
 * is wrapped in a [CoachAnchor] — the beginner-path "change one" coachmark points here.
 */
@Composable
private fun EditorNarrowBody(
    state: EditorState,
    onOpenGallery: () -> Unit,
    onOpenEditor: (String) -> Unit,
    coachMarks: CoachMarkEngine,
    modifier: Modifier = Modifier,
) {
    val reachable = !LocalCoachMarksBlocked.current
    Column(modifier.fillMaxSize()) {
        val scroll = rememberScrollState()
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TimelineView(state)
            CoachAnchor(CoachAnchorId.EDITOR_INSPECTOR, coachMarks.registry, reachable = reachable, modifier = Modifier.fillMaxWidth()) {
                InspectorPanel(state)
            }
            HorizontalDivider(color = WorkbenchColors.Grid)
            EnvelopeEditor(state)
            HorizontalDivider(color = WorkbenchColors.Grid)
            LibraryPanel(state, onOpenEditor = onOpenEditor)
        }
        HorizontalDivider(color = WorkbenchColors.Grid)
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            ChinRow()
            KeypadSlab(state, onOpenGallery)
        }
    }
}

/**
 * Desktop: two columns for the deep-edit surface, transport slab at the top of the right column.
 * [InspectorPanel] is wrapped in a [CoachAnchor] here too, same anchor id as the narrow layout — only
 * one of the two bodies is ever composed for a given width, so exactly one registration is live.
 */
@Composable
private fun EditorWideBody(
    state: EditorState,
    onOpenGallery: () -> Unit,
    onOpenEditor: (String) -> Unit,
    coachMarks: CoachMarkEngine,
    modifier: Modifier = Modifier,
) {
    val reachable = !LocalCoachMarksBlocked.current
    Row(modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        val leftScroll = rememberScrollState()
        Column(
            Modifier.weight(1.4f).verticalScroll(leftScroll),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TimelineView(state)
            EnvelopeEditor(state)
            HorizontalDivider(color = WorkbenchColors.Grid)
            LibraryPanel(state, onOpenEditor = onOpenEditor)
        }
        val rightScroll = rememberScrollState()
        Column(
            Modifier.weight(1f).verticalScroll(rightScroll),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChinRow()
            KeypadSlab(state, onOpenGallery)
            HorizontalDivider(color = WorkbenchColors.Grid)
            CoachAnchor(CoachAnchorId.EDITOR_INSPECTOR, coachMarks.registry, reachable = reachable, modifier = Modifier.fillMaxWidth()) {
                InspectorPanel(state)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared device chrome (shell, screen, status bar, chin, transport)
// ---------------------------------------------------------------------------------------------

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
private fun StatusBar(state: EditorState, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
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
 * Mapping to the workbench: Play → play current; Stop → (reserved); Gallery → back out to the Library
 * tab (or a host override, e.g. Android's standalone feel-test gallery Activity); Replay → play again.
 */
@Composable
private fun KeypadSlab(state: EditorState, onOpenGallery: () -> Unit) {
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
                        onClick = onOpenGallery,
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
