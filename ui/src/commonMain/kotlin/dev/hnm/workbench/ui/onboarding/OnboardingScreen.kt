package dev.hnm.workbench.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.ui.components.DotGridSubstrate
import dev.hnm.workbench.ui.components.GlassSurface
import dev.hnm.workbench.ui.model.WorkspaceMode
import dev.hnm.workbench.ui.theme.HyleRoles

private data class Beat(val title: String, val body: String)

private val BEATS = listOf(
    Beat(
        "Design a feeling, not a waveform",
        "A visual or material handle — a texture, a material strike, a motion primitive — controls " +
            "felt vibration far more reliably than dragging a number does.",
    ),
    Beat(
        "Describe it",
        "Type what you want in the Assistant — \"urgent alert\", \"metal tap\", \"gentle tick\" — and it " +
            "builds a haptic + sound pattern and explains what it made.",
    ),
    Beat(
        "Feel it",
        "Tap Play to feel your pattern on a real actuator, and hear it through the speaker at the same " +
            "moment — sound and touch come from one shared timeline, not hand-synced afterward.",
    ),
    Beat(
        "Refine it",
        "Ask for edits in plain words — \"softer\", \"sharper\", \"longer\", \"slower\" — or drag the " +
            "sliders in the Inspector for direct control.",
    ),
    Beat(
        "Pick your workspace",
        "Vibe keeps things simple — sliders and plain language. Technical adds raw tools like " +
            "Edit-as-JSON. You can change this any time.",
    ),
    Beat(
        "It's honest about your device",
        "Every actuator is different. The Device tab shows exactly what your hardware supports, and lets " +
            "you simulate other phones to see how a pattern degrades gracefully.",
    ),
)

private const val WORKSPACE_BEAT_INDEX = 4

/**
 * The six-beat onboarding walkthrough (UX brief §6, Phase 6): shown once after the splash, before the
 * app's first real use. Beat 5 is interactive — it's where [WorkspaceMode] (Vibe vs Technical) is
 * chosen, the same setting that gates the Editor's Edit-as-JSON tool. "Skip" and "Get started" both
 * call [onComplete] with whatever mode is currently selected (Vibe if the user skipped before choosing).
 */
@Composable
fun OnboardingScreen(onComplete: (WorkspaceMode) -> Unit, modifier: Modifier = Modifier) {
    var beatIndex by remember { mutableStateOf(0) }
    var chosenMode by remember { mutableStateOf(WorkspaceMode.VIBE) }
    val beat = BEATS[beatIndex]
    val isLast = beatIndex == BEATS.lastIndex

    Box(modifier.fillMaxSize().background(HyleRoles.Background)) {
        DotGridSubstrate(modifier = Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${beatIndex + 1} / ${BEATS.size}", color = HyleRoles.Muted, fontSize = 12.sp)
                Text(beat.title, color = HyleRoles.OnSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(beat.body, color = HyleRoles.Muted, fontSize = 15.sp)
                if (beatIndex == WORKSPACE_BEAT_INDEX) {
                    WorkspaceChoiceRow(selected = chosenMode, onSelect = { chosenMode = it })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onComplete(chosenMode) }) { Text("Skip") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (beatIndex > 0) {
                        OutlinedButton(onClick = { beatIndex-- }) { Text("Back") }
                    }
                    Button(
                        onClick = { if (isLast) onComplete(chosenMode) else beatIndex++ },
                        colors = ButtonDefaults.buttonColors(containerColor = HyleRoles.PrimaryAction),
                    ) {
                        Text(if (isLast) "Get started" else "Next")
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceChoiceRow(selected: WorkspaceMode, onSelect: (WorkspaceMode) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WorkspaceOptionCard(
            title = "Vibe",
            body = "Sliders and plain language.",
            mode = WorkspaceMode.VIBE,
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
        WorkspaceOptionCard(
            title = "Technical",
            body = "Vibe, plus raw JSON editing.",
            mode = WorkspaceMode.TECHNICAL,
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WorkspaceOptionCard(
    title: String,
    body: String,
    mode: WorkspaceMode,
    selected: WorkspaceMode,
    onSelect: (WorkspaceMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = mode == selected
    GlassSurface(
        modifier = modifier.clickable { onSelect(mode) },
        borderColor = if (isSelected) HyleRoles.SelectionOutline else HyleRoles.Grid,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = HyleRoles.OnSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = HyleRoles.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
