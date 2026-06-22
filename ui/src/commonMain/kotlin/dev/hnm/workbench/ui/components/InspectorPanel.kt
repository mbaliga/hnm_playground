package dev.hnm.workbench.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.Primitive
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors

/**
 * Live knobs for the selected event (M4): intensity and sharpness sliders that edit the IR in place
 * while a loop plays. Editing rebuilds the immutable pattern, which re-renders the timeline waveform.
 */
@Composable
fun InspectorPanel(state: EditorState, modifier: Modifier = Modifier) {
    val event = state.selectedEvent
    Column(modifier) {
        Text("Inspector", color = WorkbenchColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        if (event == null) {
            Text("Tap an event in the timeline to edit it.", color = WorkbenchColors.Muted, fontSize = 12.sp)
            return@Column
        }

        val (kind, intensity, sharpness) = when (event) {
            is Transient -> Triple("Transient @ ${ms(event.time)}", event.intensity, event.sharpness)
            is Continuous -> Triple("Continuous @ ${ms(event.time)}", event.intensity, event.sharpness)
            is Primitive -> Triple("${event.type.name} @ ${ms(event.time)}", event.scale, null)
        }
        Text(kind, color = WorkbenchColors.OnSurface, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))

        LabeledSlider(if (event is Primitive) "Scale" else "Intensity", intensity) { state.setSelectedIntensity(it) }
        if (sharpness != null) {
            LabeledSlider("Sharpness", sharpness) { state.setSelectedSharpness(it) }
        } else {
            Text("Primitives have no sharpness knob.", color = WorkbenchColors.Muted, fontSize = 10.sp)
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { state.deleteSelected() }) { Text("Delete", fontSize = 12.sp) }
            OutlinedButton(onClick = { state.mutate() }) { Text("Mutate ⟳", fontSize = 12.sp) }
            OutlinedButton(onClick = { state.reset() }) { Text("Reset", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Double, onChange: (Double) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = WorkbenchColors.OnSurface, fontSize = 12.sp)
            Text("${(value * 100).toInt()}%", color = WorkbenchColors.Muted, fontSize = 12.sp)
        }
        Slider(
            value = value.toFloat().coerceIn(0f, 1f),
            onValueChange = { onChange(it.toDouble()) },
            valueRange = 0f..1f,
        )
    }
}

private fun ms(s: Double): String = "${(s * 1000).toInt()} ms"
