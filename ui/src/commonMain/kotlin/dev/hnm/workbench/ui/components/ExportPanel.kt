package dev.hnm.workbench.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.model.ExportKind
import dev.hnm.workbench.ui.theme.WorkbenchColors

/**
 * Live export preview (M5). The exporters are pure functions of the IR, so this always reflects the
 * current pattern + target. Includes the discrete haptic schedule so degradation is visible.
 */
@Composable
fun ExportPanel(state: EditorState, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (kind in ExportKind.entries) {
                FilterChip(
                    selected = state.exportKind == kind,
                    onClick = { state.exportKind = kind },
                    label = { Text(kind.name, fontSize = 11.sp) },
                )
            }
        }
        val scroll = rememberScrollState()
        Column(Modifier.fillMaxSize().padding(top = 8.dp).verticalScroll(scroll)) {
            Text(
                state.exportText(),
                color = WorkbenchColors.OnSurface,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "\n— scheduled commands (${state.capabilities.actuatorType}) —",
                color = WorkbenchColors.Muted,
                fontSize = 10.sp,
            )
            Text(
                state.scheduleSummary,
                color = WorkbenchColors.Muted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
