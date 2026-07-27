package dev.hnm.workbench.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.hnm.workbench.core.export.ClackpadExporter
import dev.hnm.workbench.core.ir.AudioTrack
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.HapticTrack
import dev.hnm.workbench.core.library.LibraryRepository
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors

/**
 * Library panel: browse, load, star and play saved patterns from inside the editor, without leaving it
 * for the full Library tab. Backed by [EditorState.libraryRepository] (favorites/recents), not the bare
 * [EditorState.library] map — a horizontal strip of compact cards so it doesn't eat vertical space (the
 * library can grow large), but each card carries the same affordances as a full library row: tap plays
 * (and records a recent), the trailing chevron opens the pattern in the editor, the star toggles
 * favorite, and a long-press opens the same options menu the Library tab's rows use.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPanel(state: EditorState, onOpenEditor: (String) -> Unit, modifier: Modifier = Modifier) {
    var epoch by remember { mutableStateOf(0) }
    val repo = state.libraryRepository
    val patterns = remember(epoch) { repo.all() }
    var menuFor by remember { mutableStateOf<String?>(null) }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Library", color = WorkbenchColors.Muted, fontSize = 12.sp)
            OutlinedButton(
                onClick = {
                    state.saveToLibrary()
                    epoch++
                },
                modifier = Modifier.height(28.dp),
            ) {
                Text("Save current", fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        if (patterns.isEmpty()) {
            Text("Library empty — save a pattern above.", color = WorkbenchColors.Muted, fontSize = 11.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(patterns, key = { it.name }) { pattern ->
                    LibraryQuickCard(
                        pattern = pattern,
                        selected = state.pattern.name == pattern.name,
                        favorite = repo.isFavorite(pattern.name),
                        onPlay = {
                            state.playFromLibrary(pattern.name)
                            epoch++
                        },
                        onOpen = {
                            state.loadFromLibrary(pattern.name)
                            onOpenEditor(pattern.name)
                        },
                        onToggleFavorite = {
                            repo.toggleFavorite(pattern.name)
                            epoch++
                        },
                        onLongPress = { menuFor = pattern.name },
                    )
                }
            }
        }
    }

    LibraryOptionsHost(state, repo, menuFor, onDismiss = { menuFor = null }) { epoch++ }
}

/**
 * The Library tab's full row list — one row per saved pattern, "row plays / chevron opens": tapping the
 * row loads it, previews it on the actuator, and records it as a recent (same affordance as the compact
 * [LibraryPanel] strip's cards); tapping the trailing chevron loads it *and* pushes
 * [dev.hnm.workbench.ui.nav.Screen.Editor] via [onOpenEditor] for deep editing; the leading star toggles
 * favorite; a long-press opens an options menu. Above the row list, a Starred and a Recent rail of
 * [FeelCard]s surface the patterns [EditorState.libraryRepository] already tracks — the Library tab's
 * home block. Shares [EditorState.libraryRepository] with [LibraryPanel] — this is just a different
 * presentation for the dedicated tab.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryListPanel(
    state: EditorState,
    onOpenEditor: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var epoch by remember { mutableStateOf(0) }
    val repo = state.libraryRepository
    val favorites = remember(epoch) { repo.favorites() }
    val recents = remember(epoch) { repo.recents() }
    val patterns = remember(epoch) { repo.all() }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()

    Column(modifier.verticalScroll(scroll)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Library", color = WorkbenchColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            OutlinedButton(
                onClick = {
                    state.saveToLibrary()
                    epoch++
                },
                modifier = Modifier.height(30.dp),
            ) {
                Text("Save current", fontSize = 11.sp)
            }
        }

        if (favorites.isNotEmpty()) {
            FeelRail("Starred", favorites, repo, state) { epoch++ }
            Spacer(Modifier.height(14.dp))
        }
        if (recents.isNotEmpty()) {
            FeelRail("Recent", recents, repo, state) { epoch++ }
            Spacer(Modifier.height(14.dp))
        }
        if (favorites.isNotEmpty() || recents.isNotEmpty()) {
            HorizontalDivider(color = WorkbenchColors.Grid)
            Spacer(Modifier.height(10.dp))
        }

        if (patterns.isEmpty()) {
            Text(
                "Library empty — save the pattern you're working on to see it here.",
                color = WorkbenchColors.Muted,
                fontSize = 12.sp,
            )
        } else {
            patterns.forEach { pattern ->
                LibraryRow(
                    pattern = pattern,
                    selected = state.pattern.name == pattern.name,
                    favorite = repo.isFavorite(pattern.name),
                    onPlay = {
                        state.playFromLibrary(pattern.name)
                        epoch++
                    },
                    onOpen = {
                        state.loadFromLibrary(pattern.name)
                        onOpenEditor(pattern.name)
                    },
                    onToggleFavorite = {
                        repo.toggleFavorite(pattern.name)
                        epoch++
                    },
                    onLongPress = { menuFor = pattern.name },
                )
                HorizontalDivider(color = WorkbenchColors.Grid)
            }
        }
    }

    LibraryOptionsHost(state, repo, menuFor, onDismiss = { menuFor = null }) { epoch++ }
}

/** A titled horizontal rail of [FeelCard]s — the Starred/Recent sections of [LibraryListPanel]'s home block. */
@Composable
private fun FeelRail(
    title: String,
    entries: List<HapticAudioPattern>,
    repo: LibraryRepository,
    state: EditorState,
    onChanged: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(title.uppercase(), color = WorkbenchColors.Muted, fontSize = 11.sp, letterSpacing = 0.08.sp)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(entries, key = { it.name }) { pattern ->
                FeelCard(
                    pattern = pattern,
                    favorite = repo.isFavorite(pattern.name),
                    onTap = {
                        state.playFromLibrary(pattern.name)
                        onChanged()
                    },
                    onToggleFavorite = {
                        repo.toggleFavorite(pattern.name)
                        onChanged()
                    },
                )
            }
        }
    }
}

