package dev.hnm.workbench.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.ir.PatternSerialization
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.ui.components.GlassSurface
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.HyleRoles
import kotlinx.coroutines.launch

private val EMPTY_STATE_PROMPTS = listOf(
    "a heartbeat that speeds up",
    "rain on a tin roof",
    "a heavy switch",
)

private const val SEARCH_THRESHOLD = 12

/**
 * Feel — the home tab (UX brief §6.1). The library reborn: tap a card to *feel* it (primary affordance,
 * per the brief's "feel before read" thesis), tap the chevron to open it in the Editor, long-press for
 * Open/Duplicate/Export/Delete. "Yours" (saved patterns) sits above "Built-ins" (the reference
 * vocabulary); search appears once the combined list is long enough to need it. Registry entries stay
 * hidden until the remote-registry phase lands, per the brief.
 */
@Composable
fun FeelScreen(
    state: EditorState,
    onOpenEditor: () -> Unit,
    modifier: Modifier = Modifier,
    onSelfTest: (() -> Unit)? = null,
    onJumpToMakeWithPrompt: ((String) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val scope = rememberCoroutineScope()
    var libraryEpoch by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var exportPreview by remember { mutableStateOf<Pair<String, String>?>(null) } // name to JSON

    val builtInNames = remember { BuiltInPatterns.ALL.map { it.name }.toSet() }
    val yours = remember(libraryEpoch) {
        state.library.names.filter { it !in builtInNames }.mapNotNull { state.library.get(it) }
    }
    val builtIns = BuiltInPatterns.ALL
    val totalCount = yours.size + builtIns.size
    val showSearch = totalCount > SEARCH_THRESHOLD

    fun matches(p: HapticAudioPattern) = query.isBlank() || p.name.contains(query, ignoreCase = true)

    fun feel(pattern: HapticAudioPattern) {
        state.load(pattern)
        scope.launch { state.playCurrentLatched() }
    }

    fun open(pattern: HapticAudioPattern) {
        state.load(pattern)
        onOpenEditor()
    }

    fun duplicate(pattern: HapticAudioPattern) {
        var candidate = "${pattern.name} copy"
        var n = 2
        while (state.library.get(candidate) != null) { candidate = "${pattern.name} copy $n"; n++ }
        state.library.save(pattern.copy(name = candidate))
        libraryEpoch++
    }

    LazyColumn(
        modifier = modifier.background(HyleRoles.Background),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Feel", color = HyleRoles.OnSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        if (showSearch) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search patterns", fontSize = 12.sp, color = HyleRoles.Muted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    textStyle = androidx.compose.ui.text.TextStyle(color = HyleRoles.OnSurface, fontSize = 13.sp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = HyleRoles.Surface,
                        unfocusedContainerColor = HyleRoles.Surface,
                        focusedIndicatorColor = HyleRoles.PrimaryAction,
                        unfocusedIndicatorColor = HyleRoles.Grid,
                        cursorColor = HyleRoles.PrimaryAction,
                    ),
                )
            }
        }

        item {
            Text("Yours", color = HyleRoles.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        val filteredYours = yours.filter(::matches)
        if (filteredYours.isEmpty() && query.isBlank()) {
            item { EmptyYoursState(onJumpToMakeWithPrompt) }
        } else {
            items(filteredYours) { pattern ->
                PatternCard(
                    pattern = pattern,
                    deletable = true,
                    onFeel = { feel(pattern) },
                    onOpen = { open(pattern) },
                    onDuplicate = { duplicate(pattern) },
                    onExport = { exportPreview = pattern.name to PatternSerialization.encode(pattern) },
                    onDelete = { pendingDelete = pattern.name },
                )
            }
        }

        item {
            Text(
                "Built-ins",
                color = HyleRoles.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(builtIns.filter(::matches)) { pattern ->
            PatternCard(
                pattern = pattern,
                deletable = false,
                onFeel = { feel(pattern) },
                onOpen = { open(pattern) },
                onDuplicate = { duplicate(pattern) },
                onExport = { exportPreview = pattern.name to PatternSerialization.encode(pattern) },
                onDelete = {},
            )
        }

        item {
            SelfTestRow(onSelfTest = onSelfTest)
        }
    }

    pendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"$name\"?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        state.removeFromLibrary(name)
                        libraryEpoch++
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HyleRoles.Destructive),
                ) { Text("Delete") }
            },
            dismissButton = {
                Button(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    exportPreview?.let { (name, json) ->
        AlertDialog(
            onDismissRequest = { exportPreview = null },
            title = { Text(name) },
            text = {
                Text(
                    json,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = HyleRoles.OnSurface,
                )
            },
            confirmButton = {
                Button(onClick = { exportPreview = null }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun EmptyYoursState(onJumpToMakeWithPrompt: ((String) -> Unit)?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Nothing saved yet — try one of these in Make:",
            color = HyleRoles.Muted,
            fontSize = 12.sp,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(EMPTY_STATE_PROMPTS) { prompt ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(HyleRoles.Surface)
                        .clickable(enabled = onJumpToMakeWithPrompt != null) {
                            onJumpToMakeWithPrompt?.invoke(prompt)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(prompt, color = HyleRoles.PrimaryAction, fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PatternCard(
    pattern: HapticAudioPattern,
    deletable: Boolean,
    onFeel: () -> Unit,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onFeel, onLongClick = { showMenu = true })
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(pattern.name, color = HyleRoles.OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "${pattern.tracks.size} track${if (pattern.tracks.size == 1) "" else "s"}",
                        color = HyleRoles.Muted,
                        fontSize = 11.sp,
                    )
                }
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable { onOpen() }
                        .padding(10.dp),
                ) {
                    Text("›", color = HyleRoles.PrimaryAction, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Open in Editor") }, onClick = { showMenu = false; onOpen() })
                DropdownMenuItem(text = { Text("Duplicate") }, onClick = { showMenu = false; onDuplicate() })
                DropdownMenuItem(text = { Text("Export…") }, onClick = { showMenu = false; onExport() })
                if (deletable) {
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun SelfTestRow(onSelfTest: (() -> Unit)?) {
    Button(
        onClick = { onSelfTest?.invoke() },
        enabled = onSelfTest != null,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = HyleRoles.OnSurface),
    ) {
        Text(
            if (onSelfTest != null) "▶ Vibration self-test" else "▶ Self-test (device only)",
            fontSize = 13.sp,
        )
    }
}
