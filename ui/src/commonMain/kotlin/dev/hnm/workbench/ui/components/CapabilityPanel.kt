package dev.hnm.workbench.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.core.playback.HapticCapabilities
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors

private val PROFILES = listOf(
    "LRA (full)" to HapticCapabilities.LRA_FULL,
    "ERM (basic)" to HapticCapabilities.ERM_BASIC,
    "Wideband" to HapticCapabilities.WIDEBAND,
    "No actuator" to HapticCapabilities.NONE,
)

/**
 * Pick the target device profile to preview graceful degradation (M1/M4). Switching it changes how
 * `scheduleHaptics`/the Kotlin export behave — e.g. ERM collapses everything to on/off one-shots.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CapabilityPanel(state: EditorState, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Target device", color = WorkbenchColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for ((label, caps) in PROFILES) {
                FilterChip(
                    selected = state.capabilities == caps,
                    onClick = { state.capabilities = caps },
                    label = { Text(label, fontSize = 11.sp) },
                )
            }
        }
        val c = state.capabilities
        Text(
            "actuator=${c.actuatorType} · amplitude=${yn(c.hasAmplitudeControl)} · " +
                "primitives=${c.supportedPrimitives.size} · frequency=${yn(c.hasFrequencyControl)}  (ERM/LRA is a heuristic)",
            color = WorkbenchColors.Muted,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun yn(b: Boolean) = if (b) "yes" else "no"