/** Resolves [menuFor] to a live pattern (if it still exists) and hosts the shared options popup. */
@Composable
private fun LibraryOptionsHost(
    state: EditorState,
    repo: LibraryRepository,
    menuFor: String?,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    if (menuFor == null) return
    val pattern = repo.get(menuFor)
    if (pattern == null) {
        onDismiss()
        return
    }
    LibraryOptionsMenu(
        state = state,
        pattern = pattern,
        favorite = repo.isFavorite(menuFor),
        onToggleFavorite = {
            repo.toggleFavorite(menuFor)
            onChanged()
        },
        onRemoved = {
            state.removeFromLibrary(menuFor)
            onChanged()
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun LibraryRow(
    pattern: HapticAudioPattern,
    selected: Boolean,
    favorite: Boolean,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onLongPress: () -> Unit,
) {
    val hapticCount = pattern.tracks.filterIsInstance<HapticTrack>().sumOf { it.events.size }
    val audioCount = pattern.tracks.filterIsInstance<AudioTrack>().sumOf { it.events.size }
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onPlay, onLongClick = onLongPress)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (favorite) "★" else "☆",
            color = if (favorite) WorkbenchColors.Red else WorkbenchColors.InkDim,
            fontSize = 16.sp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onToggleFavorite)
                .padding(6.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                pattern.name,
                color = if (selected) WorkbenchColors.Red else WorkbenchColors.OnSurface,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                "$hapticCount haptic · $audioCount audio — tap to preview, hold for options",
                color = WorkbenchColors.Muted,
                fontSize = 11.sp,
            )
        }
        Box(
            Modifier
                .clip(CircleShape)
                .clickable(onClick = onOpen)
                .padding(8.dp),
        ) {
            Text("›", color = WorkbenchColors.Muted, fontSize = 18.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryQuickCard(
    pattern: HapticAudioPattern,
    selected: Boolean,
    favorite: Boolean,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onLongPress: () -> Unit,
) {
    val bg = if (selected) WorkbenchColors.Primary.copy(alpha = 0.18f) else WorkbenchColors.Surface
    val border = if (selected) WorkbenchColors.Primary else WorkbenchColors.Grid
    Column(
        modifier = Modifier
            .width(128.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onPlay, onLongClick = onLongPress)
            .padding(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                pattern.name,
                color = if (selected) WorkbenchColors.Primary else WorkbenchColors.OnSurface,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (favorite) "★" else "☆",
                color = if (favorite) WorkbenchColors.Red else WorkbenchColors.Muted,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onToggleFavorite),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "open in editor ›",
            color = WorkbenchColors.Muted,
            fontSize = 10.sp,
            modifier = Modifier.clickable(onClick = onOpen),
        )
    }
}

/**
 * Long-press options menu shared by [LibraryPanel]'s cards and [LibraryListPanel]'s rows. Real actions
 * only call into functionality that actually exists: favorite toggling and removal go through
 * [EditorState]/[LibraryRepository] exactly like the row's own star/chevron; "Send to Clackpad" calls the
 * real [ClackpadExporter] (core/.../export/ClackpadExporter.kt) and shows the JSON it produced. "Export
 * WAV" has no backing implementation yet — writing a file needs a platform-specific API this
 * `ui/commonMain` layer doesn't have wired in, so it's left as a clearly-labeled TODO rather than a fake
 * success message.
 */
@Composable
private fun LibraryOptionsMenu(
    state: EditorState,
    pattern: HapticAudioPattern,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onRemoved: () -> Unit,
    onDismiss: () -> Unit,
) {
    var clackpadPreview by remember(pattern.name) { mutableStateOf<String?>(null) }
    var wavStubMessage by remember(pattern.name) { mutableStateOf<String?>(null) }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .widthIn(min = 220.dp, max = 300.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(WorkbenchColors.Screen)
                .border(1.dp, WorkbenchColors.ShellBorder, RoundedCornerShape(14.dp))
                .padding(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    pattern.name,
                    color = WorkbenchColors.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Close",
                    color = WorkbenchColors.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(10.dp))

            LibraryOptionRow(if (favorite) "★ Unstar" else "☆ Star") { onToggleFavorite() }

            LibraryOptionRow("⇱ Send to Clackpad (role: action)") {
                clackpadPreview = ClackpadExporter.export(
                    pattern = pattern,
                    role = "action",
                    sourceVersion = WORKBENCH_SOURCE_VERSION,
                    sourceBuild = WORKBENCH_SOURCE_BUILD,
                )
                // A real, working export (unlike "Export WAV…" below) — counts as the beginner ladder's
                // "ship one" rung same as the Ship sheet's explicit confirm.
                state.markShipped()
            }
            clackpadPreview?.let { json ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp)
                        .padding(top = 4.dp, bottom = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(WorkbenchColors.SurfaceVariant)
                        .padding(8.dp),
                ) {
                    Text(
                        json,
                        color = WorkbenchColors.Muted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // TODO(workbench): no cross-platform file-write API is wired into ui/commonMain yet — once a
            // per-host file picker/writer lands, wire this to WavExporter.exportAudio(pattern) for real
            // instead of just reporting that it isn't implemented.
            LibraryOptionRow("⇩ Export WAV…") {
                wavStubMessage = "Not implemented yet — WAV export needs a platform file-writer this build doesn't have wired in."
            }
            wavStubMessage?.let { msg ->
                Text(msg, color = WorkbenchColors.Muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 4.dp))
            }

            LibraryOptionRow("⌫ Remove from library", destructive = true) { onRemoved() }
        }
    }
}

@Composable
private fun LibraryOptionRow(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (destructive) WorkbenchColors.Red else WorkbenchColors.OnSurface,
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
    )
}

/**
 * Mirrors the desktop packaging version (`ui/build.gradle.kts`'s `packageVersion`) until the app grows a
 * real per-platform BuildConfig; only used for the Clackpad export's provenance `source` block.
 */
private const val WORKBENCH_SOURCE_VERSION = "1.0.0"
private const val WORKBENCH_SOURCE_BUILD = "dev"
