package dev.hnm.workbench.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors

/**
 * Tap-to-capture rhythm panel. The big button records tap times (via system clock mock); taps are
 * displayed as vertical markers on a mini timeline. "Load" converts the captured taps to a pattern
 * via [RhythmCapture] and replaces the current pattern in [state].
 *
 * On desktop/JVM the clock source is [System.currentTimeMillis]; on Android the same works because
 * this is commonMain Compose — the state operations in [EditorState.startOrTap] accept a Long ms.
 */
@Composable
fun RhythmCapturePanel(state: EditorState, modifier: Modifier = Modifier) {
    // Epoch tick to force timeline redraw when taps list changes.
    var tapEpoch by remember { mutableStateOf(0) }

    Column(modifier) {
        Text("Rhythm capture", color = WorkbenchColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))

        // Tap timeline mini-view.
        val taps = state.capturedTaps
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(WorkbenchColors.Surface),
        ) {
            val w = size.width
            val h = size.height
            drawLine(WorkbenchColors.Grid, Offset(0f, h / 2f), Offset(w, h / 2f))
            if (taps.size >= 2) {
                val span = (taps.last().time - taps.first().time).coerceAtLeast(0.001)
                taps.forEach { tap ->
                    val x = ((tap.time - taps.first().time) / span * w).toFloat()
                    val barH = h * 0.7f * tap.pressure.toFloat()
                    drawRect(
                        WorkbenchColors.Haptic,
                        Offset(x - 1.5f, (h - barH) / 2f),
                        Size(3f, barH),
                    )
                }
            } else if (taps.size == 1) {
                drawRect(WorkbenchColors.Haptic, Offset(w / 2f - 1.5f, h * 0.15f), Size(3f, h * 0.7f))
            }
        }

        val tapLabel = when (taps.size) {
            0 -> "Tap the button below to start recording"
            1 -> "1 tap — keep going…"
            else -> "${taps.size} taps · ${durationMs(taps)} ms"
        }
        Text(tapLabel, color = WorkbenchColors.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    state.startOrTap(System.currentTimeMillis())
                    tapEpoch++
                },
                colors = ButtonDefaults.buttonColors(containerColor = WorkbenchColors.Primary),
                modifier = Modifier.weight(1f).height(44.dp),
            ) {
                Text(if (taps.isEmpty()) "● Start / Tap" else "● Tap", fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = {
                    state.loadCapturedRhythm()
                    tapEpoch++
                },
                enabled = taps.size >= 2,
                modifier = Modifier.height(44.dp),
            ) {
                Text("Load ↓", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = {
                    state.clearCapture()
                    tapEpoch++
                },
                enabled = taps.isNotEmpty(),
                modifier = Modifier.height(44.dp),
            ) {
                Text("Clear", fontSize = 12.sp)
            }
        }
    }
}

private fun durationMs(taps: List<dev.hnm.workbench.core.design.Tap>): Int =
    if (taps.size < 2) 0 else ((taps.last().time - taps.first().time) * 1000).toInt()
