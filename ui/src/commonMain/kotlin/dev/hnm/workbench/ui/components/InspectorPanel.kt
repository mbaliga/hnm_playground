package dev.hnm.workbench.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.OscEvent
import dev.hnm.workbench.core.ir.Primitive
import dev.hnm.workbench.core.ir.SampleEvent
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.ir.Waveform
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors

/**
 * Live knobs for the selected haptic and audio events. Editing is immediate — the IR is replaced
 * immutably on every slider move, which re-renders the timeline waveform in real time.
 */
@Composable
fun InspectorPanel(state: EditorState, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Inspector", color = WorkbenchColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))

        // Haptic event section.
        HapticEventSection(state)

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = WorkbenchColors.Grid)
        Spacer(Modifier.height(8.dp))

        // Audio event section.
        AudioEventSection(state)

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = WorkbenchColors.Grid)
        Spacer(Modifier.height(6.dp))

        // Add-event row.
        Text("Add event", color = WorkbenchColors.Muted, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { state.addTransient() }, modifier = Modifier.weight(1f)) {
                Text("+ Tap", fontSize = 11.sp)
            }
            OutlinedButton(onClick = { state.addContinuous() }, modifier = Modifier.weight(1f)) {
                Text("+ Buzz", fontSize = 11.sp)
            }
            OutlinedButton(onClick = { state.addOscEvent() }, modifier = Modifier.weight(1f)) {
                Text("+ Tone", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun HapticEventSection(state: EditorState) {
    val event = state.selectedEvent
    if (event == null) {
        Text("Tap a haptic event in the timeline to edit it.", color = WorkbenchColors.Muted, fontSize = 12.sp)
        return
    }

    val (kind, intensity, sharpness) = when (event) {
        is Transient -> Triple("Transient @ ${ms(event.time)}", event.intensity, event.sharpness)
        is Continuous -> Triple("Continuous @ ${ms(event.time)}", event.intensity, event.sharpness)
        is Primitive -> Triple("${event.type.name} @ ${ms(event.time)}", event.scale, null)
    }
    Text(kind, color = WorkbenchColors.OnSurface, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))

    // Time slider: 0..max(2s, event time + 0.5s).
    val maxTime = (state.durationSeconds + 0.5).coerceAtLeast(2.0)
    LabeledSlider("Time", event.time / maxTime, "${ms(event.time)} ms", onChangeFinished = state::commitEdit) {
        state.setSelectedTime(it * maxTime)
    }

    LabeledSlider(
        if (event is Primitive) "Scale" else "Intensity", intensity, "${(intensity * 100).toInt()}%",
        onChangeFinished = state::commitEdit,
    ) {
        state.setSelectedIntensity(it)
    }
    if (sharpness != null) {
        LabeledSlider("Sharpness", sharpness, "${(sharpness * 100).toInt()}%", onChangeFinished = state::commitEdit) {
            state.setSelectedSharpness(it)
        }
    } else {
        Text("Primitives have no sharpness knob.", color = WorkbenchColors.Muted, fontSize = 10.sp)
    }

    // Continuous-only: duration + ADSR.
    if (event is Continuous) {
        val maxDur = (state.durationSeconds + 0.2).coerceAtLeast(0.5)
        LabeledSlider("Duration", event.duration / maxDur, "${ms(event.duration)} ms", onChangeFinished = state::commitEdit) {
            state.setSelectedDuration(it * maxDur)
        }
        Text("ADSR", color = WorkbenchColors.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        val env = event.envelope
        LabeledSlider("Attack", env.attack / 0.5, "${ms(env.attack)} ms", onChangeFinished = state::commitEdit) {
            state.setSelectedEnvelope(attack = it * 0.5)
        }
        LabeledSlider("Decay", env.decay / 0.5, "${ms(env.decay)} ms", onChangeFinished = state::commitEdit) {
            state.setSelectedEnvelope(decay = it * 0.5)
        }
        LabeledSlider("Sustain", env.sustain, "${(env.sustain * 100).toInt()}%", onChangeFinished = state::commitEdit) {
            state.setSelectedEnvelope(sustain = it)
        }
        LabeledSlider("Release", env.release / 0.5, "${ms(env.release)} ms", onChangeFinished = state::commitEdit) {
            state.setSelectedEnvelope(release = it * 0.5)
        }
    }

    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { state.deleteSelected() }) { Text("Delete", fontSize = 12.sp) }
        OutlinedButton(onClick = { state.mutate() }) { Text("Mutate ⟳", fontSize = 12.sp) }
        OutlinedButton(onClick = { state.reset() }) { Text("Reset", fontSize = 12.sp) }
    }
}

@Composable
private fun AudioEventSection(state: EditorState) {
    val ev = state.selectedAudioEvent
    if (ev == null) {
        Text("No audio event selected. Tap '+Tone' to add one.", color = WorkbenchColors.Muted, fontSize = 12.sp)
        return
    }
    when (ev) {
        is OscEvent -> OscEventEditor(state, ev)
        is SampleEvent -> Text("Sample '${ev.sampleId}' @ ${ms(ev.time)}", color = WorkbenchColors.OnSurface, fontSize = 12.sp)
    }
}

@Composable
private fun OscEventEditor(state: EditorState, ev: OscEvent) {
    val waveLabel = ev.waveform.name.lowercase().replaceFirstChar { it.uppercase() }
    Text("$waveLabel tone @ ${ms(ev.time)}", color = WorkbenchColors.OnSurface, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))

    val maxFreq = 2000.0
    LabeledSlider("Frequency", ev.frequencyHz / maxFreq, "${ev.frequencyHz.toInt()} Hz", onChangeFinished = state::commitEdit) {
        state.setSelectedAudioFrequency(it * maxFreq)
    }
    LabeledSlider("Gain", ev.gain, "${(ev.gain * 100).toInt()}%", onChangeFinished = state::commitEdit) {
        state.setSelectedAudioGain(it)
    }
    val maxDur = 2.0
    LabeledSlider("Duration", ev.duration / maxDur, "${ms(ev.duration)} ms", onChangeFinished = state::commitEdit) {
        state.setSelectedAudioDuration(it * maxDur)
    }

    // Waveform picker row.
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Waveform.entries.forEach { wf ->
            val selected = ev.waveform == wf
            OutlinedButton(
                onClick = {
                    // Replace via a roundabout: setSelectedAudioFrequency triggers updateSelectedAudio which has access
                    // to OscEvent. We need a direct waveform setter — add a helper.
                    state.setSelectedAudioWaveform(wf)
                },
                modifier = Modifier.weight(1f).height(30.dp),
            ) {
                Text(wf.name.take(3), fontSize = 9.sp, color = if (selected) WorkbenchColors.Primary else WorkbenchColors.Muted)
            }
        }
    }

    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { state.deleteSelectedAudio() }) { Text("Delete tone", fontSize = 11.sp) }
    }
}

@Composable
internal fun LabeledSlider(
    label: String,
    value: Double,
    valueLabel: String,
    onChangeFinished: (() -> Unit)? = null,
    onChange: (Double) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = WorkbenchColors.OnSurface, fontSize = 12.sp)
            Text(valueLabel, color = WorkbenchColors.Muted, fontSize = 12.sp)
        }
        Slider(
            value = value.toFloat().coerceIn(0f, 1f),
            onValueChange = { onChange(it.toDouble()) },
            onValueChangeFinished = onChangeFinished,
            valueRange = 0f..1f,
        )
    }
}

private fun ms(s: Double): String = "${(s * 1000).toInt()}"
