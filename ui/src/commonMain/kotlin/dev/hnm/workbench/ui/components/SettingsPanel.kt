package dev.hnm.workbench.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors

/**
 * The Settings sheet's content (`Sheet.SETTINGS`, wired in `nav/SheetHost.kt`). Every control here is
 * backed directly by [dev.hnm.workbench.core.settings.SettingsStore] via [EditorState] and applies the
 * instant it's touched — there's no "Save" button and nothing here is cosmetic-only state that quietly
 * does nothing, the way a placeholder would.
 *
 * **Cloud assistant policy** (owner-mandated — see [dev.hnm.workbench.core.settings.SettingsStore]'s
 * kdoc and [dev.hnm.workbench.core.design.OptInPatternGenerator]): the on-device generator
 * ([dev.hnm.workbench.core.design.OnDevicePatternGenerator]) always runs and never touches the network.
 * A cloud fallback exists only as a strictly opt-in upgrade — defaults OFF — and the toggle below is the
 * one and only place in the whole app that can turn it on. Nothing else in the Workbench makes a network
 * call, toggle on or off; this screen exists so that fact is never left to trust — it's stated plainly,
 * right next to the only switch that could change it.
 */
@Composable
fun SettingsPanel(state: EditorState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        SettingsGroup("AI Assistant") {
            SettingsToggleRow(
                title = "Cloud assistant",
                subtitle = if (state.cloudAssistantEnabled) {
                    "On. When the on-device assistant can't confidently match your prompt, it may send " +
                        "the prompt text and the pattern you're currently editing to a cloud service to " +
                        "get a better result. That hand-off — prompt text plus the current pattern, " +
                        "nothing else — is the only network traffic anywhere in this app."
                } else {
                    "Off by default, as it should be. The assistant runs entirely on-device: nothing you " +
                        "type, build, or play ever leaves this device unless you turn this on."
                },
                checked = state.cloudAssistantEnabled,
                onCheckedChange = { state.setCloudAssistantEnabled(it) },
            )
        }

        Spacer(Modifier.height(18.dp))

        SettingsGroup("Getting started") {
            SettingsActionRow(
                title = "Reset getting-started progress",
                subtitle = "Currently: ${state.onboarding.tier.name.lowercase().replace('_', ' ')}. " +
                    "Clears the Feel / Make / Change / Ship checklist and brings the coachmarks back.",
                actionLabel = "Reset",
                onClick = { state.resetOnboarding() },
            )
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title.uppercase(),
            color = WorkbenchColors.Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
        )
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(WorkbenchColors.Surface)
                .padding(horizontal = 14.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = WorkbenchColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = WorkbenchColors.InkDim, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = WorkbenchColors.Red,
                checkedTrackColor = WorkbenchColors.Red.copy(alpha = 0.45f),
                uncheckedThumbColor = WorkbenchColors.InkDim,
                uncheckedTrackColor = WorkbenchColors.SurfaceVariant,
            ),
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = WorkbenchColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = WorkbenchColors.InkDim, fontSize = 11.sp, lineHeight = 15.sp)
        }
        OutlinedButton(onClick = onClick) {
            Text(actionLabel, fontSize = 12.sp)
        }
    }
}
