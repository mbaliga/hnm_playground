package dev.hnm.workbench.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hnm.workbench.core.dsp.DefaultPatternRenderer
import dev.hnm.workbench.core.ir.AudioTrack
import dev.hnm.workbench.core.ir.Continuous
import dev.hnm.workbench.core.ir.OscEvent
import dev.hnm.workbench.core.ir.Primitive
import dev.hnm.workbench.core.ir.SampleEvent
import dev.hnm.workbench.core.ir.Transient
import dev.hnm.workbench.core.playback.readAll
import dev.hnm.workbench.ui.model.EditorState
import dev.hnm.workbench.ui.theme.WorkbenchColors
import kotlin.math.abs

/**
 * The timeline: a haptic lane (event markers over a live waveform backdrop) above an audio lane.
 * Tap an event to select it; the inspector then edits it. Pure drawing from the IR, so it always
 * reflects the current pattern.
 */
@Composable
fun TimelineView(state: EditorState, modifier: Modifier = Modifier) {
    val pattern = state.pattern
    val duration = state.durationSeconds
    // Render a low-rate haptic waveform once per pattern for the backdrop.
    val waveform = remember(pattern) {
        DefaultPatternRenderer().renderHapticWaveform(pattern, WAVE_SR).readAll()
    }

    Column(modifier) {
        Text("Timeline", color = WorkbenchColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(WorkbenchColors.Surface)
                .pointerInput(pattern, duration) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        if (w <= 0f) return@detectTapGestures
                        val tappedTime = offset.x / w * duration
                        val nearest = state.hapticEvents
                            .withIndex()
                            .minByOrNull { abs(it.value.time - tappedTime) }
                        // Only select if the tap is in the upper (haptic) half and reasonably close.
                        if (nearest != null && offset.y < size.height / 2f &&
                            abs(nearest.value.time - tappedTime) < duration * 0.08
                        ) {
                            state.select(nearest.index)
                        }
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val hapticLane = Size(w, h / 2f)
            val audioTop = h / 2f

            drawGrid(duration, w, h)

            // Haptic waveform backdrop.
            drawWaveform(waveform, hapticLane, WorkbenchColors.HapticDim)

            // Haptic events.
            state.hapticEvents.forEachIndexed { i, event ->
                val x = (event.time / duration * w).toFloat()
                val selected = i == state.selectedEventIndex
                val color = if (selected) Color.White else WorkbenchColors.Haptic
                when (event) {
                    is Transient -> {
                        val r = (4 + event.intensity * 8).toFloat()
                        drawCircle(color, radius = r, center = Offset(x, hapticLane.height * 0.5f))
                    }
                    is Primitive -> {
                        val barH = (hapticLane.height * 0.6f * event.scale).toFloat()
                        drawRect(color, Offset(x - 3f, hapticLane.height * 0.5f - barH / 2f), Size(6f, barH))
                    }
                    is Continuous -> {
                        val wEv = (event.duration / duration * w).toFloat()
                        val barH = (hapticLane.height * 0.6f * event.intensity).toFloat()
                        drawRect(color.copy(alpha = 0.6f), Offset(x, hapticLane.height * 0.5f - barH / 2f), Size(wEv, barH))
                    }
                }
            }

            // Audio events as filled blocks in the lower lane.
            pattern.tracks.filterIsInstance<AudioTrack>().forEach { track ->
                track.events.forEach { ev ->
                    val x = (ev.time / duration * w).toFloat()
                    val dur = when (ev) {
                        is OscEvent -> ev.duration
                        is SampleEvent -> 0.05
                    }
                    val wEv = (dur / duration * w).toFloat().coerceAtLeast(3f)
                    val gain = if (ev is OscEvent) ev.gain else 1.0
                    val barH = (hapticLane.height * 0.7f * gain).toFloat()
                    drawRect(
                        WorkbenchColors.Primary.copy(alpha = 0.7f),
                        Offset(x, audioTop + (hapticLane.height - barH) / 2f),
                        Size(wEv, barH),
                    )
                }
            }
        }
        Text(
            "haptics ▲   audio ▼     ${formatSeconds(duration)} total",
            color = WorkbenchColors.Muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

private const val WAVE_SR = 4000

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid(duration: Double, w: Float, h: Float) {
    drawLine(WorkbenchColors.Grid, Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 1f)
    val step = niceStep(duration)
    var t = 0.0
    while (t <= duration) {
        val x = (t / duration * w).toFloat()
        drawLine(WorkbenchColors.Grid.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
        t += step
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaveform(
    samples: FloatArray,
    lane: Size,
    color: Color,
) {
    if (samples.isEmpty()) return
    val mid = lane.height * 0.5f
    val cols = lane.width.toInt().coerceAtLeast(1)
    val per = (samples.size / cols).coerceAtLeast(1)
    for (c in 0 until cols) {
        var peak = 0f
        val start = c * per
        for (i in start until minOf(start + per, samples.size)) {
            val a = abs(samples[i])
            if (a > peak) peak = a
        }
        val x = c.toFloat()
        val amp = peak * lane.height * 0.45f
        drawLine(color, Offset(x, mid - amp), Offset(x, mid + amp), strokeWidth = 1f)
    }
}

private fun niceStep(duration: Double): Double = when {
    duration <= 0.25 -> 0.05
    duration <= 1.0 -> 0.1
    duration <= 4.0 -> 0.5
    else -> 1.0
}

private fun formatSeconds(s: Double): String {
    val ms = (s * 1000).toInt()
    return "$ms ms"
}
