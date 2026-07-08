package dev.hnm.workbench.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.ui.components.MaterialPalette
import dev.hnm.workbench.ui.components.MotionPalette
import dev.hnm.workbench.ui.components.NavigatorPanel
import dev.hnm.workbench.ui.components.RhythmCapturePanel
import dev.hnm.workbench.ui.components.TexturePalette
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.nav.MakeSourceKind
import dev.hnm.workbench.ui.theme.HyleRoles
import kotlinx.coroutines.launch

/**
 * A Make source mini-flow (UX brief §6.2): one of the five generative building blocks, each a focused
 * one-screen surface embedding the panel that already exists for it (Motion/Texture/Material/Rhythm/
 * Navigator — built earlier for the dense editor and unchanged here), ending in the same three actions:
 * Feel again / Save / Open in Editor. Every panel already loads its selection straight into
 * [EditorState.pattern] on tap, so this shell only needs to act on whatever's currently loaded — no
 * separate "candidate" concept.
 *
 * "Blend" reuses [NavigatorPanel] (the [dev.hnm.workbench.core.design.ParameterNavigator] family walk)
 * rather than the brief's literal "pick two arbitrary library patterns" — `core` only supports
 * interpolating two fields/springs of the *same* underlying type today, not arbitrary IR patterns;
 * extending that is future work, not a Phase 3 simplification worth blocking on.
 */
@Composable
fun MakeSourceScreen(
    kind: MakeSourceKind,
    state: EditorState,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val scope = rememberCoroutineScope()
    var savedFlash by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().background(HyleRoles.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(
                start = 8.dp, end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp, bottom = 8.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.clip(CircleShape).clickable { onBack() }.padding(12.dp),
            ) {
                Text("‹", color = HyleRoles.PrimaryAction, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Text(kind.title, color = HyleRoles.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                when (kind) {
                    MakeSourceKind.MATERIAL -> MaterialPalette(state)
                    MakeSourceKind.TEXTURE -> TexturePalette(state)
                    MakeSourceKind.MOTION -> MotionPalette(state)
                    MakeSourceKind.RHYTHM -> RhythmCapturePanel(state)
                    MakeSourceKind.BLEND -> NavigatorPanel(state)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = { scope.launch { state.playCurrentLatched() } },
                modifier = Modifier.weight(1f),
            ) { Text("Feel again", fontSize = 13.sp) }
            OutlinedButton(
                onClick = {
                    state.saveToLibrary()
                    savedFlash = true
                },
                modifier = Modifier.weight(1f),
            ) { Text(if (savedFlash) "Saved ✓" else "Save", fontSize = 13.sp) }
            Button(
                onClick = onOpenEditor,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = HyleRoles.PrimaryAction),
            ) { Text("Open in Editor", fontSize = 13.sp) }
        }
    }
}
