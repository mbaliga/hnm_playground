package dev.hnm.workbench.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.core.ir.HapticAudioPattern
import dev.hnm.workbench.core.library.BuiltInPatterns
import dev.hnm.workbench.ui.components.GlassSurface
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.HyleRoles
import kotlinx.coroutines.launch

/**
 * Feel — the home tab (UX brief §6.1). The library reborn: tap a card to *feel* it (primary affordance,
 * per the brief's "feel before read" thesis), tap the chevron to open it in the Editor. Only the
 * built-in reference vocabulary for now — "Yours"/saved patterns and the remote registry land in Phase
 * 2 (`PatternLibrary`/`RegistryIndex` already exist in `core`, this just doesn't surface them yet).
 */
@Composable
fun FeelScreen(
    state: EditorState,
    onOpenEditor: () -> Unit,
    modifier: Modifier = Modifier,
    onSelfTest: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val scope = rememberCoroutineScope()

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
        item {
            Text(
                "Tap a card to feel it. Tap the arrow to open it in the editor.",
                color = HyleRoles.Muted,
                fontSize = 12.sp,
            )
        }
        items(BuiltInPatterns.ALL) { pattern ->
            PatternCard(
                pattern = pattern,
                onFeel = {
                    state.load(pattern)
                    scope.launch { state.playCurrentLatched() }
                },
                onOpen = {
                    state.load(pattern)
                    onOpenEditor()
                },
            )
        }
        item {
            SelfTestRow(onSelfTest = onSelfTest)
        }
    }
}

@Composable
private fun PatternCard(pattern: HapticAudioPattern, onFeel: () -> Unit, onOpen: () -> Unit) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onFeel() }
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
