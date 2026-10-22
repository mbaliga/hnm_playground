package dev.hnm.workbench.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.core.playback.InterfaceFeelLevel
import dev.hnm.workbench.ui.components.GlassSurface
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.model.WorkspaceMode
import dev.hnm.workbench.ui.theme.HyleRoles

/**
 * Device — the honesty surface (UX brief §6.4): a hero card naming the simulated device (with its
 * resonant frequency/Q when known) or the abstract capability tier, the capability facts this app
 * already knows, and a capture-report action. [dev.hnm.workbench.ui.components.CapabilityPanel] (in
 * the Editor) is where the target device is actually picked; this screen reflects that same
 * selection via [EditorState.selectedDevice] rather than tracking its own.
 */
@Composable
fun DeviceScreen(
    state: EditorState,
    modifier: Modifier = Modifier,
    onCaptureDeviceReport: (() -> String)? = null,
    onReplayOnboarding: (() -> Unit)? = null,
    onReplaySplash: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var report by remember { mutableStateOf<String?>(null) }
    var confirmRecovery by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(HyleRoles.Background),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Device", color = HyleRoles.OnSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        item {
            Text("Playback: ${state.playbackDeviceName}", color = HyleRoles.OnSurface, fontSize = 18.sp)
            Text(if (state.playbackCapabilities != null) "Reported by this device; actuator type is an estimate. Reference profiles below do not change this endpoint." else "Open HAfx on a supported device to inspect its playback capabilities.", color = HyleRoles.Muted, fontSize = 13.sp)
        }
        item {
            val c = state.playbackCapabilities
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (c == null) "No playback capability report is available. Compatibility previews below use reference data."
                    else "actuator: ${c.actuatorType}\n" +
                        "amplitude control: ${yn(c.hasAmplitudeControl)}\n" +
                        "composition primitives: ${c.supportedPrimitives.size}\n" +
                        "frequency control: ${yn(c.hasFrequencyControl)} (Android envelope/PWLE needs API 36)\n" +
                        "interface feels: ${state.interfaceFeelLevel}",
                    color = HyleRoles.OnSurface,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        item {
            Button(
                onClick = { report = onCaptureDeviceReport?.invoke() },
                enabled = onCaptureDeviceReport != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = HyleRoles.PrimaryAction),
            ) {
                Text(
                    if (onCaptureDeviceReport != null) "Capture device capability report" else "Capture report (device only)",
                    fontSize = 13.sp,
                )
            }
        }
        report?.let { r ->
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        r,
                        color = HyleRoles.Muted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }

        item {
            Text("Files & backup", color=HyleRoles.OnSurface, fontSize=16.sp)
            Button(onClick={state.importFile?.invoke()}, enabled=state.importFile != null, modifier=Modifier.fillMaxWidth()) { Text("Import effect or library") }
            Button(onClick={state.backupLibrary()}, enabled=state.exportFile != null, modifier=Modifier.fillMaxWidth()) { Text("Back up library to file") }
            Button(onClick={state.attachSearchText?.invoke()}, enabled=state.attachSearchText != null, modifier=Modifier.fillMaxWidth()) { Text("Attach text for watch search") }
            Text("Use a UTF-8 text file up to 2,000 characters. Save and send the current effect, then open Search lab on the watch. Attached text is included in exports and transfers.", color=HyleRoles.Muted)
            Text("Native JSON keeps sound, vibration and references. Import adds copies without replacing your draft. Back up before uninstalling.", color=HyleRoles.Muted)
            if(state.recoveryBackupAvailable) Button(onClick={state.exportRecoveryBackup?.invoke()}, modifier=Modifier.fillMaxWidth()) { Text("Export original recovery file") }
            if(state.recoveryAvailable) Button(onClick={confirmRecovery=true}, modifier=Modifier.fillMaxWidth()) { Text("Recover unreadable library") }
        }
        item { Text("Compatibility preview · reference data", color = HyleRoles.OnSurface, fontSize = 16.sp) }
        item { DeviceHeroCard(state) }
        item {
            Button(onClick = { state.sendCurrent() }, enabled = state.sendToCompanion != null, modifier = Modifier.fillMaxWidth()) { Text("Save and send current effect to watch") }
            Text(state.companionConnection, color=HyleRoles.OnSurface, fontSize=13.sp)
            Button(onClick={state.refreshCompanion?.invoke()}, enabled=state.refreshCompanion != null) { Text("Check connection & delivery") }
            Text(state.companionStatus, color = HyleRoles.OnSurface, fontSize = 13.sp)
            Text("Wear OS transfers may route through Google services. Receiving an effect never starts playback.", color = HyleRoles.Muted, fontSize = 13.sp)
        }
        item {
            Text(
                "Preferences",
                color = HyleRoles.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item {
            Column {
                Text("Interface feel", color = HyleRoles.OnSurface, fontSize = 13.sp)
                Text(
                    "How much the app's own chrome (tab switches, snaps, boundaries) buzzes — never your content.",
                    color = HyleRoles.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InterfaceFeelLevel.entries.forEach { level ->
                        ChoiceChip(
                            label = level.name.lowercase().replaceFirstChar { it.uppercase() },
                            selected = state.interfaceFeelLevel == level,
                            onClick = { state.interfaceFeelLevel = level },
                        )
                    }
                }
            }
        }
        item {
            Column {
                Text("Workspace", color = HyleRoles.OnSurface, fontSize = 13.sp)
                Text(
                    "Technical reveals power-user tools like the Editor's Edit-as-JSON sheet.",
                    color = HyleRoles.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WorkspaceMode.entries.forEach { mode ->
                        ChoiceChip(
                            label = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                            selected = state.workspaceMode == mode,
                            onClick = { state.workspaceMode = mode },
                        )
                    }
                }
            }
        }
        if (onReplayOnboarding != null || onReplaySplash != null) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onReplayOnboarding != null) {
                        OutlinedButton(onClick = onReplayOnboarding, modifier = Modifier.weight(1f)) {
                            Text("Replay onboarding", fontSize = 12.sp)
                        }
                    }
                    if (onReplaySplash != null) {
                        OutlinedButton(onClick = onReplaySplash, modifier = Modifier.weight(1f)) {
                            Text("Replay splash", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
    if(confirmRecovery) androidx.compose.material3.AlertDialog(
        onDismissRequest={confirmRecovery=false}, title={Text("Start a fresh library?")},
        text={Text("The unreadable file will be archived on this device first. Your draft is kept. You can then import a previous backup.")},
        confirmButton={Button(onClick={state.recoverLibrary?.invoke();confirmRecovery=false}) {Text("Archive & recover")}},
        dismissButton={Button(onClick={confirmRecovery=false}) {Text("Cancel")}})
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) HyleRoles.PrimaryAction.copy(alpha = 0.22f) else HyleRoles.Surface
    val fg = if (selected) HyleRoles.PrimaryAction else HyleRoles.Muted
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/**
 * The name-and-headline-numbers card: which real device (if any) is simulated, its resonant
 * frequency/Q (the two numbers that most determine how "buzzy vs thuddy" haptics feel on that
 * hardware), or — with no device picked — the abstract capability tier and an honest nudge toward
 * the simulator in the Editor.
 */
@Composable
private fun DeviceHeroCard(state: EditorState) {
    val device = state.selectedDevice
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (device != null) {
                Text(device.marketName, color = HyleRoles.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${device.manufacturer} · ${device.os.ifBlank { device.platform.name }}",
                    color = HyleRoles.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    HeroStat("Resonant freq", device.resonantFrequencyHz?.let { "${it.toInt()} Hz" } ?: "n/a")
                    HeroStat("Q factor", device.qFactor?.let { oneDecimal(it) } ?: "n/a")
                }
                if (device.resonantFrequencyHz == null || device.qFactor == null) {
                    Text(
                        "Reference data — not a physical measurement of the playback device.",
                        color = HyleRoles.Muted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                Text(
                    "${state.capabilities.actuatorType} (abstract tier)",
                    color = HyleRoles.OnSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Choose a reference profile in the editor to inspect export compatibility.",
                    color = HyleRoles.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String) {
    Column {
        Text(value, color = HyleRoles.PlaybackGlow, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = HyleRoles.Muted, fontSize = 10.sp)
    }
}

// No String.format in commonMain (no java.util.Formatter on non-JVM targets); Q factors are always
// positive, so integer scaling is a safe one-decimal formatter here.
private fun oneDecimal(v: Double): String {
    val scaled = (v * 10).toInt()
    return "${scaled / 10}.${kotlin.math.abs(scaled % 10)}"
}

private fun yn(b: Boolean) = if (b) "yes" else "no"
