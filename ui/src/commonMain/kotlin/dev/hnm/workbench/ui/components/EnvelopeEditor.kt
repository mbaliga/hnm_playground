package dev.hnm.workbench.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.core.dsp.EnvelopeShaper
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.Envelope
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors

/**
 * Draws the ADSR envelope of the selected continuous event (M4). It samples the same [EnvelopeShaper]
 * the renderer uses, so what you see is exactly what gets rendered. Transients/primitives have no
 * sustained envelope, so it shows a hint instead.
 */
@Composable
fun EnvelopeEditor(state: EditorState, modifier: Modifier = Modifier) {
    val selected = state.selectedEvent
    Column(modifier) {
        Text("Envelope", color = WorkbenchColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(WorkbenchColors.Surface),
        ) {
            val w = size.width
            val h = size.height
            val pad = 8f

            if (selected !is Continuous) {
                // Baseline only; the inspector explains why.
                drawLine(WorkbenchColors.Grid, Offset(pad, h - pad), Offset(w - pad, h - pad), strokeWidth = 1f)
                return@Canvas
            }

            val env = selected.envelope
            val shaper = EnvelopeShaper(env, selected.duration)
            val total = shaper.totalDuration.coerceAtLeast(0.001)
            val path = Path()
            val steps = 120
            for (i in 0..steps) {
                val t = total * i / steps
                val g = shaper.gainAt(t).coerceIn(0.0, 1.0)
                val x = pad + (t / total).toFloat() * (w - 2 * pad)
                val y = (h - pad) - g.toFloat() * (h - 2 * pad)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, WorkbenchColors.Haptic, style = Stroke(width = 2.5f))

            // Mark the stage boundaries (attack, decay end, release start).
            markStage(env, total, w, h, pad, env.attack)
            markStage(env, total, w, h, pad, env.attack + env.decay)
            markStage(env, total, w, h, pad, selected.duration)
        }
        val label = when (selected) {
            is Continuous -> "A ${ms(selected.envelope.attack)} · D ${ms(selected.envelope.decay)} · " +
                "S ${pct(selected.envelope.sustain)} · R ${ms(selected.envelope.release)}"
            null -> "Select an event"
            else -> "Transients/primitives are impulses (no ADSR)"
        }
        Text(label, color = WorkbenchColors.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.markStage(
    env: Envelope,
    total: Double,
    w: Float,
    h: Float,
    pad: Float,
    atSeconds: Double,
) {
    if (total <= 0) return
    val x = pad + (atSeconds / total).toFloat() * (w - 2 * pad)
    drawLine(WorkbenchColors.Grid.copy(alpha = 0.6f), Offset(x, pad), Offset(x, h - pad), strokeWidth = 1f)
}

private fun ms(s: Double): String = "${(s * 1000).toInt()}ms"
private fun pct(v: Double): String = "${(v * 100).toInt()}%"
