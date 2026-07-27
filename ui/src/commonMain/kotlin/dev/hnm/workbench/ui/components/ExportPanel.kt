package dev.hnm.workbench.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.model.ExportKind
import dev.hnm.workbench.ui.theme.WorkbenchColors

/**
 * Live export preview (M5). The exporters are pure functions of the IR, so this always reflects the
 * current pattern + target. Includes the discrete haptic schedule so degradation is visible.
 *
 * [onShip] is the Clackpad hand-off's "ship" action (`docs/CLACKPAD_CONTRACT.md` §2 delivery a). When
 * [state]'s [EditorState.handoffRole] is set (this session was opened by Clackpad) *and* a host actually
 * wired [onShip] up, the general format-chip row is replaced by a single "Send to Clackpad" button —
 * absent either condition (no active hand-off, or a host — e.g. the desktop app — that hasn't wired
 * shipping in at all), the normal picker renders exactly as before.
 */
@Composable
fun ExportPanel(state: EditorState, modifier: Modifier = Modifier, onShip: ((role: String) -> Unit)? = null) {
    Column(modifier) {
        val handoffRole = state.handoffRole
        if (handoffRole != null && onShip != null) {
            Text(
                "Opened from Clackpad for the \"$handoffRole\" key feedback. Shape it, then ship it straight back.",
                color = WorkbenchColors.InkDim,
                fontSize = 11.sp,
            )
            Button(
                onClick = {
                    state.markShipped()
                    onShip(handoffRole)
                },
                colors = ButtonDefaults.buttonColors(containerColor = WorkbenchColors.Red),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("⇱ Send to Clackpad", fontWeight = FontWeight.SemiBold)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // CLACKPAD is deliberately never offered here — see ExportKind's kdoc.
                for (kind in ExportKind.entries.filter { it != ExportKind.CLACKPAD }) {
                    FilterChip(
                        selected = state.exportKind == kind,
                        onClick = { state.exportKind = kind },
                        label = { Text(kind.name, fontSize = 11.sp) },
                    )
                }
            }
        }
        // No internal scroll/fillMaxSize: this panel always sits inside a scrolling parent column, so a
        // nested scroll here would measure with infinite height. Lay out at natural height instead.
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
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
