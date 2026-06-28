package dev.hnm.workbench.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.core.device.DeviceDatabase
import dev.hnm.workbench.core.device.DeviceProfile
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
 * Pick the target device profile to preview graceful degradation (M1/M4). Two ways in: the four
 * abstract capability tiers, or a **real phone from the device database** — selecting a device loads
 * its actual capabilities so `scheduleHaptics`/the export behave exactly as that handset would (a budget
 * ERM collapses everything to on/off; a wideband LRA gets the amplitude+frequency envelope path). The
 * catalog grows as users contribute device reports from the Android player.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CapabilityPanel(state: EditorState, modifier: Modifier = Modifier) {
    val devices = remember { DeviceDatabase.seeded() }
    var selectedDeviceId by remember { mutableStateOf<String?>(null) }

    Column(modifier) {
        Text("Target device", color = WorkbenchColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for ((label, caps) in PROFILES) {
                FilterChip(
                    selected = state.capabilities == caps && selectedDeviceId == null,
                    onClick = { state.capabilities = caps; selectedDeviceId = null },
                    label = { Text(label, fontSize = 11.sp) },
                )
            }
        }

        Text(
            "or simulate a real device",
            color = WorkbenchColors.Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (device in devices.all) {
                DeviceChip(
                    device = device,
                    selected = selectedDeviceId == device.id,
                    onClick = {
                        state.capabilities = device.toCapabilities()
                        selectedDeviceId = device.id
                    },
                )
            }
        }

        val device = selectedDeviceId?.let { devices.get(it) }
        if (device != null) {
            Text(
                "${device.marketName} · ${device.summary()}",
                color = WorkbenchColors.Muted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
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
}

@Composable
private fun DeviceChip(device: DeviceProfile, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) WorkbenchColors.Red.copy(alpha = 0.22f) else WorkbenchColors.Surface
    val fg = if (selected) WorkbenchColors.Red else WorkbenchColors.OnSurface
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            device.marketName,
            color = fg,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

private fun yn(b: Boolean) = if (b) "yes" else "no"
