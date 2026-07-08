package dev.hnm.workbench.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
 * Device — the honesty surface (UX brief §6.4). Phase 0 scope: the capability facts this app already
 * knows (relocated from the old native gallery's diagnostics line) plus a capture-report action. The
 * full hero card, resonant-frequency/Q display, and simulator explainer are Phase 6's job.
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

private fun yn(b: Boolean) = if (b) "yes" else "no"
