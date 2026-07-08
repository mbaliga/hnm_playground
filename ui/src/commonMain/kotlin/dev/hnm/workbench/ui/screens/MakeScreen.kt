package dev.hnm.workbench.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.ui.components.AssistantPanel
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.HyleRoles

/**
 * Make — the create tab (UX brief §6.2). Phase 0 scope: the Assistant, already built, as the tab's
 * MVP — enough that "make something" has a real, working front door. The five source mini-flows
 * (Material/Texture/Motion/Rhythm/Blend) are Phase 3's job.
 */
@Composable
fun MakeScreen(
    state: EditorState,
    onOpenEditor: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(HyleRoles.Background),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Make", color = HyleRoles.OnSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        item { AssistantPanel(state) }
        if (state.assistantMessage != null) {
            item {
                Button(
                    onClick = onOpenEditor,
                    colors = ButtonDefaults.buttonColors(containerColor = HyleRoles.PrimaryAction),
                ) {
                    Text("Open in editor", fontSize = 13.sp)
                }
            }
        }
    }
}
