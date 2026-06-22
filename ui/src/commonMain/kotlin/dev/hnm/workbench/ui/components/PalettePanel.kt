package dev.hnm.workbench.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.core.ir.PrimitiveType
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors

/** The primitive palette: tap a primitive to append it to the haptic track (M4). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PalettePanel(state: EditorState, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Primitive palette", color = WorkbenchColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (type in PrimitiveType.entries) {
                val supported = type in state.capabilities.supportedPrimitives
                FilterChip(
                    selected = false,
                    onClick = { state.addPrimitive(type) },
                    label = {
                        Text(
                            type.name.lowercase().replace('_', ' '),
                            fontSize = 11.sp,
                            color = if (supported) WorkbenchColors.OnSurface else WorkbenchColors.Muted,
                        )
                    },
                )
            }
        }
        Text(
            "Greyed primitives aren't supported by the current target — they'll be synthesized from a transient on export.",
            color = WorkbenchColors.Muted,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
