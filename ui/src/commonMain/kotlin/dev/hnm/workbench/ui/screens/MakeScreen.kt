package dev.hnm.workbench.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.ui.components.AssistantPanel
import dev.hnm.workbench.ui.components.GlassSurface
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.nav.MakeSourceKind
import dev.hnm.workbench.ui.theme.HyleRoles

/**
 * Make — the create tab (UX brief §6.2). Top: the Assistant, one prompt box for describe-and-generate.
 * Below: a grid of the five generative source mini-flows (Material/Texture/Motion/Rhythm/Blend), each
 * opening a focused one-screen flow ([MakeSourceScreen]) that reuses the panel already built for it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MakeScreen(
    state: EditorState,
    onOpenEditor: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSource: (MakeSourceKind) -> Unit = {},
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
        item {
            Text(
                "Or start from a source",
                color = HyleRoles.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item {
            // Five tiles, two-up: a plain FlowRow (as the other palettes already use) rather than a
            // LazyVerticalGrid, which would need an explicit bounded height to nest inside this
            // LazyColumn's item — five items don't need their own laziness anyway.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (kind in MakeSourceKind.entries) {
                    SourceTile(
                        kind = kind,
                        modifier = Modifier.width(160.dp),
                        onClick = { onOpenSource(kind) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceTile(kind: MakeSourceKind, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassSurface(modifier = modifier.aspectRatio(1.6f).clickable { onClick() }) {
        Box(Modifier.fillMaxSize().padding(14.dp)) {
            Text(
                kind.title,
                color = HyleRoles.OnSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}
