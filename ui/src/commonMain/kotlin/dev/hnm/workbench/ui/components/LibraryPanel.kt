package dev.hnm.workbench.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors

/**
 * Library panel: browse and load saved patterns; save the current pattern into the library.
 * Uses a horizontal chip row so it doesn't eat vertical space — the library can grow large.
 * Built-in patterns pre-populate the library; user patterns are added by name.
 */
@Composable
fun LibraryPanel(state: EditorState, modifier: Modifier = Modifier) {
    // Track library snapshot so the row re-reads when the user saves.
    var libraryEpoch by remember { mutableStateOf(0) }
    val names = remember(libraryEpoch) { state.library.names }

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
                    libraryEpoch++
                },
                modifier = Modifier.height(28.dp),
            ) {
                Text("Save current", fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        if (names.isEmpty()) {
            Text("Library empty — save a pattern above.", color = WorkbenchColors.Muted, fontSize = 11.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(names) { name ->
                    PatternChip(
                        name = name,
                        selected = state.pattern.name == name,
                        onClick = { state.loadFromLibrary(name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PatternChip(name: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) WorkbenchColors.Primary.copy(alpha = 0.25f) else WorkbenchColors.Surface
    val border = if (selected) WorkbenchColors.Primary else WorkbenchColors.Grid
    Box(
        modifier = Modifier
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name,
            color = if (selected) WorkbenchColors.Primary else WorkbenchColors.OnSurface,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
