package dev.hnm.workbench.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.ui.components.GlassSurface
import dev.hnm.workbench.ui.model.EditorState
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
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var report by remember { mutableStateOf<String?>(null) }

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
        item { DeviceHeroCard(state) }
        item {
            val c = state.capabilities
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "actuator: ${c.actuatorType}\n" +
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
                        "Seed data — real values come from an on-device probe (API 34).",
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
                    "Pick a real phone in the Editor's Target device panel to see its resonant frequency and Q.",
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
